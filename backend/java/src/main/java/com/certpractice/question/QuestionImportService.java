package com.certpractice.question;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.certpractice.catalog.Chapter;
import com.certpractice.catalog.ChapterRepository;
import com.certpractice.catalog.Subject;
import com.certpractice.catalog.SubjectRepository;
import com.certpractice.catalog.Tag;
import com.certpractice.catalog.TagRepository;
import com.certpractice.identity.AdminAuditLog;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.text.TextNormalizer;
import com.certpractice.shared.web.Guard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * CSV/XLSX preview and confirmation. Validation errors are reported before any
 * question is written; only a fully valid preview can be confirmed.
 */
@Service
public class QuestionImportService {

    private static final int MAX_ROWS = 5000;
    private static final int PREVIEW_HOURS = 1;

    private final ImportBatchRepository batches;
    private final ImportRowErrorRepository rowErrors;
    private final SubjectRepository subjects;
    private final ChapterRepository chapters;
    private final TagRepository tags;
    private final QuestionVersionRepository versions;
    private final QuestionAuthoringService authoringService;
    private final QuestionAuditService audit;
    private final ObjectMapper objectMapper;

    public QuestionImportService(
            final ImportBatchRepository batches,
            final ImportRowErrorRepository rowErrors,
            final SubjectRepository subjects,
            final ChapterRepository chapters,
            final TagRepository tags,
            final QuestionVersionRepository versions,
            final QuestionAuthoringService authoringService,
            final QuestionAuditService audit,
            final ObjectMapper objectMapper) {
        this.batches = batches;
        this.rowErrors = rowErrors;
        this.subjects = subjects;
        this.chapters = chapters;
        this.tags = tags;
        this.versions = versions;
        this.authoringService = authoringService;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QuestionDtos.ImportBatchView preview(final UUID adminId, final MultipartFile file) {
        Guard.require(file != null && !file.isEmpty(), ErrorCode.VALIDATION_FAILED,
                "Import file is required");
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("upload");
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        Guard.require(lowerName.endsWith(".csv") || lowerName.endsWith(".xlsx"),
                ErrorCode.VALIDATION_FAILED, "Only CSV or XLSX files are supported");

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Unable to read the uploaded file", exception);
        }

        List<List<String>> rawRows = lowerName.endsWith(".csv")
                ? parseCsv(new String(bytes, StandardCharsets.UTF_8))
                : parseXlsx(bytes);
        Guard.require(!rawRows.isEmpty(), ErrorCode.VALIDATION_FAILED, "Import file is empty");

        Map<String, Integer> headers = headerIndex(rawRows.get(0));
        for (String required : List.of("type", "subjectcode", "chaptercode", "stem")) {
            Guard.require(headers.containsKey(required), ErrorCode.VALIDATION_FAILED,
                    "Missing required column: " + required);
        }

        List<QuestionDtos.ImportErrorView> errors = new ArrayList<>();
        List<ImportRowPayload> payloads = new ArrayList<>();
        Set<String> seenStems = new HashSet<>();
        int totalRows = 0;

        for (int index = 1; index < rawRows.size(); index++) {
            List<String> row = rawRows.get(index);
            if (isBlank(row)) {
                continue;
            }
            totalRows++;
            if (totalRows > MAX_ROWS) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Import files are limited to " + MAX_ROWS + " rows");
            }
            int rowNumber = index + 1;
            Map<String, String> values = rowValues(headers, row);
            List<QuestionDtos.ImportErrorView> rowProblems = new ArrayList<>();
            ImportRowPayload payload = validateRow(rowNumber, values, seenStems, rowProblems);
            errors.addAll(rowProblems);
            if (rowProblems.isEmpty() && payload != null) {
                payloads.add(payload);
                seenStems.add(TextNormalizer.normalizeFillAnswer(payload.stem()));
            }
        }

        Guard.require(totalRows > 0, ErrorCode.VALIDATION_FAILED, "Import file has no data rows");

        ImportBatch batch = new ImportBatch();
        batch.setAdminUserId(adminId);
        batch.setFileName(fileName);
        batch.setFileHash(sha256(bytes));
        batch.setStatus(ImportBatch.Status.PREVIEW_READY);
        batch.setTotalRows(totalRows);
        batch.setValidRows(payloads.size());
        batch.setInvalidRows(totalRows - payloads.size());
        batch.setPreviewExpiresAt(Instant.now().plusSeconds(PREVIEW_HOURS * 3600L));
        batch.setPayloadJson(toJson(payloads));
        batches.save(batch);

        saveErrors(batch.getId(), errors);
        audit.record(adminId, "QUESTION_IMPORT_PREVIEW", "IMPORT_BATCH", batch.getId(),
                AdminAuditLog.Outcome.SUCCESS,
                Map.of("totalRows", totalRows, "validRows", payloads.size()));
        return toView(batch);
    }

    @Transactional(readOnly = true)
    public QuestionDtos.ImportBatchView get(final UUID adminId, final UUID batchId) {
        ImportBatch batch = batches.findByIdAndAdminUserId(batchId, adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Import batch not found"));
        return toView(batch);
    }

    @Transactional
    public QuestionDtos.ImportBatchView confirm(final UUID adminId, final UUID batchId) {
        ImportBatch batch = batches.findForUpdate(batchId, adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Import batch not found"));
        if (batch.getStatus() == ImportBatch.Status.CONFIRMED) {
            return toView(batch);
        }
        Guard.require(batch.getStatus() == ImportBatch.Status.PREVIEW_READY, ErrorCode.STATE_CONFLICT,
                "Import batch is not ready for confirmation");
        Guard.require(batch.getInvalidRows() == 0, ErrorCode.STATE_CONFLICT,
                "Import batch contains validation errors");
        Guard.require(batch.getPreviewExpiresAt() != null
                        && batch.getPreviewExpiresAt().isAfter(Instant.now()),
                ErrorCode.STATE_CONFLICT, "Import preview has expired");

        List<ImportRowPayload> payloads = fromJson(batch.getPayloadJson());
        for (ImportRowPayload payload : payloads) {
            authoringService.createQuestion(adminId, new QuestionDtos.CreateQuestionRequest(
                    payload.subjectId(),
                    payload.chapterId(),
                    payload.type(),
                    payload.stem(),
                    payload.explanation(),
                    payload.fillReferenceAnswer(),
                    payload.difficulty(),
                    payload.sourceText(),
                    payload.options(),
                    payload.aliases(),
                    payload.tagIds()));
        }

        batch.setStatus(ImportBatch.Status.CONFIRMED);
        batch.setConfirmedAt(Instant.now());
        batches.save(batch);

        audit.record(adminId, "QUESTION_IMPORT_CONFIRM", "IMPORT_BATCH", batchId,
                AdminAuditLog.Outcome.SUCCESS, Map.of("created", payloads.size()));
        return toView(batch);
    }

    @Transactional
    public void cancel(final UUID adminId, final UUID batchId) {
        ImportBatch batch = batches.findByIdAndAdminUserId(batchId, adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Import batch not found"));
        Guard.require(batch.getStatus() != ImportBatch.Status.CONFIRMED, ErrorCode.STATE_CONFLICT,
                "Confirmed imports cannot be cancelled");
        rowErrors.deleteByBatchId(batchId);
        batches.delete(batch);
        audit.record(adminId, "QUESTION_IMPORT_CANCEL", "IMPORT_BATCH", batchId,
                AdminAuditLog.Outcome.SUCCESS, Map.of());
    }

    private ImportRowPayload validateRow(
            final int rowNumber,
            final Map<String, String> values,
            final Set<String> seenStems,
            final List<QuestionDtos.ImportErrorView> problems) {
        String type = upper(values.get("type"));
        if (!"SINGLE_CHOICE".equals(type) && !"SINGLE_BLANK".equals(type)) {
            problems.add(error(rowNumber, "type", "INVALID_TYPE", "type must be SINGLE_CHOICE or SINGLE_BLANK"));
        }

        String subjectCode = trim(values.get("subjectcode"));
        Subject subject = subjects.findFirstByCode(subjectCode).orElse(null);
        if (subject == null) {
            problems.add(error(rowNumber, "subjectCode", "UNKNOWN_SUBJECT", "Subject code does not exist"));
        }

        String chapterCode = trim(values.get("chaptercode"));
        Chapter chapter = subject == null ? null
                : chapters.findFirstBySubjectIdAndCode(subject.getId(), chapterCode).orElse(null);
        if (chapter == null) {
            problems.add(error(rowNumber, "chapterCode", "UNKNOWN_CHAPTER",
                    "Chapter code does not exist under the subject"));
        }

        String stem = trim(values.get("stem"));
        if (stem.isBlank()) {
            problems.add(error(rowNumber, "stem", "REQUIRED", "Stem is required"));
        } else {
            String normalizedStem = TextNormalizer.normalizeFillAnswer(stem);
            if (seenStems.contains(normalizedStem)) {
                problems.add(error(rowNumber, "stem", "DUPLICATE_QUESTION",
                        "Duplicate question inside the import file"));
            } else if (versions.countByStemIgnoreCase(stem) > 0) {
                problems.add(error(rowNumber, "stem", "DUPLICATE_QUESTION",
                        "A question with the same stem already exists"));
            }
        }

        String difficulty = upper(values.get("difficulty"));
        if (!List.of("EASY", "MEDIUM", "HARD").contains(difficulty)) {
            problems.add(error(rowNumber, "difficulty", "INVALID_DIFFICULTY",
                    "difficulty must be EASY, MEDIUM or HARD"));
        }

        List<QuestionDtos.OptionInput> options = List.of();
        String fillReferenceAnswer = trim(values.get("fillanswer"));
        List<String> aliasValues = List.of();
        if ("SINGLE_CHOICE".equals(type)) {
            String correctOption = upper(values.get("correctoption"));
            options = parseOptions(values.get("options"), correctOption, rowNumber, problems);
            if (fillReferenceAnswer != null && !fillReferenceAnswer.isBlank()) {
                problems.add(error(rowNumber, "fillAnswer", "UNEXPECTED_FIELD",
                        "Single-choice questions cannot define a fill answer"));
            }
        } else if ("SINGLE_BLANK".equals(type)) {
            if (fillReferenceAnswer == null || fillReferenceAnswer.isBlank()) {
                problems.add(error(rowNumber, "fillAnswer", "REQUIRED", "Fill answer is required"));
            }
            aliasValues = splitList(values.get("aliases"));
        }

        List<UUID> tagIds = new ArrayList<>();
        for (String tagName : splitList(values.get("tags"))) {
            Tag tag = tags.findByNormalizedName(TextNormalizer.normalizeUsername(tagName)).orElse(null);
            if (tag == null) {
                problems.add(error(rowNumber, "tags", "UNKNOWN_TAG", "Unknown tag: " + tagName));
            } else {
                tagIds.add(tag.getId());
            }
        }

        if (!problems.isEmpty()) {
            return null;
        }
        return new ImportRowPayload(
                rowNumber,
                type,
                subject.getId(),
                chapter.getId(),
                stem,
                options,
                fillReferenceAnswer,
                aliasValues,
                trim(values.get("explanation")),
                difficulty,
                trim(values.get("source")),
                tagIds);
    }

    private List<QuestionDtos.OptionInput> parseOptions(
            final String raw,
            final String correctOption,
            final int rowNumber,
            final List<QuestionDtos.ImportErrorView> problems) {
        List<QuestionDtos.OptionInput> options = new ArrayList<>();
        String value = trim(raw);
        if (value.isBlank()) {
            problems.add(error(rowNumber, "options", "REQUIRED", "Options are required"));
            return options;
        }
        Set<String> keys = new HashSet<>();
        for (String part : value.split("\\|")) {
            int separator = part.indexOf(':');
            if (separator <= 0 || separator == part.length() - 1) {
                problems.add(error(rowNumber, "options", "INVALID_OPTION",
                        "Options must use the A:content|B:content format"));
                continue;
            }
            String key = part.substring(0, separator).trim().toUpperCase(Locale.ROOT);
            String content = part.substring(separator + 1).trim();
            if (!keys.add(key)) {
                problems.add(error(rowNumber, "options", "DUPLICATE_OPTION_KEY",
                        "Duplicate option key: " + key));
            }
            options.add(new QuestionDtos.OptionInput(key, content, key.equals(correctOption)));
        }
        if (options.size() < 2) {
            problems.add(error(rowNumber, "options", "INSUFFICIENT_OPTIONS",
                    "At least two options are required"));
        }
        long correctCount = options.stream().filter(QuestionDtos.OptionInput::correct).count();
        if (correctOption == null || correctOption.isBlank()) {
            problems.add(error(rowNumber, "correctOption", "REQUIRED", "Correct option is required"));
        } else if (correctCount != 1) {
            problems.add(error(rowNumber, "correctOption", "INVALID_CORRECT_OPTION",
                    "correctOption must match exactly one option key"));
        }
        return options;
    }

    private void saveErrors(final UUID batchId, final List<QuestionDtos.ImportErrorView> errors) {
        for (QuestionDtos.ImportErrorView view : errors) {
            ImportRowError error = new ImportRowError();
            error.setBatchId(batchId);
            error.setRowNumber(view.rowNumber());
            error.setFieldName(view.field());
            error.setErrorCode(view.code());
            error.setMessage(view.message());
            rowErrors.save(error);
        }
    }

    private QuestionDtos.ImportBatchView toView(final ImportBatch batch) {
        List<QuestionDtos.ImportErrorView> errors = rowErrors
                .findByBatchIdOrderByRowNumberAsc(batch.getId())
                .stream()
                .map(error -> new QuestionDtos.ImportErrorView(
                        error.getRowNumber(),
                        error.getFieldName(),
                        error.getErrorCode(),
                        error.getMessage()))
                .toList();
        return new QuestionDtos.ImportBatchView(
                batch.getId(),
                batch.getStatus().name(),
                batch.getFileName(),
                batch.getTotalRows(),
                batch.getValidRows(),
                batch.getInvalidRows(),
                batch.getPreviewExpiresAt(),
                batch.getConfirmedAt(),
                errors);
    }

    private Map<String, Integer> headerIndex(final List<String> headerRow) {
        Map<String, Integer> headers = new HashMap<>();
        for (int index = 0; index < headerRow.size(); index++) {
            String key = normalizeHeader(headerRow.get(index));
            if (!key.isBlank()) {
                headers.putIfAbsent(key, index);
            }
        }
        return headers;
    }

    private Map<String, String> rowValues(final Map<String, Integer> headers, final List<String> row) {
        Map<String, String> values = new HashMap<>();
        headers.forEach((key, index) -> values.put(key, index < row.size() ? row.get(index) : ""));
        return values;
    }

    private List<List<String>> parseCsv(final String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < content.length() && content.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (character == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (character != '\r') {
                field.append(character);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            List<String> first = rows.get(0);
            first.set(0, first.get(0).replace("﻿", ""));
        }
        return rows;
    }

    private List<List<String>> parseXlsx(final byte[] bytes) {
        List<List<String>> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                List<String> values = new ArrayList<>();
                int lastCell = Math.max(row.getLastCellNum(), 0);
                for (int index = 0; index < lastCell; index++) {
                    Cell cell = row.getCell(index);
                    values.add(cell == null ? "" : formatter.formatCellValue(cell));
                }
                rows.add(values);
            }
        } catch (IOException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Unable to read the XLSX file", exception);
        }
        return rows;
    }

    private boolean isBlank(final List<String> row) {
        return row.stream().allMatch(value -> value == null || value.isBlank());
    }

    private List<String> splitList(final String value) {
        String trimmed = trim(value);
        if (trimmed.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : trimmed.split("\\|")) {
            String item = part.trim();
            if (!item.isBlank()) {
                values.add(item);
            }
        }
        return values;
    }

    private String normalizeHeader(final String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace(" ", "");
    }

    private String upper(final String value) {
        String trimmed = trim(value);
        return trimmed == null ? "" : trimmed.toUpperCase(Locale.ROOT);
    }

    private String trim(final String value) {
        return value == null ? "" : value.trim();
    }

    private QuestionDtos.ImportErrorView error(
            final int rowNumber,
            final String field,
            final String code,
            final String message) {
        return new QuestionDtos.ImportErrorView(rowNumber, field, code, message);
    }

    private String sha256(final byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String toJson(final List<ImportRowPayload> payloads) {
        try {
            return objectMapper.writeValueAsString(payloads);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Unable to store import preview", exception);
        }
    }

    private List<ImportRowPayload> fromJson(final String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ImportRowPayload>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Unable to read import preview", exception);
        }
    }

    /**
     * Persisted preview payload for one valid row.
     */
    public record ImportRowPayload(
            int rowNumber,
            String type,
            UUID subjectId,
            UUID chapterId,
            String stem,
            List<QuestionDtos.OptionInput> options,
            String fillReferenceAnswer,
            List<String> aliases,
            String explanation,
            String difficulty,
            String sourceText,
            List<UUID> tagIds) {
    }
}

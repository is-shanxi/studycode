package com.certpractice.question;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.certpractice.catalog.Chapter;
import com.certpractice.catalog.ChapterRepository;
import com.certpractice.catalog.Subject;
import com.certpractice.catalog.SubjectRepository;
import com.certpractice.catalog.Tag;
import com.certpractice.catalog.TagRepository;
import com.certpractice.identity.AdminAuditLog;
import com.certpractice.practice.SessionItemRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.text.TextNormalizer;
import com.certpractice.shared.web.Guard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Draft authoring and immutable-version cloning. Published content is never
 * edited in place; a new draft version must be created and reviewed.
 */
@Service
public class QuestionAuthoringService {

    private final QuestionRepository questions;
    private final QuestionVersionRepository versions;
    private final QuestionOptionRepository options;
    private final QuestionAnswerAliasRepository aliases;
    private final QuestionVersionTagRepository versionTags;
    private final TagRepository tags;
    private final SubjectRepository subjects;
    private final ChapterRepository chapters;
    private final SessionItemRepository sessionItems;
    private final QuestionAuditService audit;
    private final QuestionSearchService searchService;

    public QuestionAuthoringService(
            final QuestionRepository questions,
            final QuestionVersionRepository versions,
            final QuestionOptionRepository options,
            final QuestionAnswerAliasRepository aliases,
            final QuestionVersionTagRepository versionTags,
            final TagRepository tags,
            final SubjectRepository subjects,
            final ChapterRepository chapters,
            final SessionItemRepository sessionItems,
            final QuestionAuditService audit,
            final QuestionSearchService searchService) {
        this.questions = questions;
        this.versions = versions;
        this.options = options;
        this.aliases = aliases;
        this.versionTags = versionTags;
        this.tags = tags;
        this.subjects = subjects;
        this.chapters = chapters;
        this.sessionItems = sessionItems;
        this.audit = audit;
        this.searchService = searchService;
    }

    @Transactional
    public QuestionDtos.AdminQuestionDetail createQuestion(
            final UUID authorId,
            final QuestionDtos.CreateQuestionRequest request) {
        Question.Type type = parseType(request.type());
        QuestionVersion.Difficulty difficulty = parseDifficulty(request.difficulty());
        validateSubjectChapter(request.subjectId(), request.chapterId());

        Question question = new Question();
        question.setSubjectId(request.subjectId());
        question.setChapterId(request.chapterId());
        question.setType(type);
        question.setStatus(Question.Status.DRAFT);
        question.setCreatedBy(authorId);
        questions.save(question);

        QuestionVersion version = new QuestionVersion();
        version.setQuestionId(question.getId());
        version.setVersionNo(1);
        version.setStatus(Question.Status.DRAFT);
        version.setStem(request.stem().trim());
        version.setExplanation(trimToNull(request.explanation()));
        version.setFillReferenceAnswer(trimToNull(request.fillReferenceAnswer()));
        version.setDifficulty(difficulty);
        version.setSourceText(trimToNull(request.sourceText()));
        version.setAuthorId(authorId);
        version.setContentHash("pending");
        versions.save(version);

        replaceContent(version, request.options(), request.aliases(), request.tagIds(), type);
        version.setContentHash(computeHash(question, version));
        versions.save(version);

        audit.record(authorId, "QUESTION_CREATE", "QUESTION", question.getId(),
                AdminAuditLog.Outcome.SUCCESS, java.util.Map.of("versionNo", version.getVersionNo()));
        return searchService.getAdminQuestion(question.getId());
    }

    @Transactional
    public QuestionDtos.AdminQuestionDetail updateDraft(
            final UUID actorId,
            final UUID versionId,
            final QuestionDtos.UpdateDraftRequest request) {
        QuestionVersion version = versions.findById(versionId)
                .orElseThrow(() -> notFound("Question version not found"));
        Guard.require(version.getStatus() == Question.Status.DRAFT, ErrorCode.STATE_CONFLICT,
                "Only draft versions can be edited");

        Question question = questions.findById(version.getQuestionId())
                .orElseThrow(() -> notFound("Question not found"));

        if (request.chapterId() != null) {
            validateSubjectChapter(question.getSubjectId(), request.chapterId());
            question.setChapterId(request.chapterId());
            questions.save(question);
        }
        if (request.stem() != null) {
            version.setStem(request.stem().trim());
        }
        if (request.explanation() != null) {
            version.setExplanation(trimToNull(request.explanation()));
        }
        if (request.fillReferenceAnswer() != null) {
            version.setFillReferenceAnswer(trimToNull(request.fillReferenceAnswer()));
        }
        if (request.difficulty() != null) {
            version.setDifficulty(parseDifficulty(request.difficulty()));
        }
        if (request.sourceText() != null) {
            version.setSourceText(trimToNull(request.sourceText()));
        }
        if (request.options() != null || request.aliases() != null || request.tagIds() != null) {
            replaceContent(version, request.options(), request.aliases(), request.tagIds(), question.getType());
        }
        validateContent(question.getType(), version);
        version.setContentHash(computeHash(question, version));
        versions.save(version);

        audit.record(actorId, "QUESTION_UPDATE_DRAFT", "QUESTION_VERSION", versionId,
                AdminAuditLog.Outcome.SUCCESS, java.util.Map.of("versionNo", version.getVersionNo()));
        return searchService.getAdminQuestion(question.getId());
    }

    @Transactional
    public QuestionDtos.AdminQuestionDetail cloneVersion(
            final UUID actorId,
            final UUID questionId) {
        Question question = questions.findById(questionId)
                .orElseThrow(() -> notFound("Question not found"));
        QuestionVersion source = versions.findFirstByQuestionIdOrderByVersionNoDesc(questionId)
                .orElseThrow(() -> notFound("Question has no version"));

        QuestionVersion clone = new QuestionVersion();
        clone.setQuestionId(questionId);
        clone.setVersionNo(source.getVersionNo() + 1);
        clone.setStatus(Question.Status.DRAFT);
        clone.setStem(source.getStem());
        clone.setExplanation(source.getExplanation());
        clone.setFillReferenceAnswer(source.getFillReferenceAnswer());
        clone.setDifficulty(source.getDifficulty());
        clone.setSourceText(source.getSourceText());
        clone.setAuthorId(actorId);
        clone.setContentHash(source.getContentHash());
        versions.save(clone);

        copyContent(source.getId(), clone.getId());
        clone.setContentHash(computeHash(question, clone));
        versions.save(clone);

        audit.record(actorId, "QUESTION_CLONE_VERSION", "QUESTION_VERSION", clone.getId(),
                AdminAuditLog.Outcome.SUCCESS, java.util.Map.of("sourceVersionId", source.getId()));
        return searchService.getAdminQuestion(questionId);
    }

    @Transactional
    public void deleteDraft(final UUID actorId, final UUID versionId) {
        QuestionVersion version = versions.findById(versionId)
                .orElseThrow(() -> notFound("Question version not found"));
        Guard.require(version.getStatus() == Question.Status.DRAFT, ErrorCode.STATE_CONFLICT,
                "Only draft versions can be deleted");
        if (sessionItems.existsByQuestionVersionId(versionId)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT,
                    "Draft version is referenced by a practice session");
        }

        UUID questionId = version.getQuestionId();
        Question question = questions.findById(questionId)
                .orElseThrow(() -> notFound("Question not found"));
        Guard.require(!versionId.equals(question.getPublishedVersionId()), ErrorCode.STATE_CONFLICT,
                "Published version cannot be deleted");

        deleteContent(versionId);
        versions.delete(version);
        versions.flush();

        if (versions.findByQuestionIdOrderByVersionNoDesc(questionId).isEmpty()) {
            questions.delete(question);
        }
        audit.record(actorId, "QUESTION_DELETE_DRAFT", "QUESTION_VERSION", versionId,
                AdminAuditLog.Outcome.SUCCESS, java.util.Map.of("questionId", questionId));
    }

    public void validateForPublish(final QuestionVersion version) {
        Guard.require(version.getStem() != null && !version.getStem().isBlank(),
                ErrorCode.VALIDATION_FAILED, "Question stem is required");
        Guard.require(version.getExplanation() != null && !version.getExplanation().isBlank(),
                ErrorCode.VALIDATION_FAILED, "Question explanation is required before publishing");
        Guard.require(version.getSourceText() != null && !version.getSourceText().isBlank(),
                ErrorCode.VALIDATION_FAILED, "Question source is required before publishing");
        Guard.require(version.getDifficulty() != null, ErrorCode.VALIDATION_FAILED,
                "Question difficulty is required before publishing");

        Question question = questions.findById(version.getQuestionId())
                .orElseThrow(() -> notFound("Question not found"));
        List<QuestionOption> currentOptions = options
                .findByQuestionVersionIdOrderBySortOrderAsc(version.getId());
        List<QuestionAnswerAlias> currentAliases = aliases.findByQuestionVersionId(version.getId());
        validateAnswerShape(question.getType(), version, currentOptions, currentAliases);
    }

    private void replaceContent(
            final QuestionVersion version,
            final List<QuestionDtos.OptionInput> optionInputs,
            final List<String> aliasInputs,
            final List<UUID> tagIds,
            final Question.Type type) {
        if (optionInputs != null) {
            options.deleteByQuestionVersionId(version.getId());
            List<QuestionDtos.OptionInput> inputs = optionInputs;
            for (int index = 0; index < inputs.size(); index++) {
                QuestionDtos.OptionInput input = inputs.get(index);
                QuestionOption option = new QuestionOption();
                option.setQuestionVersionId(version.getId());
                option.setOptionKey(input.key().trim());
                option.setContent(input.content().trim());
                option.setCorrect(input.correct());
                option.setSortOrder(index);
                options.save(option);
            }
        }
        if (aliasInputs != null) {
            aliases.deleteByQuestionVersionId(version.getId());
            Set<String> normalizedSeen = new HashSet<>();
            for (String raw : aliasInputs) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String value = raw.trim();
                String normalized = TextNormalizer.normalizeFillAnswer(value);
                Guard.require(normalized.length() <= 255, ErrorCode.VALIDATION_FAILED,
                        "Answer alias is too long");
                Guard.require(normalizedSeen.add(normalized), ErrorCode.VALIDATION_FAILED,
                        "Duplicate answer alias: " + value);
                QuestionAnswerAlias alias = new QuestionAnswerAlias();
                alias.setQuestionVersionId(version.getId());
                alias.setValue(value);
                alias.setNormalizedValue(normalized);
                aliases.save(alias);
            }
        }
        if (tagIds != null) {
            versionTags.deleteByIdQuestionVersionId(version.getId());
            List<UUID> uniqueTagIds = tagIds.stream().distinct().toList();
            List<Tag> found = tags.findAllById(uniqueTagIds);
            Guard.require(found.size() == uniqueTagIds.size(), ErrorCode.VALIDATION_FAILED,
                    "One or more tags do not exist");
            for (UUID tagId : uniqueTagIds) {
                QuestionVersionTag versionTag = new QuestionVersionTag();
                QuestionVersionTagId id = new QuestionVersionTagId();
                id.setQuestionVersionId(version.getId());
                id.setTagId(tagId);
                versionTag.setId(id);
                versionTags.save(versionTag);
            }
        }

        if (type == Question.Type.SINGLE_BLANK && version.getFillReferenceAnswer() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Reference answer is required");
        }
    }

    private void validateContent(
            final Question.Type type,
            final QuestionVersion version) {
        Guard.require(version.getStem() != null && !version.getStem().isBlank(),
                ErrorCode.VALIDATION_FAILED, "Question stem is required");
        List<QuestionOption> storedOptions = options
                .findByQuestionVersionIdOrderBySortOrderAsc(version.getId());
        List<QuestionAnswerAlias> storedAliases = aliases.findByQuestionVersionId(version.getId());
        validateAnswerShape(type, version, storedOptions, storedAliases);
    }

    private void validateAnswerShape(
            final Question.Type type,
            final QuestionVersion version,
            final List<QuestionOption> storedOptions,
            final List<QuestionAnswerAlias> storedAliases) {
        if (type == Question.Type.SINGLE_CHOICE) {
            Guard.require(storedOptions.size() >= 2, ErrorCode.VALIDATION_FAILED,
                    "Single-choice questions require at least two options");
            long correctCount = storedOptions.stream().filter(QuestionOption::isCorrect).count();
            Guard.require(correctCount == 1, ErrorCode.VALIDATION_FAILED,
                    "Single-choice questions require exactly one correct option");
            Set<String> keys = new HashSet<>();
            for (QuestionOption option : storedOptions) {
                Guard.require(option.getOptionKey() != null && !option.getOptionKey().isBlank(),
                        ErrorCode.VALIDATION_FAILED, "Option key is required");
                Guard.require(keys.add(option.getOptionKey()), ErrorCode.VALIDATION_FAILED,
                        "Duplicate option key: " + option.getOptionKey());
            }
            Guard.require(version.getFillReferenceAnswer() == null
                            || version.getFillReferenceAnswer().isBlank(),
                    ErrorCode.VALIDATION_FAILED,
                    "Single-choice questions must not define a fill reference answer");
            Guard.require(storedAliases.isEmpty(), ErrorCode.VALIDATION_FAILED,
                    "Single-choice questions must not define answer aliases");
        } else {
            Guard.require(storedOptions.isEmpty(), ErrorCode.VALIDATION_FAILED,
                    "Single-blank questions must not define options");
            Guard.require(version.getFillReferenceAnswer() != null
                            && !version.getFillReferenceAnswer().isBlank(),
                    ErrorCode.VALIDATION_FAILED, "Reference answer is required");
        }
    }

    private void copyContent(final UUID sourceVersionId, final UUID targetVersionId) {
        for (QuestionOption source : options.findByQuestionVersionIdOrderBySortOrderAsc(sourceVersionId)) {
            QuestionOption copy = new QuestionOption();
            copy.setQuestionVersionId(targetVersionId);
            copy.setOptionKey(source.getOptionKey());
            copy.setContent(source.getContent());
            copy.setCorrect(source.isCorrect());
            copy.setSortOrder(source.getSortOrder());
            options.save(copy);
        }
        for (QuestionAnswerAlias source : aliases.findByQuestionVersionId(sourceVersionId)) {
            QuestionAnswerAlias copy = new QuestionAnswerAlias();
            copy.setQuestionVersionId(targetVersionId);
            copy.setValue(source.getValue());
            copy.setNormalizedValue(source.getNormalizedValue());
            aliases.save(copy);
        }
        for (QuestionVersionTag source : versionTags.findByIdQuestionVersionId(sourceVersionId)) {
            QuestionVersionTag copy = new QuestionVersionTag();
            QuestionVersionTagId id = new QuestionVersionTagId();
            id.setQuestionVersionId(targetVersionId);
            id.setTagId(source.getId().getTagId());
            copy.setId(id);
            versionTags.save(copy);
        }
    }

    private void deleteContent(final UUID versionId) {
        options.deleteByQuestionVersionId(versionId);
        aliases.deleteByQuestionVersionId(versionId);
        versionTags.deleteByIdQuestionVersionId(versionId);
    }

    private String computeHash(final Question question, final QuestionVersion version) {
        StringBuilder canonical = new StringBuilder();
        canonical.append(question.getType().name()).append('\n');
        canonical.append(TextNormalizer.normalizeFillAnswer(version.getStem())).append('\n');
        canonical.append(normalizeOptional(version.getExplanation())).append('\n');
        canonical.append(normalizeOptional(version.getFillReferenceAnswer())).append('\n');
        canonical.append(version.getDifficulty() == null ? "" : version.getDifficulty().name()).append('\n');

        List<QuestionOption> storedOptions = options
                .findByQuestionVersionIdOrderBySortOrderAsc(version.getId());
        storedOptions.stream()
                .sorted(Comparator.comparing(QuestionOption::getOptionKey))
                .forEach(option -> canonical
                        .append(option.getOptionKey()).append('=')
                        .append(TextNormalizer.normalizeFillAnswer(option.getContent()))
                        .append(':').append(option.isCorrect()).append('\n'));

        aliases.findByQuestionVersionId(version.getId()).stream()
                .map(QuestionAnswerAlias::getNormalizedValue)
                .sorted()
                .forEach(value -> canonical.append("alias=").append(value).append('\n'));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateSubjectChapter(final UUID subjectId, final UUID chapterId) {
        Subject subject = subjects.findById(subjectId)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "Subject does not exist"));
        Chapter chapter = chapters.findById(chapterId)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "Chapter does not exist"));
        Guard.require(chapter.getSubjectId().equals(subject.getId()), ErrorCode.VALIDATION_FAILED,
                "Chapter does not belong to the selected subject");
    }

    private String normalizeOptional(final String value) {
        return value == null ? "" : TextNormalizer.normalizeFillAnswer(value);
    }

    private String trimToNull(final String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Question.Type parseType(final String value) {
        try {
            return Question.Type.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid question type", exception);
        }
    }

    private QuestionVersion.Difficulty parseDifficulty(final String value) {
        try {
            return QuestionVersion.Difficulty.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid difficulty", exception);
        }
    }

    private static ApiException notFound(final String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message);
    }
}

package com.certpractice.question;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Question payloads. The public and practice views intentionally contain no
 * correct-answer marker, reference answer, alias or explanation.
 */
public final class QuestionDtos {

    private QuestionDtos() {
    }

    public record TagRef(UUID id, String name) {
    }

    public record ChapterRef(UUID id, String name) {
    }

    public record OptionSafe(UUID id, String key, String content) {
    }

    public record OptionAdmin(UUID id, String key, String content, boolean correct) {
    }

    public record QuestionSummary(
            UUID questionId,
            UUID questionVersionId,
            String type,
            String stem,
            String difficulty,
            ChapterRef chapter,
            List<TagRef> tags,
            String status) {
    }

    public record PublicQuestionView(
            UUID questionId,
            UUID questionVersionId,
            String type,
            String stem,
            String difficulty,
            ChapterRef chapter,
            List<TagRef> tags,
            List<OptionSafe> options) {
    }

    public record OptionInput(
            @NotBlank @Size(max = 8) String key,
            @NotBlank String content,
            boolean correct) {
    }

    public record CreateQuestionRequest(
            @NotNull UUID subjectId,
            @NotNull UUID chapterId,
            @NotNull String type,
            @NotBlank String stem,
            String explanation,
            String fillReferenceAnswer,
            @NotNull String difficulty,
            String sourceText,
            @Valid List<OptionInput> options,
            List<String> aliases,
            List<UUID> tagIds) {
    }

    public record UpdateDraftRequest(
            UUID chapterId,
            String stem,
            String explanation,
            String fillReferenceAnswer,
            String difficulty,
            String sourceText,
            @Valid List<OptionInput> options,
            List<String> aliases,
            List<UUID> tagIds) {
    }

    public record VersionView(
            UUID versionId,
            int versionNo,
            String status,
            String stem,
            String explanation,
            String fillReferenceAnswer,
            String difficulty,
            String sourceText,
            UUID authorId,
            UUID reviewedBy,
            Instant reviewedAt,
            Instant publishedAt,
            Instant offlineAt,
            Instant supersededAt,
            List<OptionAdmin> options,
            List<String> aliases,
            List<TagRef> tags) {
    }

    public record AdminQuestionDetail(
            UUID questionId,
            UUID subjectId,
            UUID chapterId,
            String type,
            String status,
            UUID publishedVersionId,
            List<VersionView> versions) {
    }

    public record AdminQuestionSummary(
            UUID questionId,
            UUID currentVersionId,
            String type,
            String status,
            String stem,
            String difficulty,
            Instant updatedAt) {
    }

    public record ReviewRequest(
            String comment,
            Boolean publishNow) {
    }

    public record QuestionStatusResponse(
            UUID questionId,
            UUID versionId,
            String status) {
    }

    public record ImportErrorView(
            int rowNumber,
            String field,
            String code,
            String message) {
    }

    public record ImportBatchView(
            UUID batchId,
            String status,
            String fileName,
            int totalRows,
            int validRows,
            int invalidRows,
            Instant previewExpiresAt,
            Instant confirmedAt,
            List<ImportErrorView> errors) {
    }
}

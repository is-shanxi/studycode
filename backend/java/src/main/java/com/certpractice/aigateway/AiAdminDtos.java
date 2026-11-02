package com.certpractice.aigateway;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.certpractice.aigateway.dto.AiGeneratedCandidate;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Administrator generation and configuration payloads.
 */
public final class AiAdminDtos {

    private AiAdminDtos() {
    }

    public record GenerationBatchRequest(
            @NotNull UUID subjectId,
            UUID chapterId,
            @NotEmpty List<UUID> sourceQuestionIds,
            Integer targetCount,
            String difficulty) {
    }

    public record GenerationCandidateView(
            UUID candidateId,
            UUID batchId,
            UUID sourceQuestionVersionId,
            AiGeneratedCandidate candidate,
            String contentHash,
            String validationStatus,
            String reviewStatus,
            UUID convertedQuestionVersionId,
            Instant createdAt) {
    }

    public record GenerationBatchView(
            UUID batchId,
            String status,
            UUID subjectId,
            UUID chapterId,
            int targetCount,
            String modelName,
            String promptVersion,
            int attemptCount,
            String lastError,
            Instant createdAt,
            Instant completedAt,
            List<GenerationCandidateView> candidates) {
    }

    public record CandidateReviewRequest(
            @NotBlank String decision,
            String comment) {
    }

    public record ConfigView(
            String key,
            int versionNo,
            Instant effectiveFrom,
            UUID createdBy,
            JsonNode value) {
    }

    public record UpdateConfigRequest(
            @NotBlank String key,
            @NotNull JsonNode value) {
    }
}

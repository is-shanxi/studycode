package com.certpractice.practice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.certpractice.question.QuestionDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Practice session payloads. The in-progress view never contains answer keys,
 * reference answers, aliases or official explanations.
 */
public final class PracticeDtos {

    private PracticeDtos() {
    }

    public record Filters(
            UUID certificateId,
            UUID subjectId,
            List<UUID> chapterIds,
            List<UUID> tagIds,
            List<String> difficulty,
            Integer historyDays) {
    }

    public record CompositionSpec(
            Double originalRatio,
            Double aiVariantRatio) {
    }

    public record CreateSessionRequest(
            @NotNull String mode,
            Filters filters,
            Integer requestedCount,
            CompositionSpec composition) {
    }

    public record AnswerView(
            UUID selectedOptionId,
            String text,
            int answerRevision) {
    }

    public record SessionItemView(
            UUID itemId,
            int ordinal,
            String sourceType,
            String selectionBucket,
            QuestionDtos.PublicQuestionView question,
            AnswerView answer,
            String autoMatchResult,
            String selfAssessmentResult,
            String finalResult) {
    }

    public record BucketCounts(int unseen, int wrong, int normal) {
    }

    public record CompositionView(int original, int aiVariant, BucketCounts buckets) {
    }

    public record SessionView(
            UUID sessionId,
            String mode,
            String status,
            int rowVersion,
            int requestedCount,
            int actualCount,
            CompositionView composition,
            List<String> allowedActions,
            List<SessionItemView> items) {
    }

    public record AnswerInput(
            UUID selectedOptionId,
            String text) {
    }

    public record SaveAnswerRequest(
            Integer answerRevision,
            @NotNull AnswerInput answer) {
    }

    public record SaveAnswerResponse(
            UUID itemId,
            int answerRevision) {
    }

    public record SelfAssessmentRequest(
            @NotBlank String result) {
    }

    public record SelfAssessmentResponse(
            UUID itemId,
            String finalResult,
            String sessionStatus) {
    }

    public record ResultItemView(
            UUID itemId,
            UUID questionId,
            UUID questionVersionId,
            String type,
            String stem,
            String difficulty,
            String finalResult,
            UUID selectedOptionId,
            String text,
            UUID correctOptionId,
            String referenceAnswer,
            String explanation,
            String autoMatchResult,
            String selfAssessmentResult,
            List<QuestionDtos.OptionSafe> options) {
    }

    public record ObjectiveSummary(int answered, int correct) {
    }

    public record FillSummary(int autoMatched, int selfPassed, int selfFailed) {
    }

    public record ResultView(
            UUID sessionId,
            String mode,
            String status,
            int requestedCount,
            int actualCount,
            Instant startedAt,
            Instant submittedAt,
            Instant completedAt,
            ObjectiveSummary objective,
            FillSummary fill,
            List<ResultItemView> items) {
    }

    public record RecordSummary(
            UUID sessionId,
            String mode,
            String status,
            int requestedCount,
            int actualCount,
            int objectiveCorrect,
            int objectiveAnswered,
            Instant startedAt,
            Instant submittedAt,
            Instant completedAt) {
    }

    public record FavoriteView(
            UUID questionId,
            UUID questionVersionId,
            String type,
            String stem,
            String difficulty,
            String status,
            Instant favoritedAt) {
    }

    public record WrongQuestionView(
            UUID questionId,
            UUID questionVersionId,
            String type,
            String stem,
            String difficulty,
            String status,
            Instant lastFailedAt) {
    }
}

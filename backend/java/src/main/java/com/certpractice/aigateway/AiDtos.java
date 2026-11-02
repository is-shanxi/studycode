package com.certpractice.aigateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Browser-facing AI gateway payloads. Official answers are never included in
 * conversation responses; assistant text is the only model output exposed.
 */
public final class AiDtos {

    private AiDtos() {
    }

    public record CreateConversationRequest(
            @NotBlank String scope,
            UUID sessionItemId) {
    }

    public record MessageView(
            UUID messageId,
            String role,
            String content,
            String provider,
            String modelName,
            Integer inputTokens,
            Integer outputTokens,
            Instant createdAt) {
    }

    public record ConversationView(
            UUID conversationId,
            String scope,
            String status,
            UUID questionId,
            UUID questionVersionId,
            UUID sessionItemId,
            Instant createdAt,
            Instant lastMessageAt,
            List<MessageView> messages) {
    }

    public record SendMessageRequest(
            @NotBlank @Size(max = 4000) String content) {
    }

    public record UsageView(int inputTokens, int outputTokens) {
    }

    public record TeamBudgetView(
            BigDecimal limit,
            BigDecimal used,
            boolean available) {
    }

    public record QuotaView(
            LocalDate date,
            int dailyLimit,
            int used,
            int remaining,
            int sessionTurnLimit,
            int turnsUsed,
            int maxInputChars,
            boolean officialAnswerContextEnabled,
            TeamBudgetView teamBudget) {
    }

    public record SendMessageResponse(
            UUID conversationId,
            UUID requestId,
            MessageView userMessage,
            MessageView assistantMessage,
            UsageView usage,
            QuotaView quota) {
    }
}

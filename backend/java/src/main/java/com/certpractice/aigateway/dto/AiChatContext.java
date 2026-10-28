package com.certpractice.aigateway.dto;

/**
 * Minimal question context. Official answers and explanations are not included
 * unless the server-side AI policy explicitly enables them.
 */
public record AiChatContext(
        String scope,
        String stem,
        String questionType,
        String userAnswer,
        String selfAssessment) {
}

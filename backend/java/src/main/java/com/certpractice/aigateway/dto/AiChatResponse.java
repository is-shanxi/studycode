package com.certpractice.aigateway.dto;

import java.util.UUID;

public record AiChatResponse(
        UUID requestId,
        String content,
        AiUsage usage,
        String providerRequestId) {
}

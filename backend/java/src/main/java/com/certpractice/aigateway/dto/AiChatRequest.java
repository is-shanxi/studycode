package com.certpractice.aigateway.dto;

import java.util.List;
import java.util.UUID;

/**
 * Java to Python internal chat contract (camelCase JSON).
 */
public record AiChatRequest(
        UUID requestId,
        String provider,
        String model,
        List<AiChatMessage> messages,
        AiChatContext context,
        AiChatLimits limits) {
}

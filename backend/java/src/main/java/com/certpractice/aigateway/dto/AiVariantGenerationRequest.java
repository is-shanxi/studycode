package com.certpractice.aigateway.dto;

import java.util.List;
import java.util.UUID;

public record AiVariantGenerationRequest(
        UUID requestId,
        UUID sourceQuestionVersionId,
        String stem,
        String type,
        String chapter,
        List<String> tags,
        Integer targetCount,
        AiVariantConstraints constraints) {
}

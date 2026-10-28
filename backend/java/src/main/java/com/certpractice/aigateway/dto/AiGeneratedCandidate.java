package com.certpractice.aigateway.dto;

import java.util.List;

public record AiGeneratedCandidate(
        String stem,
        String type,
        List<AiCandidateOption> options,
        String referenceAnswer,
        String explanation,
        String difficulty,
        List<String> suggestedTags) {
}

package com.certpractice.aigateway;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.certpractice.aigateway.dto.AiGeneratedCandidate;
import com.certpractice.aigateway.dto.AiVariantConstraints;
import com.certpractice.aigateway.dto.AiVariantGenerationRequest;
import com.certpractice.question.QuestionGenerationQueryService;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.observability.TraceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls for claimable generation batches, calls the Python service outside any
 * database transaction, then stores and validates the returned candidates.
 */
@Component
public class AiGenerationScheduler {

    private final AiGenerationLeaseService leaseService;
    private final AiGenerationBatchRepository batches;
    private final AiCandidateService candidateService;
    private final QuestionGenerationQueryService questionQuery;
    private final AiServiceClient aiServiceClient;
    private final AiGenerationProperties properties;
    private final ObjectMapper objectMapper;

    public AiGenerationScheduler(
            final AiGenerationLeaseService leaseService,
            final AiGenerationBatchRepository batches,
            final AiCandidateService candidateService,
            final QuestionGenerationQueryService questionQuery,
            final AiServiceClient aiServiceClient,
            final AiGenerationProperties properties,
            final ObjectMapper objectMapper) {
        this.leaseService = leaseService;
        this.batches = batches;
        this.candidateService = candidateService;
        this.questionQuery = questionQuery;
        this.aiServiceClient = aiServiceClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${app.ai.generation.poll-delay-ms:5000}")
    public void poll() {
        if (!properties.isSchedulerEnabled()) {
            return;
        }
        int processed = 0;
        while (processed < 10 && runOnce()) {
            processed++;
        }
    }

    /**
     * Claims and processes at most one batch. Returns false when no batch is
     * currently claimable.
     */
    public boolean runOnce() {
        AiGenerationLeaseService.ClaimedBatch claimed = leaseService.claimNext();
        if (claimed == null) {
            return false;
        }
        try {
            AiGenerationBatch batch = batches.findById(claimed.batchId())
                    .orElseThrow(() -> new ApiException(
                            ErrorCode.NOT_FOUND, "Generation batch not found"));
            List<QuestionGenerationQueryService.GenerationSource> sources =
                    questionQuery.load(parseQuestionIds(batch.getSourceQuestionIds()));
            int perSource = Math.max(1, batch.getTargetCount() / sources.size());
            List<AiCandidateService.GeneratedCandidateWithSource> generated = new ArrayList<>();
            for (QuestionGenerationQueryService.GenerationSource source : sources) {
                AiVariantGenerationRequest request = new AiVariantGenerationRequest(
                        UUID.randomUUID(),
                        source.versionId(),
                        source.stem(),
                        source.type(),
                        source.chapter(),
                        source.tags(),
                        perSource,
                        new AiVariantConstraints(batch.getDifficulty(), batch.getPromptVersion()));
                List<AiGeneratedCandidate> candidates = aiServiceClient.generateVariants(
                        request, TraceContext.currentTraceId());
                candidates.forEach(candidate -> generated.add(
                        new AiCandidateService.GeneratedCandidateWithSource(
                                source.versionId(), candidate)));
            }
            candidateService.store(claimed.batchId(), generated);
            leaseService.markForReview(claimed.batchId());
        } catch (RuntimeException exception) {
            leaseService.markFailure(claimed.batchId(), safeError(exception));
        }
        return true;
    }

    private List<UUID> parseQuestionIds(final String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
                    })
                    .stream()
                    .map(UUID::fromString)
                    .toList();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Generation batch source list is unreadable", exception);
        }
    }

    private String safeError(final RuntimeException exception) {
        if (exception instanceof ApiException apiException) {
            return apiException.getMessage();
        }
        return "Generation failed: " + exception.getClass().getSimpleName();
    }
}

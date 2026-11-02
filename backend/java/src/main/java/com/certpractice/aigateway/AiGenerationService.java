package com.certpractice.aigateway;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.certpractice.identity.AdminAuditLog;
import com.certpractice.identity.AdminAuditService;
import com.certpractice.question.QuestionGenerationQueryService;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.web.Guard;
import com.certpractice.shared.web.PageQuery;
import com.certpractice.shared.web.PageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator-facing batch creation, listing and detail views.
 */
@Service
public class AiGenerationService {

    private final AiGenerationBatchRepository batches;
    private final AiGenerationCandidateRepository candidates;
    private final QuestionGenerationQueryService questionQuery;
    private final AiCandidateService candidateService;
    private final AiGenerationProperties generationProperties;
    private final AiProperties aiProperties;
    private final AdminAuditService audit;
    private final ObjectMapper objectMapper;

    public AiGenerationService(
            final AiGenerationBatchRepository batches,
            final AiGenerationCandidateRepository candidates,
            final QuestionGenerationQueryService questionQuery,
            final AiCandidateService candidateService,
            final AiGenerationProperties generationProperties,
            final AiProperties aiProperties,
            final AdminAuditService audit,
            final ObjectMapper objectMapper) {
        this.batches = batches;
        this.candidates = candidates;
        this.questionQuery = questionQuery;
        this.candidateService = candidateService;
        this.generationProperties = generationProperties;
        this.aiProperties = aiProperties;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AiAdminDtos.GenerationBatchView create(
            final UUID adminId,
            final AiAdminDtos.GenerationBatchRequest request) {
        int targetCount = request.targetCount() == null
                ? generationProperties.getDefaultTargetCount() : request.targetCount();
        Guard.require(targetCount >= 1 && targetCount <= 100, ErrorCode.VALIDATION_FAILED,
                "targetCount must be between 1 and 100");
        Guard.require(request.sourceQuestionIds() != null && !request.sourceQuestionIds().isEmpty(),
                ErrorCode.VALIDATION_FAILED, "sourceQuestionIds must not be empty");
        String difficulty = normalizeDifficulty(request.difficulty());

        List<QuestionGenerationQueryService.GenerationSource> sources =
                questionQuery.load(request.sourceQuestionIds());
        boolean subjectMismatch = sources.stream()
                .anyMatch(source -> !source.subjectId().equals(request.subjectId()));
        Guard.require(!subjectMismatch, ErrorCode.VALIDATION_FAILED,
                "All source questions must belong to the selected subject");

        AiGenerationBatch batch = new AiGenerationBatch();
        batch.setRequestedBy(adminId);
        batch.setSubjectId(request.subjectId());
        batch.setChapterId(request.chapterId());
        batch.setSourceQuestionIds(toJson(request.sourceQuestionIds()));
        batch.setTargetCount(targetCount);
        batch.setDifficulty(difficulty);
        batch.setModelName(aiProperties.model());
        batch.setPromptVersion(generationProperties.getPromptVersion());
        batch.setStatus(AiGenerationBatch.Status.CREATED);
        batches.save(batch);
        audit.record(adminId, "AI_GENERATION_BATCH_CREATE", "AI_GENERATION_BATCH",
                batch.getId(), AdminAuditLog.Outcome.SUCCESS,
                Map.of("targetCount", targetCount, "sourceCount", sources.size()));
        return toView(batch);
    }

    @Transactional(readOnly = true)
    public PageResponse<AiAdminDtos.GenerationBatchView> list(final PageQuery pageQuery) {
        Page<AiGenerationBatch> page = batches.findAllByOrderByCreatedAtDesc(pageQuery.toPageable());
        List<AiAdminDtos.GenerationBatchView> items = page.getContent().stream()
                .map(this::toView)
                .toList();
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AiAdminDtos.GenerationBatchView get(final UUID batchId) {
        AiGenerationBatch batch = batches.findById(batchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Generation batch not found"));
        return toView(batch);
    }

    private AiAdminDtos.GenerationBatchView toView(final AiGenerationBatch batch) {
        List<AiAdminDtos.GenerationCandidateView> candidateViews =
                candidates.findByBatchIdOrderByCreatedAtAsc(batch.getId()).stream()
                        .map(candidateService::toView)
                        .toList();
        return new AiAdminDtos.GenerationBatchView(
                batch.getId(),
                batch.getStatus().name(),
                batch.getSubjectId(),
                batch.getChapterId(),
                batch.getTargetCount(),
                batch.getModelName(),
                batch.getPromptVersion(),
                batch.getAttemptCount(),
                batch.getLastError(),
                batch.getCreatedAt(),
                batch.getCompletedAt(),
                candidateViews);
    }

    private String normalizeDifficulty(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String difficulty = value.toUpperCase(Locale.ROOT);
        Guard.require(List.of("EASY", "MEDIUM", "HARD").contains(difficulty),
                ErrorCode.VALIDATION_FAILED, "difficulty must be EASY, MEDIUM or HARD");
        return difficulty;
    }

    private String toJson(final List<UUID> questionIds) {
        try {
            return objectMapper.writeValueAsString(
                    questionIds.stream().map(UUID::toString).toList());
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Unable to serialize source question IDs", exception);
        }
    }
}

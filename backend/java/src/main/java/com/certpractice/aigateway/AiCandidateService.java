package com.certpractice.aigateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.certpractice.aigateway.dto.AiCandidateOption;
import com.certpractice.aigateway.dto.AiGeneratedCandidate;
import com.certpractice.identity.AdminAuditLog;
import com.certpractice.identity.AdminAuditService;
import com.certpractice.question.AiVariantPublisher;
import com.certpractice.question.QuestionVersionRepository;
import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import com.certpractice.shared.web.Guard;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidate validation, duplicate rejection and conversion into formal
 * published question versions after administrator review.
 */
@Service
public class AiCandidateService {

    private final AiGenerationBatchRepository batches;
    private final AiGenerationCandidateRepository candidates;
    private final QuestionVersionRepository questionVersions;
    private final AiVariantPublisher publisher;
    private final AiGenerationProperties properties;
    private final AdminAuditService audit;
    private final ObjectMapper objectMapper;

    public AiCandidateService(
            final AiGenerationBatchRepository batches,
            final AiGenerationCandidateRepository candidates,
            final QuestionVersionRepository questionVersions,
            final AiVariantPublisher publisher,
            final AiGenerationProperties properties,
            final AdminAuditService audit,
            final ObjectMapper objectMapper) {
        this.batches = batches;
        this.candidates = candidates;
        this.questionVersions = questionVersions;
        this.publisher = publisher;
        this.properties = properties;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int store(
            final UUID batchId,
            final List<GeneratedCandidateWithSource> generated) {
        AiGenerationBatch batch = batches.findById(batchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Generation batch not found"));
        Set<String> seenHashes = new HashSet<>();
        int stored = 0;
        for (GeneratedCandidateWithSource entry : generated) {
            if (entry.candidate() == null) {
                continue;
            }
            String json = toJson(entry.candidate());
            String hash = sha256(json);
            if (!seenHashes.add(hash) || candidates.findByContentHash(hash).isPresent()) {
                continue;
            }
            boolean valid = isValid(entry.candidate());
            AiGenerationCandidate entity = new AiGenerationCandidate();
            entity.setBatchId(batch.getId());
            entity.setSourceQuestionVersionId(entry.sourceQuestionVersionId());
            entity.setCandidateJson(json);
            entity.setContentHash(hash);
            entity.setValidationStatus(valid
                    ? AiGenerationCandidate.ValidationStatus.VALID
                    : AiGenerationCandidate.ValidationStatus.INVALID);
            entity.setReviewStatus(valid
                    ? AiGenerationCandidate.ReviewStatus.PENDING
                    : AiGenerationCandidate.ReviewStatus.REJECTED);
            candidates.save(entity);
            stored++;
        }
        return stored;
    }

    @Transactional
    public AiAdminDtos.GenerationCandidateView review(
            final UUID adminId,
            final UUID candidateId,
            final String decision,
            final String comment) {
        AiGenerationCandidate candidate = candidates.findById(candidateId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Candidate not found"));
        Guard.require(candidate.getReviewStatus() == AiGenerationCandidate.ReviewStatus.PENDING,
                ErrorCode.STATE_CONFLICT, "Candidate has already been reviewed");
        Guard.require(candidate.getValidationStatus() == AiGenerationCandidate.ValidationStatus.VALID,
                ErrorCode.STATE_CONFLICT, "Only validated candidates can be reviewed");
        Decision parsed = parseDecision(decision);

        if (parsed == Decision.REJECT) {
            candidate.setReviewStatus(AiGenerationCandidate.ReviewStatus.REJECTED);
            candidates.save(candidate);
            audit.record(adminId, "AI_CANDIDATE_REJECT", "AI_GENERATION_CANDIDATE",
                    candidateId, AdminAuditLog.Outcome.REJECTED,
                    Map.of("comment", comment == null ? "" : comment));
            return toView(candidate);
        }

        AiGenerationBatch batch = batches.findById(candidate.getBatchId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Generation batch not found"));
        AiGeneratedCandidate generated = fromJson(candidate.getCandidateJson());
        List<AiVariantPublisher.OptionSpec> options = generated.options() == null ? List.of()
                : generated.options().stream()
                        .map(option -> new AiVariantPublisher.OptionSpec(
                                option.key(),
                                option.content(),
                                Boolean.TRUE.equals(option.isCorrect())))
                        .toList();
        UUID versionId = publisher.publish(new AiVariantPublisher.PublishCommand(
                adminId,
                batch.getSubjectId(),
                batch.getChapterId(),
                generated.stem(),
                generated.type(),
                options,
                generated.referenceAnswer(),
                generated.explanation(),
                generated.difficulty(),
                generated.suggestedTags(),
                candidate.getSourceQuestionVersionId(),
                candidate.getId(),
                batch.getModelName(),
                batch.getPromptVersion()));

        candidate.setReviewStatus(AiGenerationCandidate.ReviewStatus.APPROVED);
        candidate.setConvertedQuestionVersionId(versionId);
        candidates.save(candidate);
        audit.record(adminId, "AI_CANDIDATE_APPROVE", "AI_GENERATION_CANDIDATE",
                candidateId, AdminAuditLog.Outcome.SUCCESS,
                Map.of("questionVersionId", versionId));
        return toView(candidate);
    }

    public AiAdminDtos.GenerationCandidateView toView(final AiGenerationCandidate candidate) {
        return new AiAdminDtos.GenerationCandidateView(
                candidate.getId(),
                candidate.getBatchId(),
                candidate.getSourceQuestionVersionId(),
                fromJsonOrNull(candidate.getCandidateJson()),
                candidate.getContentHash(),
                candidate.getValidationStatus().name(),
                candidate.getReviewStatus().name(),
                candidate.getConvertedQuestionVersionId(),
                candidate.getCreatedAt());
    }

    private boolean isValid(final AiGeneratedCandidate candidate) {
        if (candidate.stem() == null || candidate.stem().isBlank()
                || candidate.explanation() == null || candidate.explanation().isBlank()) {
            return false;
        }
        try {
            String type = candidate.type() == null
                    ? "" : candidate.type().toUpperCase(Locale.ROOT);
            String difficulty = candidate.difficulty() == null
                    ? "" : candidate.difficulty().toUpperCase(Locale.ROOT);
            if (!Set.of("SINGLE_CHOICE", "SINGLE_BLANK").contains(type)
                    || !Set.of("EASY", "MEDIUM", "HARD").contains(difficulty)) {
                return false;
            }
            if ("SINGLE_CHOICE".equals(type) && !validChoice(candidate.options())) {
                return false;
            }
            if ("SINGLE_BLANK".equals(type)
                    && (candidate.referenceAnswer() == null || candidate.referenceAnswer().isBlank())) {
                return false;
            }
        } catch (RuntimeException exception) {
            return false;
        }
        return questionVersions.countByStemIgnoreCase(candidate.stem().trim()) == 0;
    }

    private boolean validChoice(final List<AiCandidateOption> options) {
        if (options == null || options.size() < 2) {
            return false;
        }
        Set<String> keys = new HashSet<>();
        int correct = 0;
        for (AiCandidateOption option : options) {
            if (option == null || option.key() == null || option.key().isBlank()
                    || option.content() == null || option.content().isBlank()) {
                return false;
            }
            if (!keys.add(option.key().trim())) {
                return false;
            }
            if (Boolean.TRUE.equals(option.isCorrect())) {
                correct++;
            }
        }
        return correct == 1;
    }

    private Decision parseDecision(final String value) {
        try {
            return Decision.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "decision must be APPROVE or REJECT", exception);
        }
    }

    private String toJson(final AiGeneratedCandidate candidate) {
        try {
            return objectMapper.writeValueAsString(candidate);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Generated candidate is not serializable", exception);
        }
    }

    private AiGeneratedCandidate fromJson(final String json) {
        try {
            return objectMapper.readValue(json, AiGeneratedCandidate.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Stored candidate is unreadable", exception);
        }
    }

    private AiGeneratedCandidate fromJsonOrNull(final String json) {
        try {
            return objectMapper.readValue(json, AiGeneratedCandidate.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private String sha256(final String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private enum Decision {
        APPROVE,
        REJECT
    }

    public record GeneratedCandidateWithSource(
            UUID sourceQuestionVersionId,
            AiGeneratedCandidate candidate) {
    }
}

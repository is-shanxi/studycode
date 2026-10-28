package com.certpractice.aigateway;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGenerationCandidateRepository extends JpaRepository<AiGenerationCandidate, UUID> {

    List<AiGenerationCandidate> findByBatchIdOrderByCreatedAtAsc(UUID batchId);

    Optional<AiGenerationCandidate> findByContentHash(String contentHash);

    Optional<AiGenerationCandidate> findByIdAndReviewStatus(
            UUID id,
            AiGenerationCandidate.ReviewStatus reviewStatus);
}

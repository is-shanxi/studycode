package com.certpractice.aigateway;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.certpractice.shared.error.ApiException;
import com.certpractice.shared.error.ErrorCode;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional batch leasing. Claiming commits before the external model call,
 * which keeps provider latency out of database transactions.
 */
@Service
public class AiGenerationLeaseService {

    private static final int MAX_ERROR_LENGTH = 500;

    private final EntityManager entityManager;
    private final AiGenerationBatchRepository batches;
    private final AiGenerationProperties properties;
    private final Clock clock;

    public AiGenerationLeaseService(
            final EntityManager entityManager,
            final AiGenerationBatchRepository batches,
            final AiGenerationProperties properties,
            final Clock clock) {
        this.entityManager = entityManager;
        this.batches = batches;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ClaimedBatch claimNext() {
        @SuppressWarnings("unchecked")
        List<Object> rows = entityManager.createNativeQuery("""
                SELECT id FROM ai_generation_batch
                WHERE status = 'CREATED'
                  AND (leased_until IS NULL OR leased_until < now())
                ORDER BY created_at
                FOR UPDATE SKIP LOCKED
                LIMIT 1
                """).getResultList();
        if (rows.isEmpty()) {
            return null;
        }
        Object raw = rows.get(0);
        UUID batchId = raw instanceof UUID uuid ? uuid : UUID.fromString(raw.toString());
        AiGenerationBatch batch = batches.findById(batchId)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                        "Leased generation batch disappeared"));
        batch.setStatus(AiGenerationBatch.Status.RUNNING);
        batch.setAttemptCount(batch.getAttemptCount() + 1);
        batch.setLeasedUntil(clock.instant().plus(properties.getLeaseDuration()));
        batches.save(batch);
        return new ClaimedBatch(batch.getId(), batch.getAttemptCount());
    }

    @Transactional
    public void markFailure(final UUID batchId, final String error) {
        AiGenerationBatch batch = batches.findById(batchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Generation batch not found"));
        batch.setLastError(sanitize(error));
        batch.setLeasedUntil(null);
        if (batch.getAttemptCount() >= properties.getMaxAttempts()) {
            batch.setStatus(AiGenerationBatch.Status.FAILED);
            batch.setCompletedAt(clock.instant());
        } else {
            batch.setStatus(AiGenerationBatch.Status.CREATED);
        }
        batches.save(batch);
    }

    @Transactional
    public void markForReview(final UUID batchId) {
        AiGenerationBatch batch = batches.findById(batchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Generation batch not found"));
        batch.setStatus(AiGenerationBatch.Status.PENDING_REVIEW);
        batch.setLeasedUntil(null);
        batch.setLastError(null);
        batches.save(batch);
    }

    private String sanitize(final String error) {
        String safe = error == null || error.isBlank() ? "AI generation failed" : error.trim();
        return safe.length() <= MAX_ERROR_LENGTH ? safe : safe.substring(0, MAX_ERROR_LENGTH);
    }

    public record ClaimedBatch(UUID batchId, int attemptCount) {
    }
}

package com.certpractice.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByUserIdAndOperationAndIdempotencyKey(
            UUID userId,
            String operation,
            String idempotencyKey);
}

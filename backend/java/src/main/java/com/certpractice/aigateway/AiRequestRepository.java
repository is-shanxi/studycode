package com.certpractice.aigateway;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRequestRepository extends JpaRepository<AiRequest, UUID> {

    Optional<AiRequest> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}

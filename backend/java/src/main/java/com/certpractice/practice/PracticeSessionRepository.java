package com.certpractice.practice;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, UUID> {

    Page<PracticeSession> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<PracticeSession> findByIdAndUserId(UUID id, UUID userId);

    Optional<PracticeSession> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}

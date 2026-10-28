package com.certpractice.practice;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, UUID> {

    Page<PracticeSession> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<PracticeSession> findByIdAndUserId(UUID id, UUID userId);

    Optional<PracticeSession> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PracticeSession s where s.id = :id and s.userId = :userId")
    Optional<PracticeSession> findForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            select s from PracticeSession s
            where s.userId = :userId
              and (:mode is null or s.mode = :mode)
              and (:status is null or s.status = :status)
            order by s.createdAt desc
            """)
    Page<PracticeSession> search(
            @Param("userId") UUID userId,
            @Param("mode") PracticeSession.Mode mode,
            @Param("status") PracticeSession.Status status,
            Pageable pageable);
}

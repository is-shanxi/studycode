package com.certpractice.practice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionItemRepository extends JpaRepository<SessionItem, UUID> {

    List<SessionItem> findBySessionIdOrderByOrdinalAsc(UUID sessionId);

    Optional<SessionItem> findByIdAndSessionId(UUID id, UUID sessionId);

    boolean existsByQuestionVersionId(UUID questionVersionId);

    @Query("""
            select count(item) > 0
            from SessionItem item
            where item.questionId = :questionId
              and item.sessionId in (
                  select session.id
                  from PracticeSession session
                  where session.userId = :userId
                    and session.status = :status
              )
            """)
    boolean existsByOwnerAndQuestionAndSessionStatus(
            @Param("userId") UUID userId,
            @Param("questionId") UUID questionId,
            @Param("status") PracticeSession.Status status);
}

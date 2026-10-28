package com.certpractice.practice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WrongQuestionRepository extends JpaRepository<WrongQuestion, UUID> {

    List<WrongQuestion> findByUserIdAndActiveTrueOrderByLastFailedAtDesc(UUID userId);

    @Query("""
            select w from WrongQuestion w
            where w.userId = :userId and w.active = true and w.lastFailedAt >= :since
            order by w.lastFailedAt desc
            """)
    List<WrongQuestion> findActiveSince(@Param("userId") UUID userId, @Param("since") Instant since);

    Optional<WrongQuestion> findByUserIdAndQuestionIdAndActiveTrue(UUID userId, UUID questionId);

    Optional<WrongQuestion> findByUserIdAndQuestionId(UUID userId, UUID questionId);
}

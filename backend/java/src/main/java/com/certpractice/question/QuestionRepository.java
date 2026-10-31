package com.certpractice.question;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    Page<Question> findBySubjectId(UUID subjectId, Pageable pageable);

    Optional<Question> findByIdAndStatus(UUID id, Question.Status status);

    /**
     * Row lock used by publication and offline transitions so two administrators
     * (or an AI batch job and an administrator) cannot switch the published
     * pointer concurrently.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Question q where q.id = :id")
    Optional<Question> findByIdForUpdate(@Param("id") UUID id);
}

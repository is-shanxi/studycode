package com.certpractice.question;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    Page<Question> findBySubjectId(UUID subjectId, Pageable pageable);

    Optional<Question> findByIdAndStatus(UUID id, Question.Status status);
}

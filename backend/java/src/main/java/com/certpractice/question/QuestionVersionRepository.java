package com.certpractice.question;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionVersionRepository extends JpaRepository<QuestionVersion, UUID> {

    Optional<QuestionVersion> findByQuestionIdAndVersionNo(UUID questionId, int versionNo);

    List<QuestionVersion> findByQuestionIdOrderByVersionNoDesc(UUID questionId);

    Optional<QuestionVersion> findByQuestionIdAndStatusAndSupersededAtIsNull(
            UUID questionId,
            Question.Status status);

    Optional<QuestionVersion> findFirstByQuestionIdOrderByVersionNoDesc(UUID questionId);

    Optional<QuestionVersion> findByIdAndQuestionId(UUID id, UUID questionId);

    long countByStemIgnoreCase(String stem);
}

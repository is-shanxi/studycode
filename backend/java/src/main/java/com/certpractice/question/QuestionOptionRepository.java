package com.certpractice.question;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {

    List<QuestionOption> findByQuestionVersionIdOrderBySortOrderAsc(UUID questionVersionId);

    List<QuestionOption> findByQuestionVersionIdAndCorrectTrue(UUID questionVersionId);

    Optional<QuestionOption> findByIdAndQuestionVersionId(UUID id, UUID questionVersionId);

    void deleteByQuestionVersionId(UUID questionVersionId);
}

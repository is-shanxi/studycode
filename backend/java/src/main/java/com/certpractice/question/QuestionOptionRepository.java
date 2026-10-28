package com.certpractice.question;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {

    List<QuestionOption> findByQuestionVersionIdOrderBySortOrderAsc(UUID questionVersionId);
}

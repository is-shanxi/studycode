package com.certpractice.question;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionAnswerAliasRepository extends JpaRepository<QuestionAnswerAlias, UUID> {

    List<QuestionAnswerAlias> findByQuestionVersionId(UUID questionVersionId);
}

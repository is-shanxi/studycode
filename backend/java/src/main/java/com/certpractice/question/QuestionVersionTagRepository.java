package com.certpractice.question;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionVersionTagRepository extends JpaRepository<QuestionVersionTag, QuestionVersionTagId> {

    List<QuestionVersionTag> findByIdQuestionVersionId(UUID questionVersionId);

    List<QuestionVersionTag> findByIdTagId(UUID tagId);
}

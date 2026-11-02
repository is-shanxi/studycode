package com.certpractice.question;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionVersionTagRepository extends JpaRepository<QuestionVersionTag, QuestionVersionTagId> {

    List<QuestionVersionTag> findByIdQuestionVersionId(UUID questionVersionId);

    List<QuestionVersionTag> findByIdQuestionVersionIdIn(Collection<UUID> questionVersionIds);

    List<QuestionVersionTag> findByIdTagId(UUID tagId);

    void deleteByIdQuestionVersionId(UUID questionVersionId);
}

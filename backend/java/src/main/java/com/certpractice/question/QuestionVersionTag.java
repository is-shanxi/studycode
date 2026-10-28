package com.certpractice.question;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "question_version_tag")
public class QuestionVersionTag {

    @EmbeddedId
    private QuestionVersionTagId id;
}

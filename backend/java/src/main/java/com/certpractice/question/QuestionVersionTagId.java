package com.certpractice.question;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode
@Embeddable
public class QuestionVersionTagId implements Serializable {

    @Column(name = "question_version_id", nullable = false)
    private UUID questionVersionId;

    @Column(name = "tag_id", nullable = false)
    private UUID tagId;
}

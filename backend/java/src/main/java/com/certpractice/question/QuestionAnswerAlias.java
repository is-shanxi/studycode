package com.certpractice.question;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "question_answer_alias")
public class QuestionAnswerAlias {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "question_version_id", nullable = false)
    private UUID questionVersionId;

    @Column(name = "value", nullable = false, length = 255)
    private String value;

    @Column(name = "normalized_value", nullable = false, length = 255)
    private String normalizedValue;
}

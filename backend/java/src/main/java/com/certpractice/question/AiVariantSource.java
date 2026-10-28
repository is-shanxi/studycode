package com.certpractice.question;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_variant_source")
public class AiVariantSource {

    @Id
    @Column(name = "question_version_id", nullable = false, updatable = false)
    private UUID questionVersionId;

    @Column(name = "source_question_version_id", nullable = false)
    private UUID sourceQuestionVersionId;

    @Column(name = "generation_candidate_id", nullable = false)
    private UUID generationCandidateId;

    @Column(name = "model_name", nullable = false, length = 64)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, length = 32)
    private String promptVersion;
}

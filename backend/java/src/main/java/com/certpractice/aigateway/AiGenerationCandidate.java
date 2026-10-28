package com.certpractice.aigateway;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "ai_generation_candidate")
public class AiGenerationCandidate {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "source_question_version_id", nullable = false)
    private UUID sourceQuestionVersionId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "candidate_json", nullable = false, columnDefinition = "jsonb")
    private String candidateJson;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 16)
    private ValidationStatus validationStatus = ValidationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 16)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(name = "converted_question_version_id")
    private UUID convertedQuestionVersionId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum ValidationStatus {
        PENDING,
        VALID,
        INVALID
    }

    public enum ReviewStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}

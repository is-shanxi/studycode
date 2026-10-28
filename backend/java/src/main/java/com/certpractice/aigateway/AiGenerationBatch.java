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
@Table(name = "ai_generation_batch")
public class AiGenerationBatch {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "chapter_id")
    private UUID chapterId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_question_ids", nullable = false, columnDefinition = "jsonb")
    private String sourceQuestionIds = "[]";

    @Column(name = "target_count", nullable = false)
    private int targetCount;

    @Column(name = "model_name", nullable = false, length = 64)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, length = 32)
    private String promptVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private Status status = Status.CREATED;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "leased_until")
    private Instant leasedUntil;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public enum Status {
        CREATED,
        RUNNING,
        VALIDATING,
        PENDING_REVIEW,
        COMPLETED,
        FAILED
    }
}

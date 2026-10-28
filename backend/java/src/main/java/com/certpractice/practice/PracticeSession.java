package com.certpractice.practice;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "practice_session")
public class PracticeSession {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 16)
    private Mode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private Status status = Status.IN_PROGRESS;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_snapshot", nullable = false, columnDefinition = "jsonb")
    private String filterSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_snapshot", nullable = false, columnDefinition = "jsonb")
    private String ruleSnapshot;

    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    @Column(name = "actual_count", nullable = false)
    private int actualCount;

    @Column(name = "original_count", nullable = false)
    private int originalCount;

    @Column(name = "ai_variant_count", nullable = false)
    private int aiVariantCount;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Mode {
        NORMAL,
        SELF_TEST
    }

    public enum Status {
        IN_PROGRESS,
        SUBMITTED,
        PENDING_SELF_ASSESSMENT,
        COMPLETED,
        ABANDONED
    }
}

package com.certpractice.question;

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
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "correction_report")
public class CorrectionReport {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reporter_user_id", nullable = false)
    private UUID reporterUserId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "question_version_id", nullable = false)
    private UUID questionVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false, length = 24)
    private Target target;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private Status status = Status.OPEN;

    @Column(name = "handled_by")
    private UUID handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum Target {
        STEM,
        ANSWER,
        EXPLANATION,
        TAG,
        OTHER
    }

    public enum Status {
        OPEN,
        PROCESSING,
        RESOLVED,
        REJECTED
    }
}

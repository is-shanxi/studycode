package com.certpractice.practice;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "wrong_question")
public class WrongQuestion {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "first_failed_at", nullable = false, updatable = false)
    private Instant firstFailedAt;

    @UpdateTimestamp
    @Column(name = "last_failed_at", nullable = false)
    private Instant lastFailedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;
}

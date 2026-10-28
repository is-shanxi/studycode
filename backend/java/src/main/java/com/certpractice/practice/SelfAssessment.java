package com.certpractice.practice;

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
@Table(name = "self_assessment")
public class SelfAssessment {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "session_item_id", nullable = false)
    private UUID sessionItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 8)
    private Result result;

    @Column(name = "assessed_by", nullable = false)
    private UUID assessedBy;

    @CreationTimestamp
    @Column(name = "assessed_at", nullable = false, updatable = false)
    private Instant assessedAt;

    public enum Result {
        PASS,
        FAIL
    }
}

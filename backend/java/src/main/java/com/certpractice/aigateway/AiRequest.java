package com.certpractice.aigateway;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "ai_request")
public class AiRequest {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "quota_date", nullable = false)
    private LocalDate quotaDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.RESERVED;

    @Column(name = "reserved_cost", nullable = false, precision = 14, scale = 6)
    private BigDecimal reservedCost = BigDecimal.ZERO;

    @Column(name = "actual_cost", nullable = false, precision = 14, scale = 6)
    private BigDecimal actualCost = BigDecimal.ZERO;

    @Column(name = "external_request_id", length = 128)
    private String externalRequestId;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public enum Status {
        RESERVED,
        SUCCEEDED,
        FAILED,
        REFUNDED
    }
}

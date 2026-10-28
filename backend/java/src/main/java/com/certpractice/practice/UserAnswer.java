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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "user_answer")
public class UserAnswer {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "session_item_id", nullable = false)
    private UUID sessionItemId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json", nullable = false, columnDefinition = "jsonb")
    private String responseJson;

    @Column(name = "answer_revision", nullable = false)
    private int answerRevision = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_match_result", nullable = false, length = 16)
    private AutoMatchResult autoMatchResult = AutoMatchResult.NOT_APPLICABLE;

    @Column(name = "normalized_answer", columnDefinition = "text")
    private String normalizedAnswer;

    @CreationTimestamp
    @Column(name = "answered_at", nullable = false, updatable = false)
    private Instant answeredAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum AutoMatchResult {
        NOT_APPLICABLE,
        MATCH,
        MISMATCH
    }
}

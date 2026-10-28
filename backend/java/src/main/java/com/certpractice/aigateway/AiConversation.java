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
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "ai_conversation")
public class AiConversation {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 16)
    private Scope scope;

    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "question_version_id")
    private UUID questionVersionId;

    @Column(name = "session_item_id")
    private UUID sessionItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreationTimestamp
    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    public enum Scope {
        GENERAL,
        QUESTION
    }

    public enum Status {
        ACTIVE,
        ARCHIVED,
        CLEARED
    }
}

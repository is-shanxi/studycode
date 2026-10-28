package com.certpractice.question;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "question_version")
public class QuestionVersion {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private Question.Status status = Question.Status.DRAFT;

    @Column(name = "stem", nullable = false, columnDefinition = "text")
    private String stem;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    @Column(name = "fill_reference_answer", length = 255)
    private String fillReferenceAnswer;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 16)
    private Difficulty difficulty;

    @Column(name = "source_text", columnDefinition = "text")
    private String sourceText;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "offline_at")
    private Instant offlineAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD
    }
}

package com.certpractice.practice;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "session_item")
public class SessionItem {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "question_version_id", nullable = false)
    private UUID questionVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private SourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_bucket", nullable = false, length = 16)
    private SelectionBucket selectionBucket;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_result", length = 8)
    private Result finalResult;

    public enum SourceType {
        ORIGINAL,
        AI_VARIANT
    }

    public enum SelectionBucket {
        UNSEEN,
        WRONG,
        NORMAL
    }

    public enum Result {
        PASS,
        FAIL
    }
}

-- Practice sessions, answers, self-assessment, favorites and wrong questions.
CREATE TABLE practice_session (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    mode varchar(16) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'IN_PROGRESS',
    filter_snapshot jsonb NOT NULL,
    rule_snapshot jsonb NOT NULL,
    requested_count integer NOT NULL,
    actual_count integer NOT NULL,
    original_count integer NOT NULL DEFAULT 0,
    ai_variant_count integer NOT NULL DEFAULT 0,
    started_at timestamptz NOT NULL DEFAULT now(),
    submitted_at timestamptz,
    completed_at timestamptz,
    idempotency_key varchar(128),
    row_version integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_practice_session_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_practice_session_mode CHECK (mode IN ('NORMAL', 'SELF_TEST')),
    CONSTRAINT ck_practice_session_status CHECK (
        status IN ('IN_PROGRESS', 'SUBMITTED', 'PENDING_SELF_ASSESSMENT', 'COMPLETED', 'ABANDONED')
    ),
    CONSTRAINT ck_practice_session_counts CHECK (
        requested_count > 0 AND actual_count >= 0
        AND original_count >= 0 AND ai_variant_count >= 0
    )
);

CREATE INDEX ix_practice_session_user_created ON practice_session (user_id, created_at DESC);
CREATE INDEX ix_practice_session_user_status
    ON practice_session (user_id, status, updated_at DESC);
CREATE UNIQUE INDEX uq_practice_session_idempotency
    ON practice_session (user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE TABLE session_item (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id uuid NOT NULL,
    ordinal integer NOT NULL,
    question_id uuid NOT NULL,
    question_version_id uuid NOT NULL,
    source_type varchar(16) NOT NULL,
    selection_bucket varchar(16) NOT NULL,
    final_result varchar(8),
    CONSTRAINT fk_session_item_session FOREIGN KEY (session_id) REFERENCES practice_session (id),
    CONSTRAINT fk_session_item_question FOREIGN KEY (question_id) REFERENCES question (id),
    CONSTRAINT fk_session_item_version FOREIGN KEY (question_version_id) REFERENCES question_version (id),
    CONSTRAINT uq_session_item_ordinal UNIQUE (session_id, ordinal),
    CONSTRAINT uq_session_item_question UNIQUE (session_id, question_id),
    CONSTRAINT ck_session_item_source CHECK (source_type IN ('ORIGINAL', 'AI_VARIANT')),
    CONSTRAINT ck_session_item_bucket CHECK (selection_bucket IN ('UNSEEN', 'WRONG', 'NORMAL')),
    CONSTRAINT ck_session_item_result CHECK (final_result IS NULL OR final_result IN ('PASS', 'FAIL'))
);

CREATE INDEX ix_session_item_session ON session_item (session_id, ordinal);

CREATE TABLE user_answer (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_item_id uuid NOT NULL,
    response_json jsonb NOT NULL,
    answer_revision integer NOT NULL DEFAULT 1,
    auto_match_result varchar(16) NOT NULL DEFAULT 'NOT_APPLICABLE',
    normalized_answer text,
    answered_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_user_answer_item FOREIGN KEY (session_item_id) REFERENCES session_item (id),
    CONSTRAINT uq_user_answer_item UNIQUE (session_item_id),
    CONSTRAINT ck_user_answer_auto_match CHECK (
        auto_match_result IN ('NOT_APPLICABLE', 'MATCH', 'MISMATCH')
    )
);

CREATE TABLE self_assessment (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    session_item_id uuid NOT NULL,
    result varchar(8) NOT NULL,
    assessed_by uuid NOT NULL,
    assessed_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_self_assessment_item FOREIGN KEY (session_item_id) REFERENCES session_item (id),
    CONSTRAINT fk_self_assessment_user FOREIGN KEY (assessed_by) REFERENCES app_user (id),
    CONSTRAINT uq_self_assessment_item UNIQUE (session_item_id),
    CONSTRAINT ck_self_assessment_result CHECK (result IN ('PASS', 'FAIL'))
);

CREATE TABLE favorite (
    user_id uuid NOT NULL,
    question_id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_favorite PRIMARY KEY (user_id, question_id),
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_favorite_question FOREIGN KEY (question_id) REFERENCES question (id)
);

CREATE INDEX ix_favorite_user_created ON favorite (user_id, created_at DESC);

CREATE TABLE wrong_question (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    question_id uuid NOT NULL,
    active boolean NOT NULL DEFAULT true,
    first_failed_at timestamptz NOT NULL DEFAULT now(),
    last_failed_at timestamptz NOT NULL DEFAULT now(),
    removed_at timestamptz,
    row_version integer NOT NULL DEFAULT 0,
    CONSTRAINT fk_wrong_question_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_wrong_question_question FOREIGN KEY (question_id) REFERENCES question (id)
);

CREATE UNIQUE INDEX uq_wrong_question_active
    ON wrong_question (user_id, question_id)
    WHERE active = true;
CREATE INDEX ix_wrong_question_user_active
    ON wrong_question (user_id, active, last_failed_at DESC);

CREATE TABLE wrong_question_event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    wrong_question_id uuid NOT NULL,
    session_item_id uuid,
    event_type varchar(16) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_wrong_question_event_wrong_question FOREIGN KEY (wrong_question_id)
        REFERENCES wrong_question (id),
    CONSTRAINT fk_wrong_question_event_item FOREIGN KEY (session_item_id) REFERENCES session_item (id),
    CONSTRAINT ck_wrong_question_event_type CHECK (event_type IN ('ADDED', 'REMOVED', 'REACTIVATED'))
);

CREATE INDEX ix_wrong_question_event_wrong_question
    ON wrong_question_event (wrong_question_id, created_at);

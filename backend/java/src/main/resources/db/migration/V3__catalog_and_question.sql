-- Catalog tree, tags, stable question identity and immutable versions.
CREATE TABLE certificate (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_certificate_code UNIQUE (code),
    CONSTRAINT ck_certificate_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE subject (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    certificate_id uuid NOT NULL,
    code varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_subject_certificate FOREIGN KEY (certificate_id) REFERENCES certificate (id),
    CONSTRAINT uq_subject_code UNIQUE (certificate_id, code),
    CONSTRAINT ck_subject_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE chapter (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id uuid NOT NULL,
    code varchar(64) NOT NULL,
    name varchar(128) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_chapter_subject FOREIGN KEY (subject_id) REFERENCES subject (id),
    CONSTRAINT uq_chapter_code UNIQUE (subject_id, code),
    CONSTRAINT ck_chapter_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_chapter_name_trgm ON chapter USING gin (name gin_trgm_ops);

CREATE TABLE tag (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(64) NOT NULL,
    normalized_name varchar(64) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_tag_normalized_name UNIQUE (normalized_name),
    CONSTRAINT ck_tag_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_tag_normalized_name_trgm ON tag USING gin (normalized_name gin_trgm_ops);

CREATE TABLE question (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id uuid NOT NULL,
    type varchar(24) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    published_version_id uuid,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_question_subject FOREIGN KEY (subject_id) REFERENCES subject (id),
    CONSTRAINT fk_question_created_by FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT ck_question_type CHECK (type IN ('SINGLE_CHOICE', 'SINGLE_BLANK')),
    CONSTRAINT ck_question_status CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'OFFLINE'))
);

CREATE INDEX ix_question_subject_status_type ON question (subject_id, status, type);

CREATE TABLE question_version (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id uuid NOT NULL,
    version_no integer NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    stem text NOT NULL,
    explanation text,
    fill_reference_answer varchar(255),
    difficulty varchar(16) NOT NULL,
    source_text text,
    author_id uuid NOT NULL,
    reviewed_by uuid,
    reviewed_at timestamptz,
    published_at timestamptz,
    offline_at timestamptz,
    superseded_at timestamptz,
    content_hash varchar(64) NOT NULL,
    row_version integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_question_version_question FOREIGN KEY (question_id) REFERENCES question (id),
    CONSTRAINT fk_question_version_author FOREIGN KEY (author_id) REFERENCES app_user (id),
    CONSTRAINT fk_question_version_reviewer FOREIGN KEY (reviewed_by) REFERENCES app_user (id),
    CONSTRAINT uq_question_version_no UNIQUE (question_id, version_no),
    CONSTRAINT ck_question_version_status CHECK (
        status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'OFFLINE')
    ),
    CONSTRAINT ck_question_version_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD'))
);

CREATE UNIQUE INDEX uq_question_version_current_published
    ON question_version (question_id)
    WHERE status = 'PUBLISHED' AND superseded_at IS NULL;

CREATE INDEX ix_question_version_question ON question_version (question_id, version_no DESC);
CREATE INDEX ix_question_version_stem_trgm ON question_version USING gin (stem gin_trgm_ops);

ALTER TABLE question
    ADD CONSTRAINT fk_question_published_version
    FOREIGN KEY (published_version_id) REFERENCES question_version (id);

CREATE INDEX ix_question_published_version ON question (published_version_id);

CREATE TABLE question_option (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_version_id uuid NOT NULL,
    option_key varchar(8) NOT NULL,
    content text NOT NULL,
    is_correct boolean NOT NULL DEFAULT false,
    sort_order integer NOT NULL DEFAULT 0,
    CONSTRAINT fk_question_option_version FOREIGN KEY (question_version_id)
        REFERENCES question_version (id) ON DELETE CASCADE,
    CONSTRAINT uq_question_option_key UNIQUE (question_version_id, option_key)
);

CREATE UNIQUE INDEX uq_question_option_correct
    ON question_option (question_version_id)
    WHERE is_correct = true;

CREATE TABLE question_answer_alias (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_version_id uuid NOT NULL,
    value varchar(255) NOT NULL,
    normalized_value varchar(255) NOT NULL,
    CONSTRAINT fk_question_answer_alias_version FOREIGN KEY (question_version_id)
        REFERENCES question_version (id) ON DELETE CASCADE,
    CONSTRAINT uq_question_answer_alias UNIQUE (question_version_id, normalized_value)
);

CREATE TABLE question_version_tag (
    question_version_id uuid NOT NULL,
    tag_id uuid NOT NULL,
    CONSTRAINT pk_question_version_tag PRIMARY KEY (question_version_id, tag_id),
    CONSTRAINT fk_question_version_tag_version FOREIGN KEY (question_version_id)
        REFERENCES question_version (id) ON DELETE CASCADE,
    CONSTRAINT fk_question_version_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id)
);

CREATE INDEX ix_question_version_tag_tag ON question_version_tag (tag_id, question_version_id);

CREATE TABLE ai_variant_source (
    question_version_id uuid PRIMARY KEY,
    source_question_version_id uuid NOT NULL,
    generation_candidate_id uuid NOT NULL,
    model_name varchar(64) NOT NULL,
    prompt_version varchar(32) NOT NULL,
    CONSTRAINT fk_ai_variant_source_version FOREIGN KEY (question_version_id)
        REFERENCES question_version (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_variant_source_source_version FOREIGN KEY (source_question_version_id)
        REFERENCES question_version (id),
    CONSTRAINT ck_ai_variant_source_not_self CHECK (question_version_id <> source_question_version_id)
);

CREATE TABLE import_batch (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_user_id uuid NOT NULL,
    file_name varchar(255) NOT NULL,
    file_hash varchar(64) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'UPLOADED',
    total_rows integer NOT NULL DEFAULT 0,
    valid_rows integer NOT NULL DEFAULT 0,
    invalid_rows integer NOT NULL DEFAULT 0,
    preview_expires_at timestamptz,
    confirmed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_import_batch_admin FOREIGN KEY (admin_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_import_batch_status CHECK (
        status IN ('UPLOADED', 'VALIDATING', 'PREVIEW_READY', 'CONFIRMED', 'FAILED')
    )
);

CREATE INDEX ix_import_batch_admin_created ON import_batch (admin_user_id, created_at DESC);
CREATE INDEX ix_import_batch_file_hash ON import_batch (file_hash);

CREATE TABLE import_row_error (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id uuid NOT NULL,
    row_number integer NOT NULL,
    field_name varchar(64),
    error_code varchar(64) NOT NULL,
    message text NOT NULL,
    CONSTRAINT fk_import_row_error_batch FOREIGN KEY (batch_id)
        REFERENCES import_batch (id) ON DELETE CASCADE
);

CREATE INDEX ix_import_row_error_batch ON import_row_error (batch_id, row_number);

CREATE TABLE review_event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_version_id uuid NOT NULL,
    actor_user_id uuid NOT NULL,
    from_status varchar(24) NOT NULL,
    to_status varchar(24) NOT NULL,
    comment text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_review_event_version FOREIGN KEY (question_version_id)
        REFERENCES question_version (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_event_actor FOREIGN KEY (actor_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_review_event_from_status CHECK (
        from_status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'OFFLINE')
    ),
    CONSTRAINT ck_review_event_to_status CHECK (
        to_status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'OFFLINE')
    )
);

CREATE INDEX ix_review_event_version ON review_event (question_version_id, created_at);

CREATE TABLE correction_report (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_user_id uuid NOT NULL,
    question_id uuid NOT NULL,
    question_version_id uuid NOT NULL,
    target varchar(24) NOT NULL,
    description text NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'OPEN',
    handled_by uuid,
    handled_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_correction_report_reporter FOREIGN KEY (reporter_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_correction_report_question FOREIGN KEY (question_id) REFERENCES question (id),
    CONSTRAINT fk_correction_report_version FOREIGN KEY (question_version_id) REFERENCES question_version (id),
    CONSTRAINT fk_correction_report_handler FOREIGN KEY (handled_by) REFERENCES app_user (id),
    CONSTRAINT ck_correction_report_target CHECK (
        target IN ('STEM', 'ANSWER', 'EXPLANATION', 'TAG', 'OTHER')
    ),
    CONSTRAINT ck_correction_report_status CHECK (
        status IN ('OPEN', 'PROCESSING', 'RESOLVED', 'REJECTED')
    )
);

CREATE INDEX ix_correction_report_status ON correction_report (status, created_at);

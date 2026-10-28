-- AI generation, conversations, usage accounting, budgets and configuration.
CREATE TABLE ai_generation_batch (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    requested_by uuid NOT NULL,
    subject_id uuid NOT NULL,
    chapter_id uuid,
    source_question_ids jsonb NOT NULL DEFAULT '[]'::jsonb,
    target_count integer NOT NULL,
    model_name varchar(64) NOT NULL,
    prompt_version varchar(32) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'CREATED',
    attempt_count integer NOT NULL DEFAULT 0,
    leased_until timestamptz,
    last_error text,
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    CONSTRAINT fk_ai_generation_batch_requester FOREIGN KEY (requested_by) REFERENCES app_user (id),
    CONSTRAINT fk_ai_generation_batch_subject FOREIGN KEY (subject_id) REFERENCES subject (id),
    CONSTRAINT fk_ai_generation_batch_chapter FOREIGN KEY (chapter_id) REFERENCES chapter (id),
    CONSTRAINT ck_ai_generation_batch_target CHECK (target_count > 0),
    CONSTRAINT ck_ai_generation_batch_status CHECK (
        status IN ('CREATED', 'RUNNING', 'VALIDATING', 'PENDING_REVIEW', 'COMPLETED', 'FAILED')
    )
);

CREATE INDEX ix_ai_generation_batch_status
    ON ai_generation_batch (status, leased_until);
CREATE INDEX ix_ai_generation_batch_created
    ON ai_generation_batch (created_at DESC);

CREATE TABLE ai_generation_candidate (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id uuid NOT NULL,
    source_question_version_id uuid NOT NULL,
    candidate_json jsonb NOT NULL,
    content_hash varchar(64) NOT NULL,
    validation_status varchar(16) NOT NULL DEFAULT 'PENDING',
    review_status varchar(16) NOT NULL DEFAULT 'PENDING',
    converted_question_version_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_ai_generation_candidate_batch FOREIGN KEY (batch_id)
        REFERENCES ai_generation_batch (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_generation_candidate_source FOREIGN KEY (source_question_version_id)
        REFERENCES question_version (id),
    CONSTRAINT fk_ai_generation_candidate_converted FOREIGN KEY (converted_question_version_id)
        REFERENCES question_version (id),
    CONSTRAINT uq_ai_generation_candidate_hash UNIQUE (content_hash),
    CONSTRAINT ck_ai_generation_candidate_validation CHECK (
        validation_status IN ('PENDING', 'VALID', 'INVALID')
    ),
    CONSTRAINT ck_ai_generation_candidate_review CHECK (
        review_status IN ('PENDING', 'APPROVED', 'REJECTED')
    )
);

CREATE INDEX ix_ai_generation_candidate_batch
    ON ai_generation_candidate (batch_id, created_at);

ALTER TABLE ai_variant_source
    ADD CONSTRAINT fk_ai_variant_source_candidate
    FOREIGN KEY (generation_candidate_id) REFERENCES ai_generation_candidate (id);

CREATE INDEX ix_ai_variant_source_candidate
    ON ai_variant_source (generation_candidate_id);

CREATE TABLE ai_conversation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    scope varchar(16) NOT NULL,
    question_id uuid,
    question_version_id uuid,
    session_item_id uuid,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    last_message_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_ai_conversation_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_ai_conversation_question FOREIGN KEY (question_id) REFERENCES question (id),
    CONSTRAINT fk_ai_conversation_version FOREIGN KEY (question_version_id) REFERENCES question_version (id),
    CONSTRAINT fk_ai_conversation_item FOREIGN KEY (session_item_id) REFERENCES session_item (id),
    CONSTRAINT ck_ai_conversation_scope CHECK (scope IN ('GENERAL', 'QUESTION')),
    CONSTRAINT ck_ai_conversation_status CHECK (status IN ('ACTIVE', 'ARCHIVED', 'CLEARED'))
);

CREATE INDEX ix_ai_conversation_user
    ON ai_conversation (user_id, last_message_at DESC);

CREATE TABLE ai_message (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL,
    role varchar(16) NOT NULL,
    content text NOT NULL,
    provider varchar(64),
    model_name varchar(64),
    input_tokens integer NOT NULL DEFAULT 0,
    output_tokens integer NOT NULL DEFAULT 0,
    request_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_ai_message_conversation FOREIGN KEY (conversation_id)
        REFERENCES ai_conversation (id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_message_role CHECK (role IN ('SYSTEM', 'USER', 'ASSISTANT'))
);

CREATE INDEX ix_ai_message_conversation ON ai_message (conversation_id, created_at);

CREATE TABLE ai_request (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    conversation_id uuid,
    idempotency_key varchar(128) NOT NULL,
    quota_date date NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'RESERVED',
    reserved_cost numeric(14, 6) NOT NULL DEFAULT 0,
    actual_cost numeric(14, 6) NOT NULL DEFAULT 0,
    external_request_id varchar(128),
    error_code varchar(64),
    created_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    CONSTRAINT fk_ai_request_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_ai_request_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversation (id),
    CONSTRAINT uq_ai_request_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_ai_request_status CHECK (
        status IN ('RESERVED', 'SUCCEEDED', 'FAILED', 'REFUNDED')
    )
);

CREATE INDEX ix_ai_request_user_date ON ai_request (user_id, quota_date);

CREATE TABLE ai_usage_daily (
    user_id uuid NOT NULL,
    usage_date date NOT NULL,
    request_count integer NOT NULL DEFAULT 0,
    input_tokens integer NOT NULL DEFAULT 0,
    output_tokens integer NOT NULL DEFAULT 0,
    actual_cost numeric(14, 6) NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_usage_daily PRIMARY KEY (user_id, usage_date),
    CONSTRAINT fk_ai_usage_daily_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE TABLE ai_budget_daily (
    scope varchar(16) NOT NULL,
    usage_date date NOT NULL,
    cost_limit numeric(14, 6) NOT NULL,
    cost_used numeric(14, 6) NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_budget_daily PRIMARY KEY (scope, usage_date),
    CONSTRAINT ck_ai_budget_daily_scope CHECK (scope IN ('TEAM'))
);

CREATE TABLE system_config_version (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    config_key varchar(96) NOT NULL,
    config_value_json jsonb NOT NULL,
    version_no integer NOT NULL,
    effective_from timestamptz NOT NULL DEFAULT now(),
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_system_config_version_creator FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT uq_system_config_version UNIQUE (config_key, version_no)
);

CREATE INDEX ix_system_config_version_key
    ON system_config_version (config_key, version_no DESC);

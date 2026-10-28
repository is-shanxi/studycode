-- Identity, roles, Spring Session JDBC, audit and idempotency records.
CREATE TABLE app_user (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    username varchar(64) NOT NULL,
    username_normalized varchar(64) NOT NULL,
    password_hash varchar(255) NOT NULL,
    nickname varchar(64),
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    deleted_at timestamptz,
    row_version integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_app_user_username_normalized UNIQUE (username_normalized),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED_ANONYMIZED'))
);

CREATE INDEX ix_app_user_status ON app_user (status);

CREATE TABLE user_role (
    user_id uuid NOT NULL,
    role varchar(16) NOT NULL,
    CONSTRAINT pk_user_role PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_user_role_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE INDEX ix_user_role_role ON user_role (role);

-- Standard Spring Session JDBC schema. Flyway owns creation; Spring Session
-- initialization is disabled in application.yml to avoid duplicate DDL.
CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);
CREATE INDEX SPRING_SESSION_IX4 ON SPRING_SESSION (PRINCIPAL_NAME, LAST_ACCESS_TIME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BYTEA NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
);

CREATE TABLE admin_audit_log (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id uuid NOT NULL,
    action varchar(64) NOT NULL,
    target_type varchar(32) NOT NULL,
    target_id uuid,
    outcome varchar(16) NOT NULL,
    summary_json jsonb,
    trace_id varchar(64),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_admin_audit_log_actor FOREIGN KEY (actor_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_admin_audit_log_outcome CHECK (outcome IN ('SUCCESS', 'FAILED', 'REJECTED'))
);

CREATE INDEX ix_admin_audit_log_created_at ON admin_audit_log (created_at DESC);
CREATE INDEX ix_admin_audit_log_actor ON admin_audit_log (actor_user_id, created_at DESC);

CREATE TABLE idempotency_record (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL,
    operation varchar(64) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash varchar(128) NOT NULL,
    response_status integer NOT NULL,
    response_body jsonb NOT NULL,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_idempotency_record_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT uq_idempotency_record UNIQUE (user_id, operation, idempotency_key)
);

CREATE INDEX ix_idempotency_record_expires_at ON idempotency_record (expires_at);

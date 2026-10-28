-- Version-bound discussions, comments, reports and notifications.
CREATE TABLE discussion_post (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id uuid NOT NULL,
    question_version_id uuid NOT NULL,
    author_user_id uuid NOT NULL,
    title varchar(200) NOT NULL,
    body text NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'VISIBLE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_discussion_post_question FOREIGN KEY (question_id) REFERENCES question (id),
    CONSTRAINT fk_discussion_post_version FOREIGN KEY (question_version_id) REFERENCES question_version (id),
    CONSTRAINT fk_discussion_post_author FOREIGN KEY (author_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_discussion_post_status CHECK (status IN ('VISIBLE', 'HIDDEN', 'REMOVED'))
);

CREATE INDEX ix_discussion_post_version
    ON discussion_post (question_version_id, status, created_at DESC);
CREATE INDEX ix_discussion_post_question
    ON discussion_post (question_id, created_at DESC);

CREATE TABLE discussion_comment (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id uuid NOT NULL,
    parent_comment_id uuid,
    quoted_comment_id uuid,
    author_user_id uuid NOT NULL,
    body text NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'VISIBLE',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_discussion_comment_post FOREIGN KEY (post_id) REFERENCES discussion_post (id),
    CONSTRAINT fk_discussion_comment_parent FOREIGN KEY (parent_comment_id) REFERENCES discussion_comment (id),
    CONSTRAINT fk_discussion_comment_quoted FOREIGN KEY (quoted_comment_id) REFERENCES discussion_comment (id),
    CONSTRAINT fk_discussion_comment_author FOREIGN KEY (author_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_discussion_comment_status CHECK (status IN ('VISIBLE', 'HIDDEN', 'REMOVED')),
    CONSTRAINT ck_discussion_comment_parent CHECK (parent_comment_id IS NULL OR parent_comment_id <> id)
);

CREATE INDEX ix_discussion_comment_post
    ON discussion_comment (post_id, status, created_at);

CREATE TABLE content_report (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_user_id uuid NOT NULL,
    target_type varchar(16) NOT NULL,
    target_id uuid NOT NULL,
    reason text NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'OPEN',
    handled_by uuid,
    handled_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_content_report_reporter FOREIGN KEY (reporter_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_content_report_handler FOREIGN KEY (handled_by) REFERENCES app_user (id),
    CONSTRAINT ck_content_report_target CHECK (target_type IN ('QUESTION', 'POST', 'COMMENT')),
    CONSTRAINT ck_content_report_status CHECK (
        status IN ('OPEN', 'PROCESSING', 'RESOLVED', 'REJECTED')
    )
);

CREATE INDEX ix_content_report_status ON content_report (status, created_at);
CREATE INDEX ix_content_report_target ON content_report (target_type, target_id);

CREATE TABLE notification (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id uuid NOT NULL,
    type varchar(32) NOT NULL,
    title varchar(200) NOT NULL,
    body text NOT NULL,
    payload_json jsonb,
    read_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_notification_type CHECK (
        type IN ('COMMENT_REPLY', 'MODERATION_RESULT', 'ANNOUNCEMENT', 'SYSTEM')
    )
);

CREATE INDEX ix_notification_recipient
    ON notification (recipient_user_id, read_at, created_at DESC);

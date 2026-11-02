-- Query-plan tuning for public search, practice history, dashboard aggregates
-- and practice selection candidate queries. V7 and V8 are already used by
-- earlier migrations, so this index-only change is numbered V9.

-- Search and selection: published question filters plus recency ordering.
CREATE INDEX ix_question_status_subject_updated
    ON question (status, subject_id, updated_at DESC);
CREATE INDEX ix_question_version_status_difficulty
    ON question_version (status, difficulty);
CREATE INDEX ix_tag_name_trgm
    ON tag USING gin (name gin_trgm_ops);

-- Practice history filters by mode and orders by creation time.
CREATE INDEX ix_practice_session_user_mode_created
    ON practice_session (user_id, mode, created_at DESC);

-- Dashboard aggregates.
CREATE INDEX ix_practice_session_started_at ON practice_session (started_at);
CREATE INDEX ix_question_type ON question (type);
CREATE INDEX ix_session_item_final_result
    ON session_item (final_result)
    WHERE final_result IS NOT NULL;
CREATE INDEX ix_user_answer_auto_match
    ON user_answer (auto_match_result)
    WHERE auto_match_result <> 'NOT_APPLICABLE';
CREATE INDEX ix_self_assessment_result ON self_assessment (result);

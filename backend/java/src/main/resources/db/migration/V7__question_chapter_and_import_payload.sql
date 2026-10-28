-- P1 additions: questions belong to a chapter, and import previews keep the
-- validated row payload until the administrator confirms the batch.
ALTER TABLE question
    ADD COLUMN chapter_id uuid;

ALTER TABLE question
    ADD CONSTRAINT fk_question_chapter FOREIGN KEY (chapter_id) REFERENCES chapter (id);

CREATE INDEX ix_question_chapter_status ON question (chapter_id, status);

ALTER TABLE import_batch
    ADD COLUMN payload_json jsonb;

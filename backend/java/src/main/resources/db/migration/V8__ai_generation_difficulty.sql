-- Difficulty requested for each AI generation batch; null means model default.
ALTER TABLE ai_generation_batch
    ADD COLUMN difficulty varchar(16);

ALTER TABLE ai_generation_batch
    ADD CONSTRAINT ck_ai_generation_batch_difficulty CHECK (
        difficulty IS NULL OR difficulty IN ('EASY', 'MEDIUM', 'HARD')
    );

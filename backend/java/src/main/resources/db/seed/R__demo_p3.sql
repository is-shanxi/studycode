-- Deterministic P3 demo data: approved AI variants for self-test composition,
-- one completed generation batch and one batch waiting for administrator review.
-- Repeatable and idempotent; never used to satisfy production data requirements.

INSERT INTO ai_generation_batch (
    id, requested_by, subject_id, chapter_id, source_question_ids, target_count,
    difficulty, model_name, prompt_version, status, attempt_count, completed_at, created_at
)
VALUES
    (
        '30000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000001',
        '11000000-0000-0000-0000-000000000001',
        '12000000-0000-0000-0000-000000000001',
        '["20000000-0000-0000-0000-000000000001"]'::jsonb,
        4, 'MEDIUM', 'mock-model', 'v1', 'COMPLETED', 1,
        '2026-11-01T08:00:00Z', '2026-11-01T07:00:00Z'
    ),
    (
        '30000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000001',
        '11000000-0000-0000-0000-000000000001',
        '12000000-0000-0000-0000-000000000002',
        '["20000000-0000-0000-0000-000000000002"]'::jsonb,
        2, 'MEDIUM', 'mock-model', 'v1', 'PENDING_REVIEW', 1,
        NULL, now() - interval '1 hour'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO ai_generation_candidate (
    id, batch_id, source_question_version_id, candidate_json, content_hash,
    validation_status, review_status, converted_question_version_id, created_at
)
VALUES
    (
        '31000000-0000-0000-0000-000000000001',
        '30000000-0000-0000-0000-000000000001',
        '21000000-0000-0000-0000-000000000001',
        '{"stem":"AI 变式：OSI 模型中负责端到端可靠传输的是哪一层？","type":"SINGLE_CHOICE","options":[{"key":"A","content":"传输层","isCorrect":true},{"key":"B","content":"网络层","isCorrect":false},{"key":"C","content":"应用层","isCorrect":false},{"key":"D","content":"链路层","isCorrect":false}],"referenceAnswer":null,"explanation":"传输层通过重传、确认和流量控制提供端到端可靠性。","difficulty":"MEDIUM","suggestedTags":["高频","AI变式"]}'::jsonb,
        encode(digest('p3-variant-1', 'sha256'), 'hex'), 'VALID', 'APPROVED',
        NULL, '2026-11-01T07:10:00Z'
    ),
    (
        '31000000-0000-0000-0000-000000000002',
        '30000000-0000-0000-0000-000000000001',
        '21000000-0000-0000-0000-000000000001',
        '{"stem":"AI 变式：端到端可靠性主要由哪一层保证？","type":"SINGLE_CHOICE","options":[{"key":"A","content":"网络层","isCorrect":false},{"key":"B","content":"传输层","isCorrect":true},{"key":"C","content":"物理层","isCorrect":false},{"key":"D","content":"会话层","isCorrect":false}],"referenceAnswer":null,"explanation":"传输层负责端到端的可靠交付。","difficulty":"EASY","suggestedTags":["高频","AI变式"]}'::jsonb,
        encode(digest('p3-variant-2', 'sha256'), 'hex'), 'VALID', 'APPROVED',
        NULL, '2026-11-01T07:11:00Z'
    ),
    (
        '31000000-0000-0000-0000-000000000003',
        '30000000-0000-0000-0000-000000000001',
        '21000000-0000-0000-0000-000000000001',
        '{"stem":"AI 变式：TCP 与 UDP 的差异中，哪项描述正确？","type":"SINGLE_CHOICE","options":[{"key":"A","content":"TCP 无连接，UDP 面向连接","isCorrect":false},{"key":"B","content":"TCP 提供可靠传输，UDP 不保证可靠","isCorrect":true},{"key":"C","content":"两者都不提供校验","isCorrect":false},{"key":"D","content":"两者都属于网络层","isCorrect":false}],"referenceAnswer":null,"explanation":"TCP 提供确认、重传与有序交付，UDP 不保证可靠性。","difficulty":"MEDIUM","suggestedTags":["高频","AI变式"]}'::jsonb,
        encode(digest('p3-variant-3', 'sha256'), 'hex'), 'VALID', 'APPROVED',
        NULL, '2026-11-01T07:12:00Z'
    ),
    (
        '31000000-0000-0000-0000-000000000004',
        '30000000-0000-0000-0000-000000000001',
        '21000000-0000-0000-0000-000000000001',
        '{"stem":"AI 变式：下列关于可靠传输的说法，哪项最准确？","type":"SINGLE_CHOICE","options":[{"key":"A","content":"可靠传输只依赖物理介质","isCorrect":false},{"key":"B","content":"可靠传输需要差错检测与重传机制","isCorrect":true},{"key":"C","content":"可靠传输不需要确认","isCorrect":false},{"key":"D","content":"可靠传输只能在应用层实现","isCorrect":false}],"referenceAnswer":null,"explanation":"差错检测、确认和重传共同构成可靠传输。","difficulty":"HARD","suggestedTags":["易错","AI变式"]}'::jsonb,
        encode(digest('p3-variant-4', 'sha256'), 'hex'), 'VALID', 'APPROVED',
        NULL, '2026-11-01T07:13:00Z'
    ),
    (
        '31000000-0000-0000-0000-000000000011',
        '30000000-0000-0000-0000-000000000002',
        '21000000-0000-0000-0000-000000000002',
        '{"stem":"待审核变式：栈结构最适合描述下列哪种行为？","type":"SINGLE_CHOICE","options":[{"key":"A","content":"先进先出","isCorrect":false},{"key":"B","content":"后进先出","isCorrect":true},{"key":"C","content":"随机访问","isCorrect":false},{"key":"D","content":"按优先级出队","isCorrect":false}],"referenceAnswer":null,"explanation":"栈遵循后进先出原则。","difficulty":"EASY","suggestedTags":["基础","AI变式"]}'::jsonb,
        encode(digest('p3-pending-1', 'sha256'), 'hex'), 'VALID', 'PENDING',
        NULL, now() - interval '55 minutes'
    ),
    (
        '31000000-0000-0000-0000-000000000012',
        '30000000-0000-0000-0000-000000000002',
        '21000000-0000-0000-0000-000000000002',
        '{"stem":"待审核变式：队列的典型访问顺序是什么？","type":"SINGLE_CHOICE","options":[{"key":"A","content":"后进先出","isCorrect":false},{"key":"B","content":"先进先出","isCorrect":true},{"key":"C","content":"随机访问","isCorrect":false},{"key":"D","content":"按哈希顺序","isCorrect":false}],"referenceAnswer":null,"explanation":"队列遵循先进先出原则。","difficulty":"EASY","suggestedTags":["基础","AI变式"]}'::jsonb,
        encode(digest('p3-pending-2', 'sha256'), 'hex'), 'VALID', 'PENDING',
        NULL, now() - interval '54 minutes'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO question (id, subject_id, chapter_id, type, status, published_version_id, created_by)
VALUES
    ('20000000-0000-0000-0000-000000000013', '11000000-0000-0000-0000-000000000001',
     '12000000-0000-0000-0000-000000000001', 'SINGLE_CHOICE', 'PUBLISHED', NULL,
     '00000000-0000-0000-0000-000000000001'),
    ('20000000-0000-0000-0000-000000000014', '11000000-0000-0000-0000-000000000001',
     '12000000-0000-0000-0000-000000000001', 'SINGLE_CHOICE', 'PUBLISHED', NULL,
     '00000000-0000-0000-0000-000000000001'),
    ('20000000-0000-0000-0000-000000000015', '11000000-0000-0000-0000-000000000001',
     '12000000-0000-0000-0000-000000000001', 'SINGLE_CHOICE', 'PUBLISHED', NULL,
     '00000000-0000-0000-0000-000000000001'),
    ('20000000-0000-0000-0000-000000000016', '11000000-0000-0000-0000-000000000001',
     '12000000-0000-0000-0000-000000000001', 'SINGLE_CHOICE', 'PUBLISHED', NULL,
     '00000000-0000-0000-0000-000000000001')
ON CONFLICT (id) DO NOTHING;

INSERT INTO question_version (
    id, question_id, version_no, status, stem, explanation, fill_reference_answer,
    difficulty, source_text, author_id, reviewed_by, reviewed_at, published_at,
    content_hash, created_at, updated_at
)
SELECT
    CASE candidate.id
        WHEN '31000000-0000-0000-0000-000000000001' THEN '21000000-0000-0000-0000-000000000013'::uuid
        WHEN '31000000-0000-0000-0000-000000000002' THEN '21000000-0000-0000-0000-000000000014'::uuid
        WHEN '31000000-0000-0000-0000-000000000003' THEN '21000000-0000-0000-0000-000000000015'::uuid
        WHEN '31000000-0000-0000-0000-000000000004' THEN '21000000-0000-0000-0000-000000000016'::uuid
    END,
    question.id,
    1,
    'PUBLISHED',
    candidate.candidate_json ->> 'stem',
    candidate.candidate_json ->> 'explanation',
    NULL,
    candidate.candidate_json ->> 'difficulty',
    'AI variant generated from candidate ' || candidate.id,
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    '2026-11-01T08:00:00Z',
    '2026-11-01T08:00:00Z',
    encode(digest('p3-version-' || candidate.id, 'sha256'), 'hex'),
    '2026-11-01T08:00:00Z',
    '2026-11-01T08:00:00Z'
FROM ai_generation_candidate candidate
JOIN question ON question.id = CASE candidate.id
    WHEN '31000000-0000-0000-0000-000000000001' THEN '20000000-0000-0000-0000-000000000013'::uuid
    WHEN '31000000-0000-0000-0000-000000000002' THEN '20000000-0000-0000-0000-000000000014'::uuid
    WHEN '31000000-0000-0000-0000-000000000003' THEN '20000000-0000-0000-0000-000000000015'::uuid
    WHEN '31000000-0000-0000-0000-000000000004' THEN '20000000-0000-0000-0000-000000000016'::uuid
    ELSE NULL
END
WHERE candidate.id IN (
    '31000000-0000-0000-0000-000000000001',
    '31000000-0000-0000-0000-000000000002',
    '31000000-0000-0000-0000-000000000003',
    '31000000-0000-0000-0000-000000000004'
)
ON CONFLICT (id) DO NOTHING;


UPDATE ai_generation_candidate
SET converted_question_version_id = CASE id
    WHEN '31000000-0000-0000-0000-000000000001' THEN '21000000-0000-0000-0000-000000000013'::uuid
    WHEN '31000000-0000-0000-0000-000000000002' THEN '21000000-0000-0000-0000-000000000014'::uuid
    WHEN '31000000-0000-0000-0000-000000000003' THEN '21000000-0000-0000-0000-000000000015'::uuid
    WHEN '31000000-0000-0000-0000-000000000004' THEN '21000000-0000-0000-0000-000000000016'::uuid
    ELSE converted_question_version_id
END
WHERE id IN (
    '31000000-0000-0000-0000-000000000001',
    '31000000-0000-0000-0000-000000000002',
    '31000000-0000-0000-0000-000000000003',
    '31000000-0000-0000-0000-000000000004'
);

UPDATE question q
SET published_version_id = v.id,
    status = 'PUBLISHED',
    updated_at = '2026-11-01T08:00:00Z'
FROM question_version v
WHERE v.question_id = q.id
  AND v.id IN (
      '21000000-0000-0000-0000-000000000013',
      '21000000-0000-0000-0000-000000000014',
      '21000000-0000-0000-0000-000000000015',
      '21000000-0000-0000-0000-000000000016'
  );

INSERT INTO question_option (id, question_version_id, option_key, content, is_correct, sort_order)
VALUES
    ('23000000-0000-0000-0000-000000000001', '21000000-0000-0000-0000-000000000013', 'A', '传输层', true, 0),
    ('23000000-0000-0000-0000-000000000002', '21000000-0000-0000-0000-000000000013', 'B', '网络层', false, 1),
    ('23000000-0000-0000-0000-000000000003', '21000000-0000-0000-0000-000000000013', 'C', '应用层', false, 2),
    ('23000000-0000-0000-0000-000000000004', '21000000-0000-0000-0000-000000000013', 'D', '链路层', false, 3),
    ('23000000-0000-0000-0000-000000000005', '21000000-0000-0000-0000-000000000014', 'A', '网络层', false, 0),
    ('23000000-0000-0000-0000-000000000006', '21000000-0000-0000-0000-000000000014', 'B', '传输层', true, 1),
    ('23000000-0000-0000-0000-000000000007', '21000000-0000-0000-0000-000000000014', 'C', '物理层', false, 2),
    ('23000000-0000-0000-0000-000000000008', '21000000-0000-0000-0000-000000000014', 'D', '会话层', false, 3),
    ('23000000-0000-0000-0000-000000000009', '21000000-0000-0000-0000-000000000015', 'A', 'TCP 无连接，UDP 面向连接', false, 0),
    ('23000000-0000-0000-0000-000000000010', '21000000-0000-0000-0000-000000000015', 'B', 'TCP 提供可靠传输，UDP 不保证可靠', true, 1),
    ('23000000-0000-0000-0000-000000000011', '21000000-0000-0000-0000-000000000015', 'C', '两者都不提供校验', false, 2),
    ('23000000-0000-0000-0000-000000000012', '21000000-0000-0000-0000-000000000015', 'D', '两者都属于网络层', false, 3),
    ('23000000-0000-0000-0000-000000000013', '21000000-0000-0000-0000-000000000016', 'A', '可靠传输只依赖物理介质', false, 0),
    ('23000000-0000-0000-0000-000000000014', '21000000-0000-0000-0000-000000000016', 'B', '可靠传输需要差错检测与重传机制', true, 1),
    ('23000000-0000-0000-0000-000000000015', '21000000-0000-0000-0000-000000000016', 'C', '可靠传输不需要确认', false, 2),
    ('23000000-0000-0000-0000-000000000016', '21000000-0000-0000-0000-000000000016', 'D', '可靠传输只能在应用层实现', false, 3)
ON CONFLICT (id) DO NOTHING;

INSERT INTO question_version_tag (question_version_id, tag_id)
VALUES
    ('21000000-0000-0000-0000-000000000013', '13000000-0000-0000-0000-000000000001'),
    ('21000000-0000-0000-0000-000000000014', '13000000-0000-0000-0000-000000000001'),
    ('21000000-0000-0000-0000-000000000015', '13000000-0000-0000-0000-000000000001'),
    ('21000000-0000-0000-0000-000000000016', '13000000-0000-0000-0000-000000000002')
ON CONFLICT DO NOTHING;

INSERT INTO ai_variant_source (
    question_version_id, source_question_version_id, generation_candidate_id,
    model_name, prompt_version
)
VALUES
    ('21000000-0000-0000-0000-000000000013', '21000000-0000-0000-0000-000000000001',
     '31000000-0000-0000-0000-000000000001', 'mock-model', 'v1'),
    ('21000000-0000-0000-0000-000000000014', '21000000-0000-0000-0000-000000000001',
     '31000000-0000-0000-0000-000000000002', 'mock-model', 'v1'),
    ('21000000-0000-0000-0000-000000000015', '21000000-0000-0000-0000-000000000001',
     '31000000-0000-0000-0000-000000000003', 'mock-model', 'v1'),
    ('21000000-0000-0000-0000-000000000016', '21000000-0000-0000-0000-000000000001',
     '31000000-0000-0000-0000-000000000004', 'mock-model', 'v1')
ON CONFLICT (question_version_id) DO NOTHING;

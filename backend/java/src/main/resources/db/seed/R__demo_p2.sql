-- Deterministic P2 demo discussions, comments, notifications, reports and audit
-- records for the demo users. Repeatable and idempotent.
INSERT INTO discussion_post (
    id, question_id, question_version_id, author_user_id, title, body, status, created_at, updated_at
)
VALUES
    (
        '40000000-0000-0000-0000-000000000001',
        '20000000-0000-0000-0000-000000000001',
        '21000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000002',
        'TCP 可靠传输怎么判断？',
        '题干里如何快速判断是传输层负责可靠传输？',
        'VISIBLE',
        '2026-10-23T02:00:00Z',
        '2026-10-23T02:00:00Z'
    ),
    (
        '40000000-0000-0000-0000-000000000002',
        '20000000-0000-0000-0000-000000000009',
        '21000000-0000-0000-0000-000000000009',
        '00000000-0000-0000-0000-000000000001',
        '关于 SYN 的填写',
        '填空只写字母缩写即可，大小写不影响自动匹配。',
        'VISIBLE',
        '2026-10-23T02:10:00Z',
        '2026-10-23T02:10:00Z'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO discussion_comment (
    id, post_id, parent_comment_id, quoted_comment_id, author_user_id, body, status, created_at
)
VALUES
    (
        '41000000-0000-0000-0000-000000000001',
        '40000000-0000-0000-0000-000000000001',
        NULL,
        NULL,
        '00000000-0000-0000-0000-000000000001',
        '看题干关键词是可靠传输，对应 TCP。',
        'VISIBLE',
        '2026-10-23T02:05:00Z'
    ),
    (
        '41000000-0000-0000-0000-000000000002',
        '40000000-0000-0000-0000-000000000001',
        '41000000-0000-0000-0000-000000000001',
        NULL,
        '00000000-0000-0000-0000-000000000002',
        '明白了，谢谢。',
        'VISIBLE',
        '2026-10-23T02:06:00Z'
    ),
    (
        '41000000-0000-0000-0000-000000000003',
        '40000000-0000-0000-0000-000000000002',
        NULL,
        NULL,
        '00000000-0000-0000-0000-000000000002',
        '填 SYN 也可以吧？',
        'HIDDEN',
        '2026-10-23T02:15:00Z'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO notification (
    id, recipient_user_id, type, title, body, payload_json, read_at, created_at
)
VALUES
    (
        '42000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000002',
        'COMMENT_REPLY',
        '你的帖子有新回复',
        '演示管理员 回复了《TCP 可靠传输怎么判断？》',
        '{"postId":"40000000-0000-0000-0000-000000000001","commentId":"41000000-0000-0000-0000-000000000001"}'::jsonb,
        NULL,
        '2026-10-23T02:05:00Z'
    ),
    (
        '42000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000002',
        'ANNOUNCEMENT',
        '演示公告',
        '本周日 22:00 进行本地环境维护。',
        NULL,
        '2026-10-23T02:30:00Z',
        '2026-10-23T02:20:00Z'
    ),
    (
        '42000000-0000-0000-0000-000000000003',
        '00000000-0000-0000-0000-000000000001',
        'COMMENT_REPLY',
        '你的帖子有新回复',
        '演示用户 回复了《关于 SYN 的填写》',
        '{"postId":"40000000-0000-0000-0000-000000000002","commentId":"41000000-0000-0000-0000-000000000003"}'::jsonb,
        NULL,
        '2026-10-23T02:15:00Z'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO content_report (
    id, reporter_user_id, target_type, target_id, reason, status, handled_by, handled_at, created_at
)
VALUES
    (
        '43000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000002',
        'POST',
        '40000000-0000-0000-0000-000000000002',
        '帖子内容与题目无关，希望管理员确认。',
        'OPEN',
        NULL,
        NULL,
        '2026-10-23T02:40:00Z'
    ),
    (
        '43000000-0000-0000-0000-000000000002',
        '00000000-0000-0000-0000-000000000002',
        'COMMENT',
        '41000000-0000-0000-0000-000000000003',
        '评论包含无关链接（演示已处理记录）。',
        'RESOLVED',
        '00000000-0000-0000-0000-000000000001',
        '2026-10-23T02:50:00Z',
        '2026-10-23T02:45:00Z'
    )
ON CONFLICT (id) DO NOTHING;

INSERT INTO correction_report (
    id, reporter_user_id, question_id, question_version_id, target, description,
    status, handled_by, handled_at, created_at
)
VALUES (
    '44000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000002',
    '20000000-0000-0000-0000-000000000009',
    '21000000-0000-0000-0000-000000000009',
    'ANSWER',
    '建议把 syn 小写也加入可接受别名（演示纠错）。',
    'OPEN',
    NULL,
    NULL,
    '2026-10-23T02:42:00Z'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO admin_audit_log (
    id, actor_user_id, action, target_type, target_id, outcome, summary_json, trace_id, created_at
)
VALUES (
    '45000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'CONTENT_REPORT_RESOLVE',
    'CONTENT_REPORT',
    '43000000-0000-0000-0000-000000000002',
    'SUCCESS',
    '{"resolution":"RESOLVED","moderationAction":"HIDE","note":"演示审计记录"}'::jsonb,
    'demo-p2',
    '2026-10-23T02:50:00Z'
)
ON CONFLICT (id) DO NOTHING;

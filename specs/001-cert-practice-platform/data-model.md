# 数据模型设计

来源：[spec.md](./spec.md)｜日期：2026-10-23

## 1. 通用约定

- 主键使用 UUID；创建时间、更新时间使用 `timestamptz`。
- 所有可并发修改的聚合保存整数 `row_version`。
- 状态字段使用数据库 `varchar` + 应用枚举，并通过 `CHECK` 限制取值。
- 已提交答案、自评、题目版本、审计记录和成本记录不可由普通业务操作物理删除。
- 用户、题目和讨论软删除；已发布题目不能物理删除。
- 金额使用 `numeric(14,6)`，token 和次数使用整数。
- JSONB 仅保存规则快照、表单差异和审计摘要，不代替可查询的核心业务字段。
- 所有外键默认 `RESTRICT`；仅明确从属且可随父记录删除的草稿子项使用 `CASCADE`。

## 2. 实体关系概览

```text
certificate 1─* subject 1─* chapter
                         │
question *───────────────┘
  ├─* question_version
  │    ├─* question_option
  │    ├─* question_answer_alias
  │    ├─* question_version_tag ─* tag
  │    └─0..1 ai_variant_source
  ├─* discussion_post
  └─* favorite / wrong_question

app_user 1─* practice_session 1─* session_item
                                ├─0..1 user_answer
                                └─0..1 self_assessment

app_user 1─* discussion_post 1─* discussion_comment
app_user 1─* notification
app_user 1─* ai_conversation 1─* ai_message
app_user 1─* ai_request
app_user 1─* ai_usage_daily
```

普通练习和自测共用 `practice_session`，通过 `mode = NORMAL | SELF_TEST` 区分。

## 3. 身份与审计

### 3.1 `app_user`

| 字段 | 类型 | 约束/说明 |
|---|---|---|
| `id` | uuid | PK |
| `username` | varchar(64) | 展示用原值 |
| `username_normalized` | varchar(64) | 唯一；NFKC、去首尾空白、ASCII 小写 |
| `password_hash` | varchar(255) | Argon2id 或 BCrypt |
| `nickname` | varchar(64) | 可空，讨论展示使用 |
| `status` | varchar(24) | `ACTIVE`、`DISABLED`、`DELETED_ANONYMIZED` |
| `deleted_at` | timestamptz | 可空 |
| `row_version` | integer | 乐观锁 |
| `created_at` / `updated_at` | timestamptz | 非空 |

约束：

- `UNIQUE(username_normalized)`。
- 公开注册默认 `ACTIVE` 且只创建 `USER` 角色。
- 禁用或注销后，该用户对应的 `SPRING_SESSION` 记录必须在一次请求内失效。

### 3.2 `user_role`

`user_id`、`role` 联合主键；`role` 首期为 `USER`、`ADMIN`。

### 3.3 Spring Session JDBC

使用 Spring Session JDBC 标准表，不另建自定义 `auth_session`：

**`SPRING_SESSION`**

| 字段 | 说明 |
|---|---|
| `PRIMARY_ID` | 会话主键 |
| `SESSION_ID` | 会话标识，唯一 |
| `CREATION_TIME` | 创建时间 |
| `LAST_ACCESS_TIME` | 最近访问时间 |
| `MAX_INACTIVE_INTERVAL` | 最大空闲秒数 |
| `EXPIRY_TIME` | 过期时间 |
| `PRINCIPAL_NAME` | 当前用户标识，用于禁用用户后批量失效 |

**`SPRING_SESSION_ATTRIBUTES`**

| 字段 | 说明 |
|---|---|
| `SESSION_PRIMARY_ID` | 外键关联 `SPRING_SESSION.PRIMARY_ID` |
| `ATTRIBUTE_NAME` | 属性名 |
| `ATTRIBUTE_BYTES` | 序列化属性值 |

应用在受保护请求中读取会话后仍须重新检查 `app_user.status`；不保存明文凭据或业务数据。

### 3.4 `admin_audit_log`

| 字段 | 说明 |
|---|---|
| `id` | UUID |
| `actor_user_id` | 操作者 |
| `action` | 发布、下线、审核退回、禁用、重置密码、内容下架、额度调整等 |
| `target_type` / `target_id` | 目标资源 |
| `outcome` | `SUCCESS` / `FAILED` / `REJECTED` |
| `summary_json` | 必要变更摘要，不含密码和密钥 |
| `trace_id` | 链路标识 |
| `created_at` | 只追加 |

## 4. 目录与题库

### 4.1 `certificate`、`subject`、`chapter`

| 实体 | 关键字段 | 唯一约束 |
|---|---|---|
| `certificate` | `id`、`code`、`name`、`status`、`sort_order` | `code` |
| `subject` | `id`、`certificate_id`、`code`、`name`、`status`、`sort_order` | `(certificate_id, code)` |
| `chapter` | `id`、`subject_id`、`code`、`name`、`status`、`sort_order` | `(subject_id, code)` |

首期只使用一层章节，不建立任意深度目录。知识点由章节和标签组合表达。

### 4.2 `tag`

字段：`id`、`name`、`normalized_name`、`status`、`created_at`。

约束：`UNIQUE(normalized_name)`。标签不是题目难度，难度使用独立字段。

### 4.3 `question`

稳定题目身份，不保存可编辑正文。

| 字段 | 说明 |
|---|---|
| `id` | 题目稳定 ID |
| `subject_id` | 所属科目 |
| `type` | `SINGLE_CHOICE`、`SINGLE_BLANK` |
| `status` | `DRAFT`、`PENDING_REVIEW`、`PUBLISHED`、`OFFLINE` |
| `published_version_id` | 可空；当前可被新会话抽取的版本 |
| `created_by` | 创建者 |
| `created_at` / `updated_at` | 时间 |

约束：

- `published_version_id` 必须属于当前题目且状态为已发布。
- 当前发布版本存在时可用于新会话；下线后指针置空但保留历史版本。

### 4.4 `question_version`

题目内容的事实来源；创建后正文不可原地修改。

| 字段 | 说明 |
|---|---|
| `id` | UUID |
| `question_id` | 所属稳定题目 |
| `version_no` | 从 1 递增 |
| `status` | `DRAFT`、`PENDING_REVIEW`、`PUBLISHED`、`OFFLINE` |
| `stem` | 题干，非空 |
| `explanation` | 官方解析 |
| `fill_reference_answer` | 单空题规范答案，可空 |
| `difficulty` | `EASY`、`MEDIUM`、`HARD` |
| `source_text` | 来源和版权说明 |
| `author_id` / `reviewed_by` | 作者、审核人 |
| `reviewed_at` / `published_at` / `offline_at` | 状态时间 |
| `superseded_at` | 被新版本替代时间；仅旧发布版本使用 |
| `content_hash` | 规范化内容哈希，用于查重 |
| `row_version` | 编辑和审核并发控制 |
| `created_at` / `updated_at` | 时间 |

生命周期：

```text
DRAFT → PENDING_REVIEW → PUBLISHED → OFFLINE
  ↑          │
  └─退回─────┘

发布新版本后：
旧 PUBLISHED 版本保持历史状态并写 superseded_at
question.published_version_id 原子切换到新版本
```

约束：

- `UNIQUE(question_id, version_no)`。
- 同一题目最多一个当前发布版本：部分唯一索引 `question_id WHERE status = 'PUBLISHED' AND superseded_at IS NULL`。
- 发布时必须校验题目类型所需字段和正确选项/规范答案。
- 发布后的题干、解析、难度、选项和别名均按不可变处理。

### 4.5 题目子表

**`question_option`**

- 字段：`id`、`question_version_id`、`option_key`、`content`、`is_correct`、`sort_order`。
- `UNIQUE(question_version_id, option_key)`。
- 当前版本最多一个 `is_correct = true` 的选项；通过部分唯一索引和应用发布校验共同保证。

**`question_answer_alias`**

- 字段：`id`、`question_version_id`、`value`、`normalized_value`。
- `UNIQUE(question_version_id, normalized_value)`。
- 规范化限定为 NFKC、去首尾空白、连续空白折叠和 ASCII 小写；不做数值容差或单位换算。

**`question_version_tag`**

- `question_version_id`、`tag_id` 联合主键。
- 按版本保存标签，历史会话不受标签后续改名或解除影响。

### 4.6 `ai_variant_source`

字段：`question_version_id`、`source_question_version_id`、`generation_candidate_id`、`model_name`、`prompt_version`。

- `question_version_id` 唯一。
- 只允许已通过审核的 AI 变式题拥有此记录。
- 来源版本不可为本身，形成来源链时禁止循环。

### 4.7 导入、审核和报错

**`import_batch`**

字段：`id`、`admin_user_id`、`file_name`、`file_hash`、`status`、`total_rows`、`valid_rows`、`invalid_rows`、`preview_expires_at`、`confirmed_at`、`created_at`。

状态：`UPLOADED → VALIDATING → PREVIEW_READY → CONFIRMED`，异常为 `FAILED`。默认整批校验通过后才允许确认导入。

**`import_row_error`**

字段：`batch_id`、`row_number`、`field_name`、`error_code`、`message`。不保存整份上传文件。

**`review_event`**

字段：`question_version_id`、`actor_user_id`、`from_status`、`to_status`、`comment`、`created_at`。只追加。

**`correction_report`**

字段：`id`、`reporter_user_id`、`question_id`、`question_version_id`、`target`、`description`、`status`、`handled_by`、`handled_at`。

状态：`OPEN → PROCESSING → RESOLVED | REJECTED`。

## 5. 练习、作答与学习状态

### 5.1 `practice_session`

| 字段 | 说明 |
|---|---|
| `id` | UUID |
| `user_id` | 所有者 |
| `mode` | `NORMAL`、`SELF_TEST` |
| `status` | `IN_PROGRESS`、`SUBMITTED`、`PENDING_SELF_ASSESSMENT`、`COMPLETED`、`ABANDONED` |
| `filter_snapshot` | 目录、标签、历史窗口等筛选快照 |
| `rule_snapshot` | 抽题权重、6:4 比例、难度目标、规则版本 |
| `requested_count` / `actual_count` | 请求题量、实际题量 |
| `original_count` / `ai_variant_count` | 自测实际组成 |
| `started_at` / `submitted_at` / `completed_at` | 状态时间 |
| `idempotency_key` | 与用户联合唯一 |
| `row_version` | 锁与幂等 |

规则快照必须足以复现抽题和结果解释。创建后不随系统配置变化。

### 5.2 `session_item`

| 字段 | 说明 |
|---|---|
| `id` | UUID |
| `session_id` | 会话 |
| `ordinal` | 1..N |
| `question_id` | 稳定题目身份 |
| `question_version_id` | 创建会话时固定的不可变版本 |
| `source_type` | `ORIGINAL`、`AI_VARIANT` |
| `selection_bucket` | `UNSEEN`、`WRONG`、`NORMAL` |
| `final_result` | 可空；单选提交或自评完成后写入 `PASS` / `FAIL` |

约束：

- `UNIQUE(session_id, ordinal)`。
- `UNIQUE(session_id, question_id)`，防止同一会话重复题。
- 版本必须属于题目且创建时处于可抽取状态。

### 5.3 `user_answer`

字段：`id`、`session_item_id`、`response_json`、`answer_revision`、`auto_match_result`、`normalized_answer`、`answered_at`、`updated_at`。

- `UNIQUE(session_item_id)`。
- `auto_match_result` 为 `NOT_APPLICABLE`、`MATCH`、`MISMATCH`。
- 提交前可更新；提交时锁定。提交后任何答案写入返回 `409`。
- 部分提交不予接受；所有会话题目必须先有答案。

### 5.4 `self_assessment`

字段：`id`、`session_item_id`、`result`、`assessed_by`、`assessed_at`。

- `UNIQUE(session_item_id)`。
- `result` 为 `PASS` / `FAIL`。
- 只适用于单空题，且父会话已经提交。
- 首次确认后不可修改；会话全部自评后生成最终结果。

### 5.5 `favorite`

字段：`user_id`、`question_id`、`created_at`。联合主键 `(user_id, question_id)`。

### 5.6 `wrong_question` 与 `wrong_question_event`

**`wrong_question`**

字段：`id`、`user_id`、`question_id`、`active`、`first_failed_at`、`last_failed_at`、`removed_at`、`row_version`。

- 部分唯一索引：`(user_id, question_id) WHERE active = true`。
- 手动移除只将 `active` 置 false，不删除历史事件。
- 再次失败重新激活同一条错题状态。

**`wrong_question_event`**

字段：`wrong_question_id`、`session_item_id`、`event_type`、`created_at`。记录 `ADDED`、`REMOVED`、`REACTIVATED`，只追加。

## 6. 社区与通知

### 6.1 `discussion_post`

字段：`id`、`question_id`、`question_version_id`、`author_user_id`、`title`、`body`、`status`、`created_at`、`updated_at`。

状态：`VISIBLE`、`HIDDEN`、`REMOVED`。

- 版本必须属于题目。
- 新版本发布不迁移旧帖。
- 进行中的题目会话隐藏本题讨论入口。

### 6.2 `discussion_comment`

字段：`id`、`post_id`、`parent_comment_id`、`quoted_comment_id`、`author_user_id`、`body`、`status`、`created_at`。

- `parent_comment_id` 为空表示一级评论；非空时父评论必须同帖且自身也是一级评论。
- 引用回复通过 `quoted_comment_id` 表达，不增加第三层。
- 状态与帖子一致。

### 6.3 `content_report`

字段：`id`、`reporter_user_id`、`target_type`、`target_id`、`reason`、`status`、`handled_by`、`handled_at`、`created_at`。

`target_type` 包含 `QUESTION`、`POST`、`COMMENT`。同一用户对同一目标在 `OPEN/PROCESSING` 状态下的重复举报可合并或返回已有记录。

### 6.4 `notification`

字段：`id`、`recipient_user_id`、`type`、`title`、`body`、`payload_json`、`read_at`、`created_at`。

类型至少包含评论回复、审核/举报处理结果和系统公告。`read_at` 为空表示未读。

## 7. AI 数据

### 7.1 生成任务与候选

**`ai_generation_batch`**

字段：`id`、`requested_by`、`subject_id`、`chapter_id`、`source_question_ids`、`target_count`、`model_name`、`prompt_version`、`status`、`attempt_count`、`leased_until`、`last_error`、`created_at`、`completed_at`。

状态：`CREATED → RUNNING → VALIDATING → PENDING_REVIEW → COMPLETED`，异常为 `FAILED`。调度器通过 `SELECT ... FOR UPDATE SKIP LOCKED` 领取。

**`ai_generation_candidate`**

字段：`id`、`batch_id`、`source_question_version_id`、`candidate_json`、`content_hash`、`validation_status`、`review_status`、`converted_question_version_id`、`created_at`。

候选表只是暂存。通过结构校验和人工审核后，转换为正式的 `question` + `question_version`；正式版本是题库与审核的唯一事实来源。

### 7.2 AI 会话

**`ai_conversation`**

字段：`id`、`user_id`、`scope`、`question_id`、`question_version_id`、`session_item_id`、`status`、`created_at`、`last_message_at`。

`scope = GENERAL | QUESTION`。题目会话必须引用本人已完成且已自评的 `session_item`，并在创建时验证。

**`ai_message`**

字段：`id`、`conversation_id`、`role`、`content`、`provider`、`model_name`、`input_tokens`、`output_tokens`、`request_id`、`created_at`。

不保存供应商密钥。清空会话只删除用户可见消息正文或标记为清空，不删除成本汇总。

### 7.3 额度、预算与请求

**`ai_request`**

字段：`id`、`user_id`、`conversation_id`、`idempotency_key`、`quota_date`、`status`、`reserved_cost`、`actual_cost`、`external_request_id`、`error_code`、`created_at`、`finished_at`。

状态：`RESERVED → SUCCEEDED | FAILED | REFUNDED`。`(user_id, idempotency_key)` 唯一。

**`ai_usage_daily`**

字段：`user_id`、`usage_date`、`request_count`、`input_tokens`、`output_tokens`、`actual_cost`。`(user_id, usage_date)` 唯一。

**`ai_budget_daily`**

字段：`scope`、`usage_date`、`cost_limit`、`cost_used`。首期 `scope = TEAM`，按日唯一。

额度预留与结算必须锁定对应日账本行。失败请求是否退还个人次数按需求：默认退还，但供应商已产生的成本仍写入预算。

## 8. 配置与幂等

### 8.1 `system_config_version`

字段：`id`、`config_key`、`config_value_json`、`version_no`、`effective_from`、`created_by`、`created_at`。

抽题权重、难度目标、自测比例、AI 默认额度、填空规范化规则版本等应版本化。进行中会话使用创建时写入的规则快照。

### 8.2 `idempotency_record`

用于创建会话、提交、自评完成和 AI 请求等关键写操作。

字段：`user_id`、`operation`、`idempotency_key`、`request_hash`、`response_status`、`response_body`、`expires_at`、`created_at`。

约束：`UNIQUE(user_id, operation, idempotency_key)`。相同键但请求体不同返回 `409`。

## 9. 关键索引

```text
app_user(username_normalized) UNIQUE
question(subject_id, status, type)
question_version(question_id, version_no) UNIQUE
question_version(question_id) WHERE status='PUBLISHED' AND superseded_at IS NULL UNIQUE
question_version_tag(tag_id, question_version_id)
SPRING_SESSION(PRINCIPAL_NAME, LAST_ACCESS_TIME)
session_item(session_id, ordinal) UNIQUE
session_item(session_id, question_id) UNIQUE
practice_session(user_id, created_at DESC)
practice_session(user_id, status, updated_at DESC)
user_answer(session_item_id) UNIQUE
self_assessment(session_item_id) UNIQUE
wrong_question(user_id, question_id) WHERE active=true UNIQUE
discussion_post(question_version_id, status, created_at DESC)
discussion_comment(post_id, status, created_at)
notification(recipient_user_id, read_at, created_at DESC)
ai_usage_daily(user_id, usage_date) UNIQUE
ai_budget_daily(scope, usage_date) UNIQUE
ai_request(user_id, idempotency_key) UNIQUE
```

全文检索索引：

```text
GIN(question_version.stem gin_trgm_ops)
GIN(chapter.name gin_trgm_ops)
GIN(tag.normalized_name gin_trgm_ops)
```

## 10. 核心校验

- 题目发布前必须有题干、解析、难度、来源和合法答案结构。
- 单选题至少有 2 个选项且恰好 1 个正确选项。
- 单空题必须有非空规范答案；别名规范化后不得冲突。
- 自测只能选择已发布原题和已审核发布的 AI 变式题。
- 会话题量、来源比例和实际组成保存快照；不足时记录真实降级结果。
- 提交要求所有会话题目都有答案；提交后答案冻结。
- 单空题提交后必须逐题自评，全部完成才能生成最终成绩。
- 题目 AI 仅允许当前用户、已完成会话、对应题目版本。
- 进行中会话不得通过任何响应 DTO 获得答案、正确选项标记、别名或官方解析。
- 已发布题目、已提交作答、自评、审计和成本记录不得物理删除。

## 11. 状态转换权限

| 实体 | 操作 | 允许角色/前置条件 |
|---|---|---|
| 用户 | 注销 | 普通用户；`ACTIVE/DISABLED → DELETED_ANONYMIZED`；`ADMIN` 返回 403 |
| 题目版本 | 草稿编辑 | 管理员；仅 `DRAFT` |
| 题目版本 | 送审 | 管理员；`DRAFT → PENDING_REVIEW` |
| 题目版本 | 审核退回 | 管理员；`PENDING_REVIEW → DRAFT` |
| 题目版本 | 发布 | 管理员；`PENDING_REVIEW → PUBLISHED` 且结构校验通过 |
| 题目版本 | 下线 | 管理员；当前发布版本 |
| 练习会话 | 修改答案 | 所有者；`IN_PROGRESS` |
| 练习会话 | 提交 | 所有者；`IN_PROGRESS` 且所有题已答 |
| 单空题 | 自评 | 所有者；会话 `PENDING_SELF_ASSESSMENT` |
| AI 题目会话 | 创建/提问 | 所有者；对应会话项 `COMPLETED` |
| 讨论帖 | 隐藏/下架 | 管理员或内容治理流程 |

## 12. 数据生命周期

- 草稿版本及其未引用子项可按管理操作清理；已发布和已下线版本保留。
- 用户注销后停止登录并匿名化用户名和昵称，保留必要业务外键。
- 题目、作答、自评、审计和成本记录不随用户匿名化而删除。
- 具体匿名化字段和保留期限在公开部署前由产品与隐私要求确认；数据库通过 `deleted_at` 和配置保留扩展空间。
- 所有清理操作必须显式、可审计，不由普通接口自动级联删除业务历史。

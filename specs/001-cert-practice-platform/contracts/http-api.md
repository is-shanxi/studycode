# HTTP API 契约

版本：v1｜日期：2026-10-23

## 1. 通用协议

### 1.1 路径与格式

- 浏览器基础路径：`/api/v1`。
- 管理端基础路径：`/api/v1/admin`。
- Java/Python 内部路径：`/internal/v1`，仅允许服务令牌访问。
- 请求与响应：`application/json; charset=utf-8`。
- 时间：ISO 8601 UTC；ID：UUID。
- 分页参数：`page` 从 0 开始，`size` 最大 100。

分页响应：

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "total": 0
}
```

### 1.2 认证

- 登录成功后由服务端创建会话，写入 `SESSION` HttpOnly Cookie 和可读取的 `XSRF-TOKEN` Cookie。
- 已认证的状态变更请求必须发送 `X-XSRF-TOKEN` 头。
- 浏览器不保存 JWT、密码或长期业务令牌。
- 401 表示未登录或会话失效；403 表示已登录但权限不足。

### 1.3 错误格式

使用 RFC Problem Details：

```json
{
  "type": "https://local.cert-practice/errors/answers-incomplete",
  "title": "Answers are incomplete",
  "status": 422,
  "detail": "All session items must be answered before submission.",
  "code": "ANSWERS_INCOMPLETE",
  "traceId": "9d0d37..."
}
```

| HTTP | 用途 | 常用错误码 |
|---|---|---|
| 400 | 请求结构错误 | `INVALID_REQUEST` |
| 401 | 未登录/会话失效 | `UNAUTHENTICATED` |
| 403 | 角色或资源禁止 | `FORBIDDEN` |
| 404 | 资源不存在或无权得知其存在 | `NOT_FOUND` |
| 409 | 状态冲突/重复键不一致 | `STATE_CONFLICT`、`IDEMPOTENCY_MISMATCH` |
| 422 | 业务规则不满足 | `ANSWERS_INCOMPLETE`、`NO_ELIGIBLE_QUESTIONS` |
| 429 | 请求频率或 AI 额度超限 | `RATE_LIMITED`、`AI_QUOTA_EXCEEDED` |
| 503 | AI 或依赖暂不可用 | `AI_UNAVAILABLE` |

### 1.4 幂等

以下接口接受 `Idempotency-Key`：

- 创建练习会话
- 提交练习
- 填空题自评
- 创建 AI 请求/消息
- 确认批量导入

同一用户、操作和键重复请求时：

- 请求哈希相同：返回原始状态码和响应。
- 请求哈希不同：返回 `409 IDEMPOTENCY_MISMATCH`。
- 幂等记录默认保留 24 小时。

### 1.5 并发

可修改资源响应包含 `rowVersion`。更新请求可携带 `If-Match: "rowVersion"`；不匹配返回 `409 STATE_CONFLICT`。

## 2. 安全 DTO

### 2.1 题目公开/练习安全视图

```json
{
  "questionId": "uuid",
  "questionVersionId": "uuid",
  "type": "SINGLE_CHOICE",
  "stem": "题干",
  "difficulty": "MEDIUM",
  "chapter": {
    "id": "uuid",
    "name": "章节"
  },
  "tags": [
    {
      "id": "uuid",
      "name": "高频"
    }
  ],
  "options": [
    {
      "id": "uuid",
      "key": "A",
      "content": "选项文本"
    }
  ]
}
```

该结构严禁包含：

- `isCorrect`
- 正确答案选项 ID
- 规范答案
- 可接受别名
- 官方解析
- 会暴露答案位置的内部排序

### 2.2 练习会话视图

```json
{
  "sessionId": "uuid",
  "mode": "NORMAL",
  "status": "IN_PROGRESS",
  "rowVersion": 3,
  "requestedCount": 10,
  "actualCount": 10,
  "composition": {
    "original": 10,
    "aiVariant": 0,
    "buckets": {
      "unseen": 5,
      "wrong": 3,
      "normal": 2
    }
  },
  "allowedActions": ["SAVE_ANSWER", "SUBMIT", "ABANDON"],
  "items": []
}
```

`items[]` 使用安全视图。提交后返回结果视图，才允许包含答案和解析。

## 3. 认证与个人中心

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/register` | 注册普通用户；不接受 `role` 字段 |
| POST | `/auth/sessions` | 用户名密码登录 |
| DELETE | `/auth/sessions/current` | 退出并销毁会话 |
| GET | `/auth/me` | 当前用户、角色、状态 |
| GET | `/me/profile` | 个人资料 |
| PATCH | `/me/profile` | 修改昵称等允许字段 |
| PUT | `/me/password` | 校验旧密码后修改密码 |
| DELETE | `/me` | 仅普通用户注销；执行匿名化软删除；管理员返回 `403 ADMIN_CANNOT_DELETE_SELF` |

注册示例：

```json
{
  "username": "student01",
  "password": "至少满足配置长度和复杂度",
  "passwordConfirmation": "..."
}
```

管理员不能通过注册接口创建，不能通过 `DELETE /me` 自行注销。会话响应不得返回密码哈希。

## 4. 目录、标签与搜索

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/catalog/tree` | 证书、科目、章节树 |
| GET | `/tags` | 可用标签 |
| GET | `/questions/search` | 公开题库筛选 |
| GET | `/questions/{questionId}` | 公开安全摘要；不返回答案与解析 |

搜索参数：

```text
certificateId, subjectId, chapterIds, tagIds, keyword,
difficulty, type, page, size
```

规则：

- 多标签取交集。
- 关键词只匹配题干、章节和标签。
- 不搜索选项、规范答案、别名或官方解析。
- 用户存在进行中的同一题目会话时，公开详情仍不得包含题解或答案。

## 5. 练习与自测

普通练习和自测统一使用 `practice-session`。

### 5.1 创建会话

`POST /practice-sessions`

```json
{
  "mode": "NORMAL",
  "filters": {
    "certificateId": "uuid",
    "subjectId": "uuid",
    "chapterIds": ["uuid"],
    "tagIds": ["uuid"],
    "difficulty": ["EASY", "MEDIUM", "HARD"],
    "historyDays": 90
  },
  "requestedCount": 10
}
```

自测可额外指定：

```json
{
  "mode": "SELF_TEST",
  "composition": {
    "originalRatio": 0.6,
    "aiVariantRatio": 0.4
  }
}
```

响应为会话安全视图。合法题不足 10 道时返回实际数量；零题返回 `422 NO_ELIGIBLE_QUESTIONS`。不返回未审核 AI 题。

### 5.2 其他练习接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/practice-sessions/{id}` | 当前用户会话；按状态返回安全或结果视图 |
| PUT | `/practice-sessions/{id}/items/{itemId}/answer` | 保存未提交答案 |
| POST | `/practice-sessions/{id}/submit` | 提交全部答案 |
| POST | `/practice-sessions/{id}/items/{itemId}/self-assessment` | 提交单题自评 |
| POST | `/practice-sessions/{id}/abandon` | 放弃会话，可选 |
| GET | `/practice-sessions/{id}/result` | 提交后成绩与逐题解析 |

保存答案：

```json
{
  "answerRevision": 4,
  "answer": {
    "selectedOptionId": "uuid"
  }
}
```

填空题：

```json
{
  "answerRevision": 4,
  "answer": {
    "text": "用户输入"
  }
}
```

提交请求可为空，但必须带 `Idempotency-Key`。所有题目未作答时返回 `422 ANSWERS_INCOMPLETE`。重复提交返回已冻结结果，不创建新成绩。

自评：

```json
{
  "result": "PASS"
}
```

`result` 只能是 `PASS` 或 `FAIL`。同一会话题目只能确认一次；重复相同请求返回原响应，不同请求返回 `409`。

### 5.3 结果视图

```json
{
  "sessionId": "uuid",
  "status": "COMPLETED",
  "objective": {
    "answered": 10,
    "correct": 7
  },
  "fill": {
    "autoMatched": 2,
    "selfPassed": 2,
    "selfFailed": 1
  },
  "items": [
    {
      "itemId": "uuid",
      "questionVersionId": "uuid",
      "finalResult": "PASS",
      "answer": {},
      "correctOptionId": "uuid",
      "referenceAnswer": "可空",
      "explanation": "官方解析",
      "autoMatchResult": "MATCH",
      "selfAssessmentResult": "PASS"
    }
  ]
}
```

结果页明确区分客观单选结果、自动匹配建议和用户自评结果。

## 6. 记录、收藏与错题

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/me/records` | 当前用户记录，按时间和状态筛选 |
| GET | `/me/records/{id}` | 记录详情，使用当时题目版本 |
| GET | `/me/favorites` | 收藏列表 |
| PUT | `/me/favorites/{questionId}` | 收藏，幂等 |
| DELETE | `/me/favorites/{questionId}` | 取消收藏，幂等 |
| GET | `/me/wrong-questions` | 当前激活错题 |
| DELETE | `/me/wrong-questions/{questionId}` | 手动移出错题集，保留历史 |

所有接口强制当前用户所有权。访问他人资源返回 `404`。

## 7. 讨论、评论、举报与通知

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/questions/{questionId}/discussions` | 帖子列表；进行中会话默认 `409` |
| POST | `/questions/{questionId}/discussions` | 发布帖子，绑定当前题目版本 |
| GET | `/discussions/{postId}` | 帖子与一级评论 |
| POST | `/discussions/{postId}/comments` | 发布一级评论或引用回复 |
| POST | `/comments/{commentId}/report` | 举报评论 |
| POST | `/discussions/{postId}/report` | 举报帖子 |
| POST | `/correction-reports` | 报告题目答案、题干、解析或标签错误 |

发帖：

```json
{
  "questionVersionId": "uuid",
  "title": "标题",
  "body": "内容"
}
```

评论：

```json
{
  "parentCommentId": "uuid 或 null",
  "quotedCommentId": "uuid 或 null",
  "body": "内容"
}
```

服务端限制最多一层父子关系。已隐藏或下架内容不出现在公开列表。

通知：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/me/notifications` | 分页列表，可按已读筛选 |
| PUT | `/me/notifications/{id}/read` | 标记已读 |
| PUT | `/me/notifications/read-all` | 全部标记已读 |

## 8. AI 辅导

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/ai/quota` | 当前用户剩余次数、会话轮次和团队可用状态 |
| POST | `/ai/conversations` | 创建通用或题目会话 |
| POST | `/ai/conversations/{id}/messages` | 发送消息 |
| GET | `/ai/conversations/{id}` | 获取本人会话消息 |
| DELETE | `/ai/conversations/{id}` | 清空用户可见会话记录 |

创建题目会话：

```json
{
  "scope": "QUESTION",
  "sessionItemId": "uuid"
}
```

创建通用会话：

```json
{
  "scope": "GENERAL"
}
```

规则：

- 题目会话必须引用本人且已完成的会话项，否则 `409`。
- 通用会话不自动附带题目上下文。
- 会话轮次或个人额度超限返回 `429`。
- AI 暂不可用返回 `503`；不泄露供应商内部错误。

## 9. 管理端

### 9.1 题目与审核

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/questions` | 管理搜索，可检索全部字段 |
| POST | `/admin/questions` | 创建稳定题目和草稿版本 |
| GET | `/admin/questions/{id}` | 题目与版本历史 |
| POST | `/admin/questions/{id}/versions` | 从当前版本创建新草稿 |
| PUT | `/admin/question-versions/{versionId}` | 编辑 `DRAFT` |
| DELETE | `/admin/question-versions/{versionId}` | 删除未引用的草稿 |
| POST | `/admin/question-versions/{versionId}/submit-review` | 送审 |
| POST | `/admin/question-versions/{versionId}/approve` | 审核通过并可发布 |
| POST | `/admin/question-versions/{versionId}/reject` | 退回草稿 |
| POST | `/admin/questions/{id}/offline` | 下线当前发布版本 |

发布请求可携带 `publishNow`。所有状态变更记录管理员、前后状态和 trace ID。

### 9.2 批量导入

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/admin/question-imports` | 上传 CSV/XLSX 并创建预览任务 |
| GET | `/admin/question-imports/{id}` | 查看校验结果与错误行 |
| POST | `/admin/question-imports/{id}/confirm` | 确认导入，需幂等键 |
| DELETE | `/admin/question-imports/{id}` | 取消未确认任务 |

默认整批校验通过才能确认。导入后的题目仍需经过发布流程。

### 9.3 其他管理接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/users` | 查询用户 |
| POST | `/admin/users/{id}/disable` | 禁用用户并失效会话 |
| POST | `/admin/users/{id}/enable` | 启用用户 |
| POST | `/admin/users/{id}/reset-password` | 生成一次显示的重置结果，不返回明文存储 |
| GET | `/admin/reports` | 举报和题目报错 |
| POST | `/admin/reports/{id}/resolve` | 处理举报 |
| GET | `/admin/dashboard` | 活跃、完成率、匹配率、自评率、错误率、审核量、AI 成本 |
| GET | `/admin/audit-logs` | 审计记录查询 |
| GET | `/admin/ai/generation-batches` | AI 生成任务 |
| POST | `/admin/ai/generation-batches` | 创建变式题生成任务 |
| POST | `/admin/ai/generation-candidates/{id}/review` | 审核候选并转换为正式版本 |
| GET/PUT | `/admin/configs` | 抽题、AI 额度、规则配置，带版本控制 |

管理接口不接受普通用户 Cookie 越权访问；公开注册不能创建管理员。

## 10. Java 到 Python 内部契约

### 10.1 公共头

```text
X-Internal-Token: service token
X-Trace-Id: uuid
X-Request-Id: uuid
```

请求超时默认 30 秒，生成任务可配置更长超时。首期不自动重试超时请求。

### 10.2 辅导

`POST /internal/v1/chat`

```json
{
  "requestId": "uuid",
  "provider": "configured-provider",
  "model": "configured-model",
  "messages": [
    {
      "role": "user",
      "content": "..."
    }
  ],
  "context": {
    "scope": "QUESTION",
    "stem": "...",
    "questionType": "SINGLE_BLANK",
    "userAnswer": "...",
    "selfAssessment": "PASS"
  },
  "limits": {
    "maxOutputTokens": 1200,
    "timeoutMs": 30000
  }
}
```

响应：

```json
{
  "requestId": "uuid",
  "content": "...",
  "usage": {
    "inputTokens": 200,
    "outputTokens": 300
  },
  "providerRequestId": "supplier-id"
}
```

官方解析和规范答案默认不出现在 `context` 中；开启策略由 Java 配置控制。

### 10.3 变式题生成

`POST /internal/v1/generate-variants`

```json
{
  "requestId": "uuid",
  "sourceQuestionVersionId": "uuid",
  "stem": "...",
  "type": "SINGLE_CHOICE",
  "chapter": "...",
  "tags": ["..."],
  "targetCount": 30,
  "constraints": {
    "difficulty": "MEDIUM",
    "promptVersion": "v1"
  }
}
```

响应为候选数组，每项包含题干、题型、选项或规范答案、解析、难度和建议标签。Java 必须重新校验结构、答案、重复性和标签有效性，不能直接信任 Python 输出。

### 10.4 内部错误

```json
{
  "code": "MODEL_TIMEOUT",
  "message": "Model request timed out",
  "requestId": "uuid",
  "retryable": false
}
```

内部错误只由 Java 转为稳定的用户错误码。不得把供应商原始响应、密钥、堆栈或完整请求内容返回浏览器。

## 11. 兼容性与测试

- 所有公开 DTO 需要契约测试。
- 安全测试必须断言进行中会话、搜索结果、讨论和错误响应不包含答案字段。
- 幂等测试覆盖相同请求重放、不同请求冲突和并发提交。
- Java/Python 契约测试使用固定 Mock 响应，不依赖真实模型。
- API 版本升级只允许通过 `/api/v2` 或明确的兼容策略进行。

# 本地运行与验收指南

日期：2026-10-23｜依赖：[plan.md](./plan.md)、[data-model.md](./data-model.md)、[http-api.md](./contracts/http-api.md)

本指南描述 P1、P2、P3 已实现后的本地运行、联调和回归验收方式。统一检查脚本、演示数据和自动化测试均已就绪；`§10` 记录了 2026-11-02 的执行结果。

## 1. 前置环境

| 工具 | 建议版本 |
|---|---|
| Node.js | 22 LTS |
| pnpm | 10.x |
| JDK | 21 |
| Python | 3.12 |
| Docker Desktop / Engine | 支持 Compose v2 |
| PostgreSQL 客户端 | 可选，用于排查 |

浏览器验证使用 Chrome、Edge 或 Firefox 最近两个主要版本。

## 2. 服务与端口

| 服务 | 地址 |
|---|---|
| Vue 前端 | `http://localhost:5173` |
| Java API | `http://localhost:8080` |
| Python AI | `http://localhost:8000` |
| PostgreSQL | `localhost:5432` |

## 3. 环境配置

创建本地环境文件，不从仓库读取真实密钥：

```powershell
Copy-Item -LiteralPath 'infra/env/.env.example' -Destination 'infra/env/.env.local'
```

至少配置：

```dotenv
POSTGRES_DB=cert_practice
POSTGRES_USER=cert_practice
POSTGRES_PASSWORD=local-only-password
DATABASE_URL=jdbc:postgresql://localhost:5432/cert_practice
SESSION_COOKIE_SECURE=false
JAVA_AI_BASE_URL=http://localhost:8000
JAVA_AI_SERVICE_TOKEN=local-internal-token
AI_PROVIDER=mock
AI_MODEL=mock-model
AI_API_KEY=
AI_REQUEST_TIMEOUT_SECONDS=30
```

真实模型联调时只修改本地环境文件，不提交密钥。

## 4. 启动步骤

### 4.1 PostgreSQL

```powershell
docker compose --env-file 'infra/env/.env.local' -f 'infra/compose.yaml' up -d postgres
docker compose --env-file 'infra/env/.env.local' -f 'infra/compose.yaml' ps
```

预期：`postgres` 状态为 healthy。Java 启动时 Flyway 自动迁移并加载演示数据。

### 4.2 Java API

```powershell
Set-Location 'backend/java'
./mvnw spring-boot:run
```

预期：

- 日志显示 Flyway 迁移成功。
- `GET http://localhost:8080/actuator/health` 返回 `UP`。
- API 基址为 `http://localhost:8080/api/v1`。

### 4.3 Python AI

```powershell
Set-Location 'backend/python'
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -e ".[dev]"
python -m uvicorn app.main:app --port 8000 --reload
```

预期：`GET http://localhost:8000/health/live` 返回 `{"status":"ok"}`。默认 Mock 模式不调用外部供应商；真实供应商使用 `AI_PROVIDER=openai-compatible`、`AI_PROVIDER_BASE_URL`、`AI_MODEL` 和 `AI_API_KEY`。

### 4.4 Vue 前端

```powershell
Set-Location 'frontend'
pnpm install --frozen-lockfile
pnpm dev
```

预期：

- `http://localhost:5173/app` 打开用户端。
- `http://localhost:5173/admin` 在管理员登录后可用。
- Vite 开发代理把 `/api` 转发到 Java，浏览器形成同源 Cookie 与 CSRF 调用。

### 4.5 自测与 AI（P3）

1. 以 `student01` 登录，打开 `/app/selftest`，保留默认 60/40 比例并开始自测。
2. 预期会话页显示 `原题 6 / AI 变式 4`；完成全部题目并提交，填空题逐题自评。
3. 结果页出现“本题讲解”入口；打开后应看到剩余额度、会话轮次和团队预算。发送问题后，Mock 模式返回 `[mock]` 开头的内容。
4. 新建一场未提交的普通练习，直接请求本题 AI；Java 应返回 `409 STATE_CONFLICT`，Python 不应收到该题上下文。
5. 以 `admin` 登录 `/admin/ai`，可看到种子生成任务与 `PENDING_REVIEW` 候选；批准候选后会转换为已发布题目版本，拒绝则不会创建题目。
6. 在 `/admin/ai` 修改个人额度、会话轮次或团队预算后保存，配置版本递增；`/app/assistant` 的额度面板按新配置展示。

## 5. 演示账号与数据

演示数据由可重复的种子迁移或脚本创建，至少包含：

- 一个普通用户和一个管理员。
- 单证书、单科目、多个章节和标签。
- 单选题、单空填空题、规范答案和别名。
- 已审核与待审核 AI 变式题。
- 讨论、通知、错题和至少一份历史练习记录。

演示密码只用于本地环境，文档和脚本中不得复用为生产凭据。

## 6. 自动化检查

### 6.1 前端

```powershell
Set-Location 'frontend'
pnpm lint
pnpm format:check
pnpm typecheck
pnpm test:unit
pnpm test:e2e
```

### 6.2 Java

```powershell
Set-Location 'backend/java'
./mvnw test
./mvnw verify
# PowerShell 使用 .\mvnw.cmd test / .\mvnw.cmd verify
```

集成测试使用 Testcontainers PostgreSQL，不依赖开发者本机数据库。

### 6.3 Python

```powershell
Set-Location 'backend/python'
.\.venv\Scripts\Activate.ps1
python -m pytest
```

### 6.4 统一入口

实现阶段应提供便捷脚本执行前后端检查，例如：

```powershell
./scripts/check.ps1
```

脚本只串联现有测试命令，不隐藏失败。

## 7. 端到端验收场景

| 编号 | 操作 | 预期 | 对应需求 |
|---|---|---|---|
| V-01 | 注册时附加 `role=ADMIN` | 请求被拒绝或角色被忽略，账号仍为 `USER` | AC-01 |
| V-02 | 普通用户调用管理接口 | 返回 403；访问他人记录返回 404 | AC-01 |
| V-03 | 组合两个标签筛选 | 只返回同时满足标签的题目 | AC-02 |
| V-04 | 用答案文本搜索 | 不能命中隐藏选项、答案或解析 | AC-02 |
| V-05 | 题池充足时创建普通练习 | 返回 10 道不重复题，响应不含答案解析 | AC-03 |
| V-06 | 双击/重试提交 | 只产生一份成绩，答案冻结 | AC-04 |
| V-07 | 提交单空题并仅自动匹配 | 进入待自评，尚未完成 | AC-05 |
| V-08 | 自动失败但人工自评成功 | 两个结果分别保存，最终为自评成功 | AC-05 |
| V-09 | 修改已发布题并再次发布 | 旧记录使用旧版本，新练习使用新版本 | AC-06 |
| V-10 | 错题移除后再次失败 | 历史保留，错题重新激活 | AC-07 |
| V-11 | 自测题池不足 | 原题补足或返回实际题量，不生成未审核题 | AC-08 |
| V-12 | 未审核 AI 变式题进入自测 | 抽取结果中不存在该题 | AC-09 |
| V-13 | 进行中会话请求本题 AI | Java 返回 409，Python 未收到题目上下文 | AC-10 |
| V-14 | 并发发送 AI 请求 | 个人额度和团队预算均不超限 | AC-11 |
| V-15 | 讨论下架并检查通知 | 公开列表隐藏，处理通知持久化，审计存在 | AC-12 |
| V-16 | 导入含错误行的文件 | 预览显示错误且不落库；修正后确认导入 | AC-13 |
| V-17 | 题目下线后创建练习 | 新会话不包含该题，历史仍可打开 | AC-14 |
| V-18 | 禁用用户后复用旧会话 | 原会话失效，受保护接口返回 401/403 | AC-14 |

## 8. 安全人工检查

在浏览器开发者工具中确认：

- 练习进行中接口响应不含 `isCorrect`、`correctOptionId`、`referenceAnswer`、`explanation` 或别名。
- Cookie 为 HttpOnly；除 CSRF 外没有长期令牌写入 localStorage。
- 管理员页面隐藏时，直接调用管理接口仍被后端拒绝。
- AI 未完成题目请求被 Java 拒绝，Python 日志没有收到该题上下文。
- 错误响应不包含 SQL、堆栈、供应商原始错误或提示词。

## 9. 完成标准

P1 本地验收必须满足：

- 所有服务可独立启动，Flyway 迁移可重复执行。
- V-01 至 V-10、V-16、V-17、V-18 通过。
- 核心单元、集成和 E2E 测试通过。
- 演示数据可重建，不依赖手工改库。

P2/P3 已补齐 V-11 至 V-15 的自动化与人工检查。真实模型联调前，Mock 路径必须先全部通过；E2E 的 AI 场景需要按 4.3 启动 Python 服务。

## 10. 2026-11-02 执行记录

**环境**：Windows 11、Docker Desktop 29.5.2、PostgreSQL 16.15、Java 21.0.3、Python 3.13.9（`.venv`）、Playwright Chromium/Firefox。

### 10.1 自动化检查

| 检查 | 命令 | 结果 |
|---|---|---|
| 统一检查 | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/check.ps1` | 通过：前端 lint/format/typecheck/unit；Java 全量测试与 0 Checkstyle 违规；Python ruff/format/mypy/pytest |
| Chromium E2E | `VITE_API_PROXY_TARGET=http://localhost:8081 npx playwright test --project=chromium` | 15/15 通过（独立数据库） |
| Firefox E2E | `VITE_API_PROXY_TARGET=http://localhost:8082 npx playwright test --project=firefox` | 15/15 通过（独立数据库） |
| Cookie 与本地存储 | `curl` 登录后检查 Cookie jar + 前端源码检索 | `SESSION` 带 `HttpOnly`；`XSRF-TOKEN` 可读；前端无 `localStorage` 使用 |

> E2E 套件会创建进行中会话并执行举报/治理操作。重复执行或让 Chromium 与 Firefox 共用同一数据库时，残留的进行中会话会阻止 P2 讨论场景；本轮为每个浏览器使用独立数据库验证，重建数据库后即可复跑。

### 10.2 验收场景执行结果

| 编号 | 执行方式 | 结果 |
|---|---|---|
| V-01 | `AuthIntegrationTest.registrationIgnoresClientSuppliedAdminRole`、`p1-core-flow` 注册旅程 | 通过 |
| V-02 | `AuthIntegrationTest.ordinaryUserCannotCallAdminEndpoints`、`SecurityReviewIntegrationTest.everyAdminSurfaceRejectsOrdinaryUsers`、`ownershipIsEnforcedForSessionsRecordsNotificationsAndAiConversations` | 通过 |
| V-03 | `PracticeIntegrationTest.searchUsesTagIntersectionAndNeverHiddenFields` | 通过 |
| V-04 | `PracticeIntegrationTest.searchUsesTagIntersectionAndNeverHiddenFields`（选项/解析关键词不命中） | 通过 |
| V-05 | `PracticeIntegrationTest.sessionCreationIsIdempotentDeduplicatedAndAnswerSafe`、`p1-core-flow` 练习旅程 | 通过 |
| V-06 | `PracticeIntegrationTest.submissionRequiresAllAnswersAndCannotBeRepeated` | 通过 |
| V-07 | `PracticeIntegrationTest.submissionRequiresAllAnswersAndCannotBeRepeated`（自动匹配后仍待自评） | 通过 |
| V-08 | `PracticeIntegrationTest.autoMismatchAndManualPassAreBothPreserved`（自动失败 + 自评成功结果并存） | 通过 |
| V-09 | `QuestionVersionIntegrationTest.publishingNewVersionKeepsHistoricalSessionsAndOfflineKeepsHistory`、`p1-version-history` E2E | 通过 |
| V-10 | `PracticeIntegrationTest.wrongQuestionsFavoritesAndOwnershipFollowP1Rules` | 通过 |
| V-11 | `SelfTestIntegrationTest.fillsVariantShortageWithApprovedOriginals`、`usesActualCountWhenTheLegalPoolIsSmallerThanRequested`、`rejectsSelfTestWhenNoLegalQuestionMatches` | 通过 |
| V-12 | `SelfTestIntegrationTest.excludesUnpublishedOrUnapprovedVariantsFromThePool` | 通过 |
| V-13 | `AiGateIntegrationTest.rejectsQuestionConversationsForInProgressItems`、`p3-selftest-ai` 未提交拒绝旅程 | 通过 |
| V-14 | `AiQuotaConcurrencyTest`（个人额度、会话轮次、团队预算、幂等与退款） | 通过 |
| V-15 | `ModerationIntegrationTest.reportResolutionHidesContentNotifiesReporterAndWritesAudit`、`NotificationIntegrationTest.commentRepliesCreatePersistentUnreadNotifications`、`p2-community` E2E | 通过 |
| V-16 | `QuestionImportIntegrationTest.csvPreviewReportsErrorsWithoutWritingAndConfirmCreatesDrafts`、`xlsxUploadIsParsedAndConfirmed`、`QuestionImportUploadLimitTest` | 通过 |
| V-17 | `PracticeIntegrationTest.offlineQuestionIsExcludedFromNewSessionsButHistoryRemains` | 通过 |
| V-18 | `SecurityReviewIntegrationTest.disabledUsersLoseTheirExistingSession`、`AuthIntegrationTest.deletedUserSessionIsRejectedOnNextRequest` | 通过 |

### 10.3 安全人工检查替代结果

| 检查项 | 方式 | 结果 |
|---|---|---|
| 进行中练习响应不含 `isCorrect`、`correctOptionId`、`referenceAnswer`、`explanation`、别名 | `AnswerVisibilityContractTest`、`P1RedactionTest`、`SecurityReviewIntegrationTest` 与 E2E 断言 | 通过 |
| Cookie `HttpOnly`，除 CSRF 外无长期令牌写入 `localStorage` | curl Cookie jar + `grep -rn localStorage frontend/src` | 通过 |
| 管理员页面隐藏时直接调用管理接口仍被拒绝 | `SecurityReviewIntegrationTest.everyAdminSurfaceRejectsOrdinaryUsers` | 通过 |
| 未完成题目 AI 请求被 Java 拒绝，Python 不接收题目上下文 | `AiGateIntegrationTest`、`p3-selftest-ai` | 通过 |
| 错误响应不包含 SQL、堆栈、供应商原始错误或提示词 | `P1RedactionTest`、`AiQuotaConcurrencyTest.failedRequestsRefundPersonalQuotaButKeepTheReservedBudget`、Python `test_redaction.py` | 通过 |

本轮使用 MockProvider 完成全部 AI 路径，未进行真实模型供应商联调。响应式检查覆盖 360/768/1280 视口工程基线，最终支持的浏览器和移动端尺寸仍待产品确认。

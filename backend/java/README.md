# Java 业务后端

Java 21 + Spring Boot 3 模块化单体，是 PostgreSQL 中业务数据的唯一写入方。

## 已接入能力

- 账号：注册、登录、退出、资料与密码修改、普通用户软删除匿名化；管理员不能自行注销。
- 题库：目录树、标签、关键词/多标签交集搜索、不可变题目版本、草稿编辑、送审、发布、下线。
- 练习：受控随机抽题（未见/错题/普通 50/30/20，难度加权，不足回退）、会话快照、答案保存、统一提交、幂等重放。
- 填空：自动匹配仅作建议，逐题自评决定最终结果；全部自评后会话完成。
- 学习状态：历史记录（固定当时版本）、收藏、错题加入/移出/再次失败重新激活。
- 导入：CSV/XLSX 预览校验、错误行展示、整批确认后创建草稿。
- 安全：服务端会话 + HttpOnly Cookie + CSRF；答案安全 DTO；管理端角色门禁；审计日志；登录失败限流/锁定；CSP 与上传大小限制。
- 自测：默认 6:4 原题/AI 变式，变式不足用原题补齐，合法题不足按实际题量；规则快照固定创建时的比例、种子和组成。
- AI 辅导：通用与本题会话；本题仅允许本人已完成会话；官方答案解析默认不进入上下文；个人日额度、会话轮次和团队预算由服务端原子控制。
- AI 生成：数据库租约调度、超时重试上限、候选结构/重复校验、人工审核后转换为不可变已发布版本。
- AI 配置：管理员通过 `system_config_version` 维护带版本的额度与上下文策略。
- 通知角标：`/api/v1/me/notifications/unread-count` 提供未读计数，前端 Pinia Store 负责登录后、路由切换和轮询刷新。
- 查询性能：练习会话/结果 DTO 批量加载题目、选项、标签、章节、答案和自评；`V9__index_tuning.sql` 补充搜索、历史、看板和抽题候选索引。

## 命令

```powershell
# 依赖 Docker（Testcontainers PostgreSQL）
mvn test
mvn verify          # 包含 Checkstyle
mvn spring-boot:run
```

本地数据库：

```powershell
# 仓库根目录
Copy-Item infra/env/.env.example infra/env/.env.local
docker compose --env-file infra/env/.env.local -f infra/compose.yaml up -d postgres
```

Flyway 启动时自动迁移并加载演示数据。

## 演示账号

| 用户名 | 密码 | 角色 |
|---|---|---|
| `admin` | `Admin@12345` | ADMIN |
| `student01` | `Student@12345` | USER |

演示密码仅用于本地开发，不得复用到其他环境。

## P1 主要接口

| 分组 | 路径 |
|---|---|
| 认证 | `/api/v1/auth/register`、`/api/v1/auth/sessions`、`/api/v1/auth/me` |
| 个人中心 | `/api/v1/me/profile`、`/api/v1/me/password`、`DELETE /api/v1/me` |
| 目录与搜索 | `/api/v1/catalog/tree`、`/api/v1/tags`、`/api/v1/questions/search` |
| 练习 | `/api/v1/practice-sessions`、`.../submit`、`.../items/{itemId}/answer`、`.../self-assessment` |
| 记录与收藏 | `/api/v1/me/records`、`/api/v1/me/favorites`、`/api/v1/me/wrong-questions` |
| 管理题库 | `/api/v1/admin/questions`、`/api/v1/admin/question-versions/{id}/...` |
| 批量导入 | `/api/v1/admin/question-imports` |
| 自测 | `POST /api/v1/practice-sessions`（`mode=SELF_TEST` + `composition`） |
| AI 辅导 | `/api/v1/ai/quota`、`/api/v1/ai/conversations`、`.../messages` |
| 管理 AI | `/api/v1/admin/ai/generation-batches`、`.../generation-candidates/{id}/review`、`/api/v1/admin/configs` |

写操作需要 `X-XSRF-TOKEN`；创建会话、提交和自评支持 `Idempotency-Key`。

通知接口：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/me/notifications` | 分页通知，可按 `unreadOnly` 筛选 |
| GET | `/api/v1/me/notifications/unread-count` | 当前用户未读数量，用于全局角标 |
| PUT | `/api/v1/me/notifications/{id}/read` | 标记单条已读 |
| PUT | `/api/v1/me/notifications/read-all` | 全部标记已读 |

## 运维与可观测性

- 健康探针：`GET /api/v1/health`（版本化公开探针）和 `GET /actuator/health`（`show-details=never`，仅返回聚合状态）。
- 日志：每个请求一行 `method/path/status/durationMs` 和 trace ID，不记录请求头、查询串、请求体、答案、提示词或密钥；未处理异常只记录类型。
- 数据库结构只通过 Flyway 变更；不要修改已应用迁移。索引调优以新的 `V{n}__*.sql` 追加，`IndexTuningIntegrationTest` 校验关键索引存在。
- 查询回归：`PracticeDtoQueryCountTest` 对会话、结果和记录列表设置语句数上限；这些是回归护栏，不是产品性能验收阈值。

## 导入文件格式

CSV 或 XLSX，第一行为表头：

```text
type,subjectCode,chapterCode,stem,options,correctOption,fillAnswer,aliases,explanation,difficulty,source,tags
SINGLE_CHOICE,SUB-DEMO-01,CH-01,示例题干,A:选项一|B:选项二,B,,,,MEDIUM,原创,基础|高频
SINGLE_BLANK,SUB-DEMO-01,CH-01,示例填空,,,,SYN,同步位|同步标志,MEDIUM,原创,基础
```

- 选项格式：`A:内容|B:内容`。
- 别名和标签使用 `|` 分隔。
- 先预览校验，`invalidRows > 0` 时不能确认；确认后题目仍为草稿，需送审发布。

## 测试

- `AuthIntegrationTest`：注册角色升级、重复用户名、登录、越权、管理员自删、注销后会话失效。
- `QuestionVersionIntegrationTest`：版本发布、历史版本保留、下线、拒绝与草稿删除。
- `QuestionDraftUpdateIntegrationTest`：草稿替换选项、别名和标签时不会触发唯一索引冲突。
- `PracticeIntegrationTest`：会话幂等/去重、提交校验、自评完成、错题激活、收藏、记录所有权、搜索交集与离线排除。
- `AnswerVisibilityContractTest` / `P1RedactionTest`：答案字段与日志/错误响应脱敏。
- `SecurityReviewIntegrationTest`：角色门禁、CSRF、退出/禁用后的会话失效、资源归属和答案裁剪复核。
- `PracticeDtoQueryCountTest`：练习会话/结果/记录 DTO 的批量查询语句数回归护栏。
- `IndexTuningIntegrationTest`：搜索、历史、看板和抽题索引迁移存在性。
- `ObservabilityHealthIntegrationTest`：健康探针不暴露数据源或组件细节。
- `QuestionImportIntegrationTest`：CSV 错误预览、确认导入、重复题、XLSX。
- `PracticePerformanceTest`：记录 P1 响应时间与语句数基线，不设未确认的产品阈值。
- `SelfTestIntegrationTest`：6:4 组成、变式不足回退、实际题量、快照与未审核变式排除。
- `AiGateIntegrationTest` / `AiQuotaConcurrencyTest`：本题 AI 门禁、上下文最小化、并发额度/轮次/预算与幂等。
- `AiGenerationIntegrationTest` / `AiGenerationRetryTest`：候选校验、重复拒绝、审核转换、配置版本与重试上限。
- `OpenApiContractTest`：Spring MVC 路由与 `contracts/openapi.yaml` 双向比对。

# Java 业务后端

Java 21 + Spring Boot 3 模块化单体，是 PostgreSQL 中业务数据的唯一写入方。

## P1 已接入能力

- 账号：注册、登录、退出、资料与密码修改、普通用户软删除匿名化；管理员不能自行注销。
- 题库：目录树、标签、关键词/多标签交集搜索、不可变题目版本、草稿编辑、送审、发布、下线。
- 练习：受控随机抽题（未见/错题/普通 50/30/20，难度加权，不足回退）、会话快照、答案保存、统一提交、幂等重放。
- 填空：自动匹配仅作建议，逐题自评决定最终结果；全部自评后会话完成。
- 学习状态：历史记录（固定当时版本）、收藏、错题加入/移出/再次失败重新激活。
- 导入：CSV/XLSX 预览校验、错误行展示、整批确认后创建草稿。
- 安全：服务端会话 + HttpOnly Cookie + CSRF；答案安全 DTO；管理端角色门禁；审计日志。

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

写操作需要 `X-XSRF-TOKEN`；创建会话、提交和自评支持 `Idempotency-Key`。

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
- `PracticeIntegrationTest`：会话幂等/去重、提交校验、自评完成、错题激活、收藏、记录所有权、搜索交集与离线排除。
- `AnswerVisibilityContractTest` / `P1RedactionTest`：答案字段与日志/错误响应脱敏。
- `QuestionImportIntegrationTest`：CSV 错误预览、确认导入、重复题、XLSX。
- `PracticePerformanceTest`：记录 P1 响应时间与语句数基线，不设未确认的产品阈值。

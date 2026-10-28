# 本地运行与验收指南

日期：2026-10-23｜依赖：[plan.md](./plan.md)、[data-model.md](./data-model.md)、[http-api.md](./contracts/http-api.md)

本指南描述技术方案落地后的本地验证方式。当前仓库处于设计阶段，真正可执行前必须先完成 P1 实现和本地脚本。

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

预期：`GET http://localhost:8000/health/live` 返回 `{"status":"ok"}`。默认 Mock 模式不调用外部供应商。

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
pnpm typecheck
pnpm test
pnpm exec playwright test
```

### 6.2 Java

```powershell
Set-Location 'backend/java'
./mvnw test
./mvnw verify
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

P2/P3 分别在对应阶段补齐 V-11 至 V-15。真实模型联调前，Mock 路径必须先全部通过。

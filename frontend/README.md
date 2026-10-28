# 前端工程

Vue 3 + TypeScript + Vite + Element Plus 单页应用，通过 `/app`（用户端）和 `/admin`（管理端）分区，共用一个 API 客户端。

## P1 页面

用户端：

- `/app/login`、`/app/register`：登录与注册。
- `/app`：首页快捷入口。
- `/app/library`：目录、标签交集、难度/题型和关键词搜索；用当前筛选开始 10 题练习。
- `/app/practice/:sessionId`：单选/填空作答、保存、统一提交。
- `/app/practice/:sessionId/fill/:itemId`：填空题左右布局、参考答案、逐题自评。
- `/app/practice/:sessionId/result`：客观单选、自动匹配和自评结果分开展示。
- `/app/records`、`/app/records/:sessionId`：记录列表与按当时版本回放详情。
- `/app/favorites`、`/app/wrong-questions`：收藏与错题管理。
- `/app/account`：资料、密码和注销。

管理端：

- `/admin/questions`：题目检索、编辑、版本与下线。
- `/admin/questions/new`、`/admin/questions/:questionId/edit`：草稿编辑、送审、审核发布、版本历史。
- `/admin/review`：待审核队列。
- `/admin/import`：CSV/XLSX 上传、错误预览、确认导入。

## 命令

```powershell
pnpm install
pnpm dev          # http://localhost:5173
pnpm lint
pnpm format:check
pnpm typecheck
pnpm test:unit
pnpm test:e2e     # 首次执行前运行 pnpm test:e2e:install
```

开发服务器把 `/api` 与 `/actuator` 代理到 `VITE_API_PROXY_TARGET`（默认 `http://localhost:8080`）。

## 端到端测试

E2E 依赖已启动的 Java 后端和 PostgreSQL（演示数据由 Flyway 加载）。Playwright 会自动启动 Vite：

```powershell
# 仓库根目录先启动 postgres 与 Java 后端
docker compose --env-file infra/env/.env.local -f infra/compose.yaml up -d postgres
Set-Location backend/java
mvn spring-boot:run

# 另一个终端
Set-Location frontend
pnpm exec playwright test --project=chromium
```

覆盖场景：注册、完整练习与填空自评、发布新版本后历史记录仍显示旧版本。

## 安全约定

- 浏览器只保存 HttpOnly `SESSION` Cookie 和可读的 `XSRF-TOKEN` Cookie。
- 前端路由守卫只用于体验，服务端始终校验角色和资源所有权。
- 进行中会话的响应不包含 `isCorrect`、`correctOptionId`、`referenceAnswer`、`explanation` 或别名；单元测试会检查泄漏字段。

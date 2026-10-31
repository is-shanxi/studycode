# Implementation Plan: 考证刷题平台

**Branch**: `001-cert-practice-platform` | **Date**: 2026-10-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-cert-practice-platform/spec.md`

## Summary

构建一个面向单证书、单科目考证场景的刷题平台。首期完成账号权限、知识目录、不可变题目版本、搜索筛选、单选练习、单空填空自评、收藏错题、题解社区、站内通知、个性化自测、AI 辅导和管理看板。

系统采用前后端分离架构：Vue 3 + TypeScript 单页应用分别承载 `/app` 与 `/admin`；Java Spring Boot 模块化单体作为唯一业务后端和 PostgreSQL 写入方；Python FastAPI 作为无状态 AI 服务，通过内部 REST 接口提供辅导及变式题生成能力。题目、练习会话和讨论均保存版本快照，保证历史可追溯；本地使用 Docker Compose，不引入 Redis、消息队列或搜索引擎。

详细架构、模块边界、关键流程与部署说明见 [技术设计文档](../../docs/技术设计文档.md)。

## Technical Context

**Language/Version**: TypeScript 5.x + Vue 3；Java 21；Python 3.12

**Primary Dependencies**: Vue 3、Vite、Pinia、Vue Router、Element Plus、Spring Boot 3、Spring Security、Spring Data JPA、Flyway、FastAPI、Pydantic v2、PostgreSQL 16、Docker Compose

**Storage**: PostgreSQL 16，启用 `pg_trgm`；Spring Session 使用 PostgreSQL；无独立缓存、对象存储或消息队列

**Testing**: Vitest、Vue Test Utils、Playwright、JUnit 5、Spring Boot Test、Testcontainers、pytest、httpx

**Target Platform**: 工程适配基线，非最终验收承诺：桌面 Web 优先的响应式浏览器应用；本地开发覆盖 Chrome、Edge、Firefox 最近两个主要版本，正式支持范围待产品确认

**Project Type**: 前后端分离 Web application，包含 SPA、Java 业务服务、Python AI 服务和 PostgreSQL

**Performance Goals**: 工程观察基线，非验收门槛。普通读取接口建议 p95 < 300 ms，普通写入和练习提交建议 p95 < 800 ms；不包含外部 AI 调用；正式阈值待产品确认

**Constraints**: 2～3 人团队；单仓库；模块化单体；Java 独占业务数据库写入；Python 不访问业务表；服务端会话与 HttpOnly Cookie；提交前绝不返回答案或官方解析；本地环境不引入 Redis、消息队列、Elasticsearch、Nginx 和 Kubernetes

**Scale/Scope**: 1,000 DAU、100 同时在线、单科目约 1 万题、10 万历史作答；首期约 20 个主要页面，普通练习每次 10 题

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

当前宪章版本为 1.0.0，已定义业务数据单一写入、答案与权限安全、不可变历史、测试优先和本地简单运行五项原则。本计划及后续实现不得降低这些要求。

| Gate | Result | Evidence |
|---|---|---|
| 技术栈与部署边界 | PASS | Vue + TypeScript、Java、Python、PostgreSQL；无额外基础设施 |
| 业务数据单一写入方 | PASS | Java 独占数据库，Python 仅返回 AI 结果 |
| 权限与答案保护 | PASS | 服务端鉴权；练习 DTO 按会话状态裁剪答案字段 |
| 历史可追溯 | PASS | 不可变题目版本；练习和讨论保存版本引用 |
| 本地可运行 | PASS | Docker Compose + 三个应用进程，无外部中间件 |
| 测试覆盖 | PASS | 单元、集成、契约及关键 E2E 场景均已规划 |

初始门禁结果：全部通过，无复杂度豁免。

## Project Structure

### Documentation (this feature)

```text
specs/001-cert-practice-platform/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    ├── README.md
    └── http-api.md
```

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── app/                 # 用户端路由、页面、布局与页面组件
│   ├── admin/               # 管理端路由、页面
│   ├── components/          # 跨页面共享组件（预留）
│   ├── features/            # 按业务域组织的 API、组合式逻辑与会话 Store
│   ├── stores/              # 跨页面 UI 状态（预留）
│   ├── styles/
│   └── main.ts
└── tests/
    ├── unit/
    └── e2e/

backend/java/
├── src/main/java/com/certpractice/
│   ├── identity/
│   ├── catalog/
│   ├── question/
│   ├── practice/
│   ├── selftest/
│   ├── community/
│   ├── notification/
│   ├── admin/
│   ├── aigateway/
│   └── shared/
├── src/main/resources/db/migration/
└── src/test/

backend/python/
├── app/
│   ├── api/
│   ├── providers/
│   ├── prompts/
│   └── schemas/
└── tests/

infra/
├── compose.yaml
├── postgres/
└── env/

docs/
├── 需求规约.md
└── 技术设计文档.md
```

**Structure Decision**: 采用一个仓库、一个前端工程和两个后端进程。前端通过 `/app`、`/admin` 路由分区，但共用一个构建产物和 API 客户端；Java 内部按业务域组织为模块化单体；Python 只承载模型调用和生成逻辑。该结构满足 2～3 人团队的维护成本，同时保持前后端和 AI 运行时边界清晰。

## Phase 0: Research

研究任务及结论记录在 [research.md](./research.md)，覆盖：

- 模块化单体与 AI 服务边界
- 不可变题目版本与练习快照
- 受控抽题、权重回退和并发一致性
- 填空自动匹配与自评状态机
- PostgreSQL 搜索与索引
- Spring Session、Cookie、CSRF 和权限
- AI 防泄题、额度并发与无队列任务处理
- 批量导入、审计与通知
- 本地部署与测试策略

## Phase 1: Design and Contracts

- 数据实体、关系、约束和索引：[data-model.md](./data-model.md)
- HTTP 契约与通用协议：[contracts/http-api.md](./contracts/http-api.md)
- 契约目录说明：[contracts/README.md](./contracts/README.md)
- 本地运行与验收步骤：[quickstart.md](./quickstart.md)
- 面向开发者的整体技术说明：[技术设计文档.md](../../docs/技术设计文档.md)

## Post-Design Constitution Check

| Gate | Result | Evidence |
|---|---|---|
| 模块边界 | PASS | Java 业务模块按域组织，Python 无业务数据库访问 |
| 版本一致性 | PASS | 题目版本不可变，会话固定版本，发布使用乐观锁 |
| 安全边界 | PASS | Cookie 会话、CSRF、角色与资源所有权双层校验 |
| 答案防泄漏 | PASS | 提交前响应模型不构造答案字段，AI 服务受状态门禁 |
| 数据完整性 | PASS | 外键、唯一约束、状态机和事务边界已定义 |
| 可测试性 | PASS | 契约、集成、并发及 E2E 验收均有验证入口 |
| 运维简化 | PASS | 仅 PostgreSQL 和三个应用进程，异步任务使用数据库状态 |

设计后门禁结果：全部通过，无宪章违规。

## Complexity Tracking

无。当前设计不增加超出需求必要性的运行组件或架构层。

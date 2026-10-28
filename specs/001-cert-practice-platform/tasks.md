# Tasks: 考证刷题平台

**Input**: Design documents from `/specs/001-cert-practice-platform/`

**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/http-api.md](./contracts/http-api.md)、[quickstart.md](./quickstart.md)

**Tests**: 包含测试任务。需求规约 NFR-05 和验收场景 AC-01～AC-14 明确要求覆盖身份权限、抽题、自评、错题和 AI 审核。

**Organization**: 按已确认的 P1、P2、P3 交付阶段组织为三个用户故事，每个故事在基础阶段完成后可独立验收。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行执行，涉及不同文件且不依赖未完成任务。
- **[Story]**: 用户故事标签，仅用于用户故事阶段。
- 每个任务均包含明确目标、项目相对文件路径和必要的需求编号。

## Path Conventions

- 前端：`frontend/src/`、`frontend/tests/`
- Java：`backend/java/src/`
- Python：`backend/python/app/`、`backend/python/tests/`
- 基础设施：`infra/`
- 设计产物：`speckit/specs/001-cert-practice-platform/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 初始化单仓库、前后端工程、测试与本地运行骨架。

- [X] T001 Create the repository directory skeleton for `frontend/`, `backend/java/`, `backend/python/`, `infra/`, and `scripts/`
- [X] T002 Initialize the Vue 3 + TypeScript application metadata in `frontend/package.json`
- [X] T003 Configure Vite, TypeScript and Vue entry points in `frontend/vite.config.ts`, `frontend/tsconfig.json` and `frontend/src/main.ts`
- [X] T004 Initialize the Java 21 Spring Boot build in `backend/java/pom.xml`
- [X] T005 Initialize the Python 3.12 package and dependencies in `backend/python/pyproject.toml`
- [X] T006 [P] Configure PostgreSQL 16 and service orchestration in `infra/compose.yaml`
- [X] T007 [P] Add local-only environment templates in `infra/env/.env.example`
- [X] T008 [P] Configure Vue linting and formatting in `frontend/eslint.config.js` and `frontend/.prettierrc.json`
- [X] T009 [P] Configure Java formatting and static checks in `backend/java/checkstyle.xml` and `backend/java/pom.xml`
- [X] T010 [P] Configure Python linting and type checks in `backend/python/pyproject.toml`
- [X] T011 Create frontend, Java and Python test configuration in `frontend/tests/`, `backend/java/src/test/` and `backend/python/tests/conftest.py`
- [X] T012 Create the unified local validation entry point in `scripts/check.ps1`
- [X] T013 Create the initial OpenAPI contract shell in `speckit/specs/001-cert-practice-platform/contracts/openapi.yaml`

**Checkpoint**: 三个应用和 PostgreSQL 的工程骨架可安装依赖，静态检查命令可发现文件。

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 建立所有用户故事共用的数据库、身份、错误处理、前端外壳、AI 服务外壳和测试基础设施。

**CRITICAL**: 本阶段完成前不开始任何用户故事。

- [X] T014 Configure Flyway and PostgreSQL extensions in `backend/java/src/main/resources/application.yml` and `backend/java/src/main/resources/db/migration/V1__extensions.sql`
- [X] T015 Create identity, role, Spring Session JDBC standard tables, audit and idempotency tables in `backend/java/src/main/resources/db/migration/V2__identity_and_audit.sql`
- [X] T016 Create certificate, subject, chapter, tag, question and immutable version tables in `backend/java/src/main/resources/db/migration/V3__catalog_and_question.sql`
- [X] T017 Create practice session, item, answer, self-assessment, favorite and wrong-question tables in `backend/java/src/main/resources/db/migration/V4__practice.sql`
- [X] T018 Create discussion, comment, report and notification tables in `backend/java/src/main/resources/db/migration/V5__community.sql`
- [X] T019 Create AI generation, candidate, conversation, usage, budget and configuration tables in `backend/java/src/main/resources/db/migration/V6__ai_and_config.sql`
- [X] T020 [P] Implement shared UUID, timestamp, page and error primitives in `backend/java/src/main/java/com/certpractice/shared/`
- [X] T021 [P] Implement JPA entities and repositories for catalog and question domains in `backend/java/src/main/java/com/certpractice/catalog/` and `backend/java/src/main/java/com/certpractice/question/`
- [X] T022 [P] Implement JPA entities and repositories for practice domains in `backend/java/src/main/java/com/certpractice/practice/`
- [X] T023 [P] Implement JPA entities and repositories for community and notification domains in `backend/java/src/main/java/com/certpractice/community/` and `backend/java/src/main/java/com/certpractice/notification/`
- [X] T024 [P] Implement JPA entities for AI usage and generation workflows in `backend/java/src/main/java/com/certpractice/aigateway/`
- [X] T025 Implement Spring Security and JDBC-backed sessions in `backend/java/src/main/java/com/certpractice/identity/SecurityConfig.java`
- [X] T026 Implement current-user loading, role checks and resource ownership helpers in `backend/java/src/main/java/com/certpractice/shared/security/`
- [X] T027 Configure Cookie and CSRF behavior for the SPA in `backend/java/src/main/java/com/certpractice/identity/CsrfConfig.java`
- [X] T028 Implement RFC Problem Details exception handling in `backend/java/src/main/java/com/certpractice/shared/error/`
- [X] T029 Implement request trace IDs and redacted structured logging in `backend/java/src/main/java/com/certpractice/shared/observability/`
- [X] T030 Implement shared pagination and validation utilities in `backend/java/src/main/java/com/certpractice/shared/web/`
- [X] T031 Implement the typed fetch client with Cookie, CSRF and trace headers in `frontend/src/features/http/client.ts`
- [X] T032 Implement the session store and route guard skeleton in `frontend/src/features/auth/store.ts` and `frontend/src/router/index.ts`
- [X] T033 Implement `/app` and `/admin` route shells in `frontend/src/app/router.ts` and `frontend/src/admin/router.ts`
- [X] T034 Create the FastAPI application, environment settings and internal-token check in `backend/python/app/main.py` and `backend/python/app/config.py`
- [X] T035 Implement the model provider protocol and MockProvider in `backend/python/app/providers/base.py` and `backend/python/app/providers/mock.py`
- [X] T036 Create internal chat and generation route skeletons in `backend/python/app/api/chat.py` and `backend/python/app/api/generate.py`
- [X] T037 Create the Java to Python internal REST client with timeouts in `backend/java/src/main/java/com/certpractice/aigateway/AiServiceClient.java`
- [X] T038 Configure JUnit 5 and Testcontainers PostgreSQL harness in `backend/java/src/test/java/com/certpractice/support/`
- [X] T039 Configure pytest and HTTPX fixtures in `backend/python/tests/conftest.py`
- [X] T040 Configure Vitest, Vue Test Utils and Playwright in `frontend/tests/unit/` and `frontend/tests/e2e/`
- [X] T041 Add basic local seed users and catalog data in `backend/java/src/main/resources/db/seed/R__demo_baseline.sql`
- [X] T042 Implement Java and Python health endpoints in `backend/java/src/main/java/com/certpractice/shared/health/HealthController.java` and `backend/python/app/api/health.py`

**Checkpoint**: 数据库可迁移，会话与 CSRF 可用，三端服务可启动，测试容器可运行。

---

## Phase 3: User Story 1 - 核心刷题闭环 (Priority: P1) 🎯 MVP

**Requirement Scope**: FR-01、02、04～11、13～21

**Acceptance**: AC-01～AC-07、AC-13、AC-14

**Goal**: 完成账号、目录搜索、题目版本与审核、单选练习、填空自评、历史记录、收藏错题和题目导入。

**Independent Test**: 使用演示管理员创建并发布一道单选题和一道填空题，普通用户完成登录、搜索、10 题练习、提交、自评、查看记录、收藏和错题操作；修改题目发布新版本后，历史记录仍显示旧版本。

### Tests for User Story 1

- [X] T043 [P] [US1] Add failing integration tests for registration, login, role escalation, ownership and admin self-deletion rejection in `backend/java/src/test/java/com/certpractice/identity/AuthIntegrationTest.java` (AC-01、AC-14)
- [X] T044 [P] [US1] Add failing integration tests for question versions, publishing and historical references in `backend/java/src/test/java/com/certpractice/question/QuestionVersionIntegrationTest.java` (AC-06)
- [X] T045 [P] [US1] Add failing integration tests for session creation, submit, self-assessment and wrong-question updates in `backend/java/src/test/java/com/certpractice/practice/PracticeIntegrationTest.java` (AC-03、AC-04、AC-05、AC-07)
- [X] T046 [P] [US1] Add failing contract tests proving practice, search and discussion responses omit answers before submission in `backend/java/src/test/java/com/certpractice/practice/AnswerVisibilityContractTest.java` (AC-02、AC-03)
- [X] T047 [P] [US1] Add failing frontend unit tests for answer visibility and session actions in `frontend/tests/unit/practice-session.spec.ts`
- [X] T048 [P] [US1] Add failing Playwright journeys for register, practice submit and version history in `frontend/tests/e2e/p1-core-flow.spec.ts`

### Implementation for User Story 1

- [X] T049 [US1] Implement registration, login, logout, password change and account anonymization with an ADMIN self-deletion guard in `backend/java/src/main/java/com/certpractice/identity/IdentityService.java` (FR-01～04)
- [X] T050 [US1] Implement authentication and profile endpoints in `backend/java/src/main/java/com/certpractice/identity/IdentityController.java` and `backend/java/src/main/java/com/certpractice/identity/ProfileController.java`
- [X] T051 [P] [US1] Implement catalog tree and tag queries in `backend/java/src/main/java/com/certpractice/catalog/CatalogService.java` and `CatalogController.java`
- [X] T052 [P] [US1] Implement safe keyword and multi-tag search in `backend/java/src/main/java/com/certpractice/question/QuestionSearchService.java` (FR-05～06)
- [X] T053 [US1] Implement draft creation, cloning and immutable version validation in `backend/java/src/main/java/com/certpractice/question/QuestionAuthoringService.java` (FR-07～09)
- [X] T054 [US1] Implement review, publish, offline and transactional version pointer switching in `backend/java/src/main/java/com/certpractice/question/QuestionLifecycleService.java` (FR-08～10)
- [X] T055 [US1] Implement admin question and version endpoints in `backend/java/src/main/java/com/certpractice/question/AdminQuestionController.java`
- [X] T056 [US1] Add failing import validation tests and implement CSV/XLSX parsing, schema validation, alias checks and duplicate detection in `backend/java/src/test/java/com/certpractice/question/QuestionImportIntegrationTest.java` and `backend/java/src/main/java/com/certpractice/question/QuestionImportService.java` (FR-11、AC-13)
- [X] T057 [US1] Implement import preview and confirmation endpoints in `backend/java/src/main/java/com/certpractice/question/QuestionImportController.java`
- [X] T058 [US1] Implement weighted selection, bucket quotas, difficulty targets and shortage fallback in `backend/java/src/main/java/com/certpractice/practice/QuestionSelectionService.java` (FR-13)
- [X] T059 [US1] Implement session creation, safe loading and answer upsert in `backend/java/src/main/java/com/certpractice/practice/PracticeSessionService.java` (FR-14)
- [X] T060 [US1] Implement locked submission, grading and idempotent replay in `backend/java/src/main/java/com/certpractice/practice/PracticeSubmissionService.java` (FR-15)
- [X] T061 [US1] Implement fill normalization, alias matching and per-item self-assessment state transitions in `backend/java/src/main/java/com/certpractice/practice/FillAssessmentService.java` (FR-16～18)
- [X] T062 [US1] Implement answer-safe and result DTO mappers in `backend/java/src/main/java/com/certpractice/practice/PracticeDtoMapper.java`
- [X] T063 [US1] Implement practice record list and version-preserving detail queries in `backend/java/src/main/java/com/certpractice/practice/PracticeRecordService.java`
- [X] T064 [US1] Implement idempotent favorite add/remove and listing in `backend/java/src/main/java/com/certpractice/practice/FavoriteService.java` (FR-19)
- [X] T065 [US1] Implement active wrong-question updates and append-only failure events in `backend/java/src/main/java/com/certpractice/practice/WrongQuestionService.java` (FR-20～21)
- [X] T066 [US1] Implement practice session, answer, submit and self-assessment endpoints in `backend/java/src/main/java/com/certpractice/practice/PracticeController.java`
- [X] T067 [US1] Implement record, favorite and wrong-question endpoints in `backend/java/src/main/java/com/certpractice/practice/LearningHistoryController.java`
- [X] T068 [US1] Record question publish, offline, import and draft deletion audit events in `backend/java/src/main/java/com/certpractice/question/QuestionAuditService.java`
- [X] T069 [P] [US1] Add deterministic P1 demo questions, answers, aliases, exercise history and wrong questions in `backend/java/src/main/resources/db/seed/R__demo_p1.sql`
- [X] T070 [P] [US1] Implement login, registration and logout pages in `frontend/src/app/pages/LoginPage.vue` and `frontend/src/app/pages/RegisterPage.vue`
- [X] T071 [P] [US1] Implement account profile and password forms in `frontend/src/app/pages/AccountPage.vue`
- [X] T072 [P] [US1] Implement catalog browsing, keyword search and tag intersection filters in `frontend/src/app/pages/QuestionLibraryPage.vue`
- [X] T073 [P] [US1] Implement single-choice practice layout, answer editing and submit confirmation in `frontend/src/app/pages/PracticeSessionPage.vue`
- [X] T074 [P] [US1] Implement fill-question detail, reference display and per-item self-assessment in `frontend/src/app/pages/FillQuestionPage.vue`
- [X] T075 [P] [US1] Implement result and historical record detail views in `frontend/src/app/pages/PracticeResultPage.vue` and `frontend/src/app/pages/RecordDetailPage.vue`
- [X] T076 [P] [US1] Implement practice and self-test history filters in `frontend/src/app/pages/RecordListPage.vue`
- [X] T077 [P] [US1] Implement favorites list and idempotent toggle behavior in `frontend/src/app/pages/FavoritePage.vue`
- [X] T078 [P] [US1] Implement active wrong-question list and removal behavior in `frontend/src/app/pages/WrongQuestionPage.vue`
- [X] T079 [US1] Implement admin question list, filters and version history view in `frontend/src/admin/pages/QuestionListPage.vue`
- [X] T080 [US1] Implement admin single-choice and fill question editors in `frontend/src/admin/pages/QuestionEditorPage.vue`
- [X] T081 [US1] Implement review, publish, offline and rejection actions in `frontend/src/admin/pages/QuestionReviewPage.vue`
- [X] T082 [US1] Implement import upload, validation preview and confirmation in `frontend/src/admin/pages/QuestionImportPage.vue`
- [X] T083 [US1] Implement admin route definitions and role-aware navigation in `frontend/src/admin/router.ts` and `frontend/src/admin/layout/AdminLayout.vue`
- [X] T084 [US1] Generate and wire typed P1 API models in `frontend/src/features/api/types.ts` and `frontend/src/features/practice/api.ts`
- [X] T085 [US1] Add responsive breakpoints for core P1 pages in `frontend/src/styles/responsive.css`
- [X] T086 [US1] Complete P1 backend integration fixtures and transaction assertions in `backend/java/src/test/java/com/certpractice/practice/PracticeIntegrationTest.java`
- [X] T087 [US1] Implement the full P1 browser journey in `frontend/tests/e2e/p1-core-flow.spec.ts`
- [X] T088 [US1] Record P1 query-count and response-time baselines and detect material regressions without enforcing unconfirmed thresholds in `backend/java/src/test/java/com/certpractice/practice/PracticePerformanceTest.java`
- [X] T089 [US1] Add answer-redaction assertions for logs and problem responses in `backend/java/src/test/java/com/certpractice/security/P1RedactionTest.java`
- [X] T090 [US1] Update P1 execution notes and commands in `backend/java/README.md`, `frontend/README.md` and `backend/python/README.md`
- [X] T091 [US1] Add the immutable-version historical replay E2E journey in `frontend/tests/e2e/p1-version-history.spec.ts`

**Checkpoint**: US1 可独立完成从题库到练习、自评、记录、收藏错题和管理题库的闭环；通过 AC-01～AC-07、AC-13、AC-14 的 P1 部分。

---

## Phase 4: User Story 2 - 社区与运营闭环 (Priority: P2)

**Requirement Scope**: FR-03、12、27～29、35～36

**Acceptance**: AC-01、AC-06、AC-12、AC-14

**Goal**: 完成题目讨论、一级评论、举报与纠错、站内通知、用户管理、基础看板和管理审计。

**Independent Test**: 使用已发布的演示题创建帖子和评论，完成举报处理、题目纠错新版本、通知已读、用户禁用和看板指标查询；进行中的练习不能访问本题讨论。

### Tests for User Story 2

- [ ] T092 [P] [US2] Add failing integration tests for version-bound discussions and one-level comments in `backend/java/src/test/java/com/certpractice/community/DiscussionIntegrationTest.java` (AC-12)
- [ ] T093 [P] [US2] Add failing tests for moderation, correction reports and audit entries in `backend/java/src/test/java/com/certpractice/community/ModerationIntegrationTest.java` (AC-12)
- [ ] T094 [P] [US2] Add failing tests for notification creation and read state in `backend/java/src/test/java/com/certpractice/notification/NotificationIntegrationTest.java` (AC-12)
- [ ] T095 [P] [US2] Add failing Playwright journeys for discussion, report, notification and admin moderation in `frontend/tests/e2e/p2-community.spec.ts`

### Implementation for User Story 2

- [ ] T096 [US2] Implement version-bound discussion creation and listing in `backend/java/src/main/java/com/certpractice/community/DiscussionService.java` (FR-27)
- [ ] T097 [US2] Enforce question access and in-progress discussion suppression in `backend/java/src/main/java/com/certpractice/community/DiscussionAccessService.java`
- [ ] T098 [US2] Implement one-level comments, quote references and content filtering in `backend/java/src/main/java/com/certpractice/community/CommentService.java` (FR-28)
- [ ] T099 [US2] Implement question correction reports and report state transitions in `backend/java/src/main/java/com/certpractice/community/CorrectionReportService.java` (FR-12)
- [ ] T100 [US2] Implement content reports, admin hiding/removal and audit records in `backend/java/src/main/java/com/certpractice/community/ModerationService.java` (FR-28、36)
- [ ] T101 [US2] Implement public discussion and comment endpoints in `backend/java/src/main/java/com/certpractice/community/DiscussionController.java`
- [ ] T102 [US2] Implement correction and content report endpoints in `backend/java/src/main/java/com/certpractice/community/ReportController.java`
- [ ] T103 [US2] Implement transactional notification creation for replies, moderation and announcements in `backend/java/src/main/java/com/certpractice/notification/NotificationService.java` (FR-29)
- [ ] T104 [US2] Implement notification list, read and read-all endpoints in `backend/java/src/main/java/com/certpractice/notification/NotificationController.java`
- [ ] T105 [US2] Implement admin user query, disable, enable and one-time password reset in `backend/java/src/main/java/com/certpractice/admin/AdminUserService.java` (FR-03)
- [ ] T106 [US2] Implement admin report query and resolution endpoints in `backend/java/src/main/java/com/certpractice/admin/AdminModerationController.java`
- [ ] T107 [US2] Implement dashboard aggregate queries without a BI store in `backend/java/src/main/java/com/certpractice/admin/DashboardService.java` (FR-35)
- [ ] T108 [US2] Implement append-only audit query and filters in `backend/java/src/main/java/com/certpractice/admin/AuditQueryService.java` (FR-36)
- [ ] T109 [US2] Expose admin user, dashboard, report and audit endpoints in `backend/java/src/main/java/com/certpractice/admin/AdminOperationsController.java`
- [ ] T110 [P] [US2] Implement discussion list and detail pages with version labels in `frontend/src/app/pages/DiscussionListPage.vue` and `frontend/src/app/pages/DiscussionDetailPage.vue`
- [ ] T111 [P] [US2] Implement post and one-level reply forms in `frontend/src/app/components/DiscussionComposer.vue` and `frontend/src/app/components/CommentThread.vue`
- [ ] T112 [P] [US2] Implement report and correction dialogs in `frontend/src/app/components/ContentReportDialog.vue`
- [ ] T113 [P] [US2] Implement the notification center with persistent read state in `frontend/src/app/pages/NotificationPage.vue`
- [ ] T114 [US2] Implement admin user management UI in `frontend/src/admin/pages/UserManagementPage.vue`
- [ ] T115 [US2] Implement admin moderation queue UI in `frontend/src/admin/pages/ModerationPage.vue`
- [ ] T116 [US2] Implement the small operational dashboard in `frontend/src/admin/pages/DashboardPage.vue`
- [ ] T117 [US2] Implement audit log query UI in `frontend/src/admin/pages/AuditLogPage.vue`
- [ ] T118 [P] [US2] Add P2 demo discussions, notifications, reports and moderation records in `backend/java/src/main/resources/db/seed/R__demo_p2.sql`
- [ ] T119 [US2] Complete the P2 browser journey in `frontend/tests/e2e/p2-community.spec.ts`

**Checkpoint**: US2 可独立使用种子题目完成社区、举报、通知、用户管理和审计验收；通过 AC-12 及 AC-01、AC-06、AC-14 的运营部分。

---

## Phase 5: User Story 3 - 自测与 AI 辅导 (Priority: P3)

**Requirement Scope**: FR-22～26、30～34

**Acceptance**: AC-08～AC-11

**Goal**: 完成已审核 AI 变式题池、个性化自测、题目辅导门禁、额度与成本控制、Mock/真实供应商适配和管理端生成审核。

**Independent Test**: 使用种子原题和已审核候选创建 6:4 自测；题池不足时正确回退。进行中会话禁止题目 AI，完成后允许本人辅导；并发请求不超个人额度和团队预算；真实密钥缺失时 Mock 路径仍可完成联调。

### Tests for User Story 3

- [ ] T120 [P] [US3] Add failing integration tests for self-test composition, shortage fallback and snapshot replay in `backend/java/src/test/java/com/certpractice/selftest/SelfTestIntegrationTest.java` (AC-08、AC-09)
- [ ] T121 [P] [US3] Add failing tests for AI access gating, context minimization and conversation ownership in `backend/java/src/test/java/com/certpractice/aigateway/AiGateIntegrationTest.java` (AC-10)
- [ ] T122 [P] [US3] Add failing concurrency tests for personal quota, session turns and team budget in `backend/java/src/test/java/com/certpractice/aigateway/AiQuotaConcurrencyTest.java` (AC-11)
- [ ] T123 [P] [US3] Add failing Python tests for provider contracts, Mock output validation and timeout mapping in `backend/python/tests/test_provider_contract.py`
- [ ] T124 [P] [US3] Add failing Playwright journeys for self-test and completed-question AI tutoring in `frontend/tests/e2e/p3-selftest-ai.spec.ts`

### Implementation for User Story 3

- [ ] T125 [US3] Implement database-leased generation batch scheduling with retry limits in `backend/java/src/main/java/com/certpractice/aigateway/AiGenerationScheduler.java` (FR-25)
- [ ] T126 [US3] Implement candidate validation, duplicate rejection and conversion to formal question versions in `backend/java/src/main/java/com/certpractice/aigateway/AiCandidateService.java`
- [ ] T127 [US3] Implement the internal chat contract and Mock response path in `backend/python/app/api/chat.py`
- [ ] T128 [US3] Implement the internal variant generation contract and structured output validation in `backend/python/app/api/generate.py`
- [ ] T129 [US3] Implement the single real provider adapter behind the provider protocol in `backend/python/app/providers/real.py` and `backend/python/app/config.py` (FR-33)
- [ ] T130 [US3] Implement Java internal AI request, response, timeout and error mapping in `backend/java/src/main/java/com/certpractice/aigateway/AiServiceClient.java`
- [ ] T131 [US3] Implement completed-session and ownership checks before题目 AI context creation in `backend/java/src/main/java/com/certpractice/aigateway/AiAccessService.java` (FR-30～32)
- [ ] T132 [US3] Implement AI conversation and message persistence with clear-record behavior in `backend/java/src/main/java/com/certpractice/aigateway/AiConversationService.java`
- [ ] T133 [US3] Implement atomic quota reservation, budget settlement and refunds in `backend/java/src/main/java/com/certpractice/aigateway/AiQuotaService.java` (FR-34)
- [ ] T134 [US3] Implement 6:4 self-test selection, history weighting, shortage fallback and rule snapshots in `backend/java/src/main/java/com/certpractice/selftest/SelfTestSelectionService.java` (FR-22～24)
- [ ] T135 [US3] Integrate self-test creation with the existing practice session lifecycle in `backend/java/src/main/java/com/certpractice/practice/PracticeSessionService.java`
- [ ] T136 [US3] Implement self-test and AI conversation endpoints in `backend/java/src/main/java/com/certpractice/selftest/SelfTestController.java` and `backend/java/src/main/java/com/certpractice/aigateway/AiController.java`
- [ ] T137 [US3] Implement admin AI batch creation, candidate review and configuration endpoints in `backend/java/src/main/java/com/certpractice/admin/AdminAiController.java`
- [ ] T138 [US3] Implement configurable AI context policy and quota defaults in `backend/java/src/main/java/com/certpractice/aigateway/AiConfigService.java` (FR-33～34)
- [ ] T139 [P] [US3] Implement self-test range, composition and result pages in `frontend/src/app/pages/SelfTestPage.vue` and `frontend/src/app/pages/SelfTestResultPage.vue`
- [ ] T140 [P] [US3] Implement the general and completed-question AI assistant in `frontend/src/app/pages/AiAssistantPage.vue`
- [ ] T141 [US3] Show题目 AI entry only when server-provided actions allow it in `frontend/src/app/pages/PracticeResultPage.vue`
- [ ] T142 [P] [US3] Implement remaining AI quota and limit feedback in `frontend/src/app/components/AiQuotaPanel.vue`
- [ ] T143 [US3] Implement admin AI generation monitoring and trigger UI in `frontend/src/admin/pages/AiGenerationPage.vue`
- [ ] T144 [US3] Implement candidate review and conversion UI in `frontend/src/admin/pages/AiCandidateReviewPage.vue`
- [ ] T145 [P] [US3] Add Mock AI and approved variant seed data in `backend/java/src/main/resources/db/seed/R__demo_p3.sql`
- [ ] T146 [US3] Complete the Python provider and internal-contract test suite in `backend/python/tests/test_provider_contract.py`
- [ ] T147 [US3] Complete P3 concurrency and idempotency assertions in `backend/java/src/test/java/com/certpractice/aigateway/AiQuotaConcurrencyTest.java`
- [ ] T148 [US3] Add AI prompt, answer and secret redaction tests in `backend/python/tests/test_redaction.py`
- [ ] T149 [US3] Complete the P3 browser journey in `frontend/tests/e2e/p3-selftest-ai.spec.ts`

**Checkpoint**: US3 可使用 MockProvider 独立完成变式题审核、自测、AI 门禁和额度验收；通过 AC-08～AC-11。

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 收口跨故事安全性、性能、文档和完整验收。

- [ ] T150 [P] Complete and validate OpenAPI 3.1 coverage for public and admin APIs in `speckit/specs/001-cert-practice-platform/contracts/openapi.yaml`
- [ ] T151 [P] Run a full authorization, CSRF, session invalidation and answer-leak review across `backend/java/src/test/java/com/certpractice/security/`
- [ ] T152 Review and tune PostgreSQL indexes and query plans for search, practice history and dashboards in `backend/java/src/main/resources/db/migration/V7__index_tuning.sql`
- [ ] T153 [P] Validate responsive engineering baselines at 360 px, 768 px and 1280 px in `frontend/tests/e2e/responsive.spec.ts` without treating them as final product acceptance until confirmed
- [ ] T154 Review structured-log redaction and health checks in `backend/java/src/main/java/com/certpractice/shared/observability/` and `backend/python/app/api/health.py`
- [ ] T155 Update architecture, operations and module ownership notes in `docs/技术设计文档.md` and `backend/java/README.md`
- [ ] T156 Run all frontend, Java, Python and contract checks through `scripts/check.ps1`
- [ ] T157 Execute every applicable scenario in `quickstart.md` and record expected results in `speckit/specs/001-cert-practice-platform/quickstart.md`
- [ ] T158 Remove unused placeholders and verify all configuration keys in `infra/env/.env.example` and `infra/compose.yaml`
- [ ] T159 Complete the requirements-to-test traceability review against AC-01～AC-14 in `speckit/specs/001-cert-practice-platform/spec.md` and `frontend/tests/e2e/`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: 无前置依赖。
- **Phase 2 Foundational**: 依赖 Setup；完成后才能开始用户故事。
- **Phase 3 US1**: 依赖 Foundational；MVP 首选路径。
- **Phase 4 US2**: 依赖 Foundational；可用种子题目独立开发，最终与 US1 题目数据联调。
- **Phase 5 US3**: 依赖 Foundational；可用已审核种子变式和已完成种子会话独立开发，最终与 US1/US2 联调。
- **Phase 6 Polish**: 依赖计划纳入交付的所有用户故事。

### User Story Dependencies

- **US1 (P1)**: 无其他故事依赖；提供完整核心闭环。
- **US2 (P2)**: 不依赖 US1 的 UI 和练习实现；题目版本、讨论和通知表已在 Foundational 建立，测试使用固定种子题。
- **US3 (P3)**: 不依赖 US2；题目、练习、会话和 AI 表已在 Foundational 建立，测试使用已完成种子会话和 MockProvider。

### Within Each User Story

1. 先完成失败测试任务。
2. 再完成后端服务与事务。
3. 完成 Controller/API 契约。
4. 完成前端页面和 API 接入。
5. 最后运行该故事的集成与 E2E 验收。

### Critical Path

`Setup → Foundational → US1 → US2 → US3 → Polish`

P2/P3 的数据模型已在 Foundational 中建立，因此可以在人员充足时与 US1 并行开发；端到端演示仍按 P1 → P2 → P3 增量交付。

---

## Parallel Opportunities

### Setup

- T006、T007、T008、T009、T010 可并行。
- T011、T012 可在工程配置完成后并行。

### Foundational

- T020～T024 可按独立领域文件并行。
- T031～T033 可与 T034～T036 并行。
- T038～T040 可按测试框架并行。

### User Story 1

```text
Task: "Add failing integration tests for question versions in backend/java/src/test/java/com/certpractice/question/QuestionVersionIntegrationTest.java"
Task: "Add failing frontend unit tests for answer visibility in frontend/tests/unit/practice-session.spec.ts"
Task: "Implement catalog queries in backend/java/src/main/java/com/certpractice/catalog/CatalogService.java"
Task: "Implement login and registration pages in frontend/src/app/pages/LoginPage.vue"
```

### User Story 2

```text
Task: "Add failing discussion integration tests in backend/java/src/test/java/com/certpractice/community/DiscussionIntegrationTest.java"
Task: "Implement notification service in backend/java/src/main/java/com/certpractice/notification/NotificationService.java"
Task: "Implement notification center in frontend/src/app/pages/NotificationPage.vue"
Task: "Implement admin dashboard in frontend/src/admin/pages/DashboardPage.vue"
```

### User Story 3

```text
Task: "Add failing Python provider contract tests in backend/python/tests/test_provider_contract.py"
Task: "Implement internal chat endpoint in backend/python/app/api/chat.py"
Task: "Implement AI quota reservation in backend/java/src/main/java/com/certpractice/aigateway/AiQuotaService.java"
Task: "Implement self-test UI in frontend/src/app/pages/SelfTestPage.vue"
```

---

## Implementation Strategy

### MVP First

1. 完成 Phase 1 与 Phase 2。
2. 只实现 US1，交付 P1 核心刷题闭环。
3. 运行 V-01～V-10、V-16、V-17、V-18 及 P1 自动化测试。
4. 通过后作为首个可演示版本。

### Incremental Delivery

1. P1：题库、练习、填空自评、记录、收藏错题。
2. P2：社区、通知、运营管理、看板、审计。
3. P3：变式题、自测、AI 辅导、额度与成本控制。

每个阶段结束后保留独立验收记录，不跨阶段补测。

### Parallel Team Strategy

- 基础阶段由全员协作完成。
- 完成后，前端可按页面域并行；Java 可按 identity/question/practice 模块并行；Python 只由 AI 方向成员维护。
- P2/P3 并行时，共享实体和 Flyway 迁移需要先合并，避免迁移版本冲突。

---

## Notes

- 所有任务必须按顺序获得唯一 ID，完成后勾选并记录提交。
- `[P]` 仅表示文件级并行，不表示可跳过依赖。
- 安全字段裁剪测试不可延后到发布前。
- 测试必须先失败，再实现对应行为。
- 每个故事结束都必须在独立种子数据和独立测试入口下验证。
- 不得通过删除历史、关闭权限检查或直接修改数据库来绕过失败测试。

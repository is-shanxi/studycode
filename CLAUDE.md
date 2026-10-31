# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

"studycode" / cert-practice: a certification-exam practice platform in one repository with three runtime processes:

- `frontend/` — Vue 3 + TypeScript + Vite + Element Plus SPA, split into `/app` (users) and `/admin` (administrators).
- `backend/java/` — Java 21 + Spring Boot 3 modular monolith. The only writer of business data.
- `backend/python/` — Python 3.12 + FastAPI stateless AI service, reachable only from Java.
- PostgreSQL 16 via `infra/compose.yaml`.

The driving feature is `specs/001-cert-practice-platform/`. P1 (core practice loop) and P2 (community and operations) are implemented; P3 (self-test and AI tutoring) plus cross-cutting polish remain open in [tasks.md](specs/001-cert-practice-platform/tasks.md).

`plan.md` and `spec.md` link to `../../docs/技术设计文档.md`. Requirements and design docs live in `docs/` inside this repository and are versioned with the code. Treat `spec.md`, `plan.md`, `data-model.md`, `contracts/http-api.md`, the constitution, and the design/requirements docs as the source of truth. Each subproject has its own README with API and command notes.

## Architecture

### Request and data flow

Browser → Vue dev server (`:5173`) → Vite proxy for `/api` and `/actuator` → Java (`:8080`) → PostgreSQL. Java → Python (`:8000`) only over `/internal/v1/*` authenticated with the shared `X-Internal-Token`. The browser never calls Python; Python never touches business tables.

### Invariants from the constitution

Read `.specify/memory/constitution.md` before changing behavior. The clauses most often at risk:

1. **Single writer** — Java alone writes business data in PostgreSQL. Python returns AI results to Java for validation, persistence, and publication. Nothing may bypass Java authorization or transaction boundaries.
2. **Answer security** — responses for unsubmitted work must not contain `isCorrect`, `correctOptionId`, `referenceAnswer`, `fillReferenceAnswer`, `explanation`, or `aliases`. Practice/session responses expose them only through `PracticeDtoMapper.toResultView`, after submission. `AnswerVisibilityContractTest`, `P1RedactionTest`, and the frontend `containsAnswerLeak` guard cover this. Public search deliberately matches only stem/chapter/tag names; only admin search may match explanations, options, or aliases.
3. **Immutable history** — published question content is never edited in place. `QuestionVersion` rows are immutable once published; publishing moves `Question.publishedVersionId` and marks the previous version superseded. Practice items and discussions pin the exact version they used. Submitted answers, self-assessments, audit rows, and cost rows are never silently overwritten or physically deleted.
4. **Test-first** — add a failing automated test before implementing behavior. A failing security or consistency test blocks completion.
5. **Local simplicity** — one SPA, one Java service, one Python service, PostgreSQL. Redis, queues, search engines, Nginx, Kubernetes, or service decomposition require an explicit constitution amendment.

Every protected operation re-checks role and resource ownership on the server; frontend route guards are UX only.

### 2026-10-31 design decisions

- Existing P1/P2 modules may reuse other modules' repositories; new modules (`selftest`, `aigateway`) must go through public services or query ports, per the amended design doc.
- Passwords use BCrypt (cost 12); the design doc records this choice over Argon2id.
- `PracticeSession.Status.SUBMITTED` is a compatibility enum value only: submission writes `COMPLETED` (single-choice) or `PENDING_SELF_ASSESSMENT` (with fill items).
- Selection bucket/difficulty weights and the candidate cap are configured under `app.practice.selection`.
- Java tests are integration-first (Testcontainers); `TextNormalizer`, selection quota math, and configuration binding have focused unit tests.
- The practice page treats the server's `allowedActions` as the authority for submission; self-assessment requests send an `Idempotency-Key`.
- Requirements and design documents live in `docs/` inside this repository.
- Administrator catalog/subject/chapter/tag maintenance is deferred to `tasks.md` T165.

### Java backend (`com.certpractice`)

Packages are business domains, not layers: `identity` (users, roles, JDBC-backed sessions, CSRF, admin audit), `catalog`, `question` (search, immutable versions, import, lifecycle), `practice` (sessions, items, answers, self-assessment, favorites, wrong questions), `community` (discussions, comments, reports, moderation), `notification`, `admin` (user management, dashboard, audit queries), `aigateway` (AI boundary and usage/budget tables; P3), and `shared`.

Cross-cutting conventions:

- Throw `ApiException` with a stable `ErrorCode`; `GlobalExceptionHandler` and `ProblemDetailsWriter` render RFC 9457 `application/problem+json` including `code` and `traceId`. Add an `ErrorCode` constant for a new client-visible failure instead of ad-hoc messages/codes.
- API responses are allow-listed DTO records, never JPA entities.
- `X-Trace-Id` is accepted and returned per request (`TraceIdFilter`, MDC). Never log answers, prompts, passwords, or SQL parameters.
- `Idempotency-Key` is honored for session creation, submission, and self-assessment: same key with a different payload yields `IDEMPOTENCY_MISMATCH`; records are retained 24 hours.
- Spring Session JDBC with an HttpOnly `SESSION` cookie; CSRF uses the readable `XSRF-TOKEN` cookie echoed as `X-XSRF-TOKEN` on unsafe methods. `/api/v1/admin/**` requires `ROLE_ADMIN`.

### Frontend (`frontend/src`)

- `app/` and `admin/` are route/page trees; `features/` holds domain API clients and logic.
- `features/http/client.ts` is the single fetch wrapper: `/api/v1` base, CSRF bootstrap, trace header, `ApiError` parsed from Problem Details. Use it instead of `fetch`.
- `features/auth/store.ts` is the Pinia session store; route meta `requiresAuth` / `guestOnly` / `requiresAdmin` drives redirects only.
- `features/practice/session.ts` holds the answer-leak guard and session helpers.
- Alias `@` maps to `src`; TypeScript is strict with unused-local checks.

### AI service (`backend/python`)

`app/main.py` builds the FastAPI app. `/health/live` and `/health/ready` are public; `/internal/v1/chat` and `/internal/v1/generate-variants` require `X-Internal-Token` matching `JAVA_AI_SERVICE_TOKEN`. `app/providers/base.py` defines the provider protocol; the default `MockProvider` is deterministic and offline. The real provider adapter is still a P3 task. Keep the service stateless and free of database imports.

### Database and migrations

Flyway migrations live in `backend/java/src/main/resources/db/migration` (`V{n}__*.sql`); repeatable demo data lives in `db/seed/R__demo_*.sql`. Hibernate runs `ddl-auto: validate` and Flyway has `clean-disabled: true`, so never edit an applied migration — add the next numbered one. Java integration tests use a Testcontainers `postgres:16-alpine` instance (`TestDatabase`), so Docker must be running for `mvn test` and `mvn verify`.

## Commands

### First-time setup

```bash
cp infra/env/.env.example infra/env/.env.local
docker compose --env-file infra/env/.env.local -f infra/compose.yaml up -d postgres
```

Flyway applies schema and demo data when Java starts. Defaults in `application.yml` match `.env.local`; export variables only when overriding. Ports: frontend 5173, Java 8080, Python 8000, PostgreSQL 5432.

Local demo accounts: `admin` / `Admin@12345` (ADMIN), `student01` / `Student@12345` (USER).

### Frontend (run in `frontend/`)

```bash
pnpm install
pnpm dev               # http://localhost:5173
pnpm build             # vue-tsc --noEmit + vite build
pnpm lint
pnpm format:check
pnpm typecheck
pnpm test:unit
pnpm test:e2e:install  # first Playwright run only
pnpm test:e2e          # requires Java :8080 and PostgreSQL; Playwright starts Vite
```

- Single unit test: `pnpm test:unit -- tests/unit/practice-session.spec.ts`
- Single E2E test: `pnpm exec playwright test tests/e2e/p1-core-flow.spec.ts --project=chromium`

If `pnpm`/corepack is broken, use `npx --yes pnpm@10 <script>` (the fallback used by `scripts/check.ps1`) or call `npx vitest run`, `npx vue-tsc --noEmit`, `npx eslint .`, `npx prettier --check .` directly.

### Java backend (run in `backend/java/`)

```bash
./mvnw test                              # unit + integration tests; Docker required
./mvnw test -Dtest=PracticeIntegrationTest
./mvnw verify                            # adds Checkstyle (120 columns, import order, whitespace)
./mvnw spring-boot:run
```

On PowerShell use `.\mvnw.cmd`. Configuration comes from `DATABASE_*`, `SERVER_PORT`, `SESSION_*`, and `JAVA_AI_*` environment variables.

### Python AI service (run in `backend/python/`)

```bash
python -m venv .venv
python -m pip install -e ".[dev]"        # activate .venv first
python -m ruff check .
python -m ruff format --check .
python -m mypy app tests                 # strict
python -m pytest
python -m uvicorn app.main:app --port 8000 --reload
```

- Single test file: `python -m pytest tests/test_internal_api.py`
- Filter by name: `python -m pytest -k "internal_token"`

### Unified check (repository root, PowerShell)

```powershell
./scripts/check.ps1                    # frontend checks, mvn verify, ruff/mypy/pytest
./scripts/check.ps1 -IncludeE2E
./scripts/check.ps1 -SkipJava -SkipPython
```

## Working in this repository

- This project follows Spec Kit. The constitution is binding; feature artifacts live under `specs/001-cert-practice-platform/` (`spec.md`, `plan.md`, `data-model.md`, `contracts/`, `tasks.md`). Use the `/speckit-*` skills for specifying, planning, tasking, and implementing, and tick tasks in `tasks.md` as they complete.
- Add tests with the code: Java integration tests extend `IntegrationTestSupport` (real migrated PostgreSQL), frontend unit tests go in `tests/unit`, E2E journeys in `tests/e2e`, Python tests use the fixtures in `tests/conftest.py`.
- UI copy, specs, and READMEs are Chinese; Java/TypeScript code comments are English. Match the surrounding language.
- `spider/` and `sql/` are standalone data-collection/import scripts and a legacy raw schema for a separate question bank; they are not part of the running application or its Flyway history.
- History uses Conventional Commits with a scope, e.g. `feat(frontend): ...`, `fix(community): ...`, `test(e2e): ...`.

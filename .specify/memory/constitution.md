# Certification Practice Platform Constitution

## Core Principles

### I. Business Data Single Writer

The Java Spring Boot service MUST be the only writer of business data in PostgreSQL. The Python AI service MUST remain stateless with respect to business records and MUST return results to Java for validation, persistence, and publication. No browser client or AI service may bypass Java authorization or transaction boundaries.

### II. Answer and Access Security

Unsubmitted practice responses MUST NOT contain correct-option markers, reference answers, accepted aliases, or official explanations. Every protected operation MUST validate the authenticated role and resource ownership on the server. Passwords, secrets, and other users' private data MUST NOT appear in API responses or logs. Administrators MUST NOT impersonate users or delete their own accounts.

### III. Immutable History

Published question content MUST NOT be edited in place. Edits MUST create immutable versions, and practice sessions and discussions MUST retain the exact version they used. Already submitted answers, self-assessments, audit records, and cost records MUST NOT be silently overwritten or physically deleted by normal business operations.

### IV. Test-First Verification

Every user story MUST define a failing automated test before implementation. Identity and ownership checks, answer redaction, question versioning, draw de-duplication, fill self-assessment, wrong-question updates, AI publication gates, and quota concurrency MUST have automated coverage. A failing security or consistency test MUST block completion.

### V. Local Simplicity

The system MUST remain a modular monolith with one Vue application, one Java business service, one Python AI service, and PostgreSQL. Redis, message queues, Elasticsearch, Nginx, Kubernetes, and microservice decomposition MUST NOT be introduced unless a measured requirement justifies an explicit constitution amendment.

## Technology and Security Requirements

- Frontend: Vue 3 and TypeScript.
- Business backend: Java 21 and Spring Boot 3.
- AI backend: Python 3.12 and FastAPI.
- Database: PostgreSQL 16 with Flyway migrations.
- Local runtime: Docker Compose.
- Authentication: server-side session, HttpOnly Cookie, and CSRF protection.
- AI calls MUST use a restricted internal contract and MUST NOT directly access business tables.
- Public responses MUST be built from allow-listed DTOs rather than serialized persistence entities.

## Development Workflow and Quality Gates

1. Specify the requirement and acceptance scenario before implementation.
2. Record architecture and interface decisions in the feature design artifacts.
3. Add failing tests for each new behavior or defect.
4. Implement the smallest change that satisfies the tests.
5. Run frontend, Java, Python, and contract checks before declaring a story complete.
6. Record administrative actions and data-version changes in the audit log.
7. Any exception to this constitution requires a documented reason and explicit approval.

## Governance

This constitution supersedes conflicting implementation practices. Feature specifications, plans, tasks, contracts, and code MUST be checked against these principles. Amendments require a documented rationale, compatibility impact, and approval before implementation. Complexity that adds a runtime component or breaks a core principle MUST be rejected or explicitly justified rather than silently accepted.

**Version**: 1.0.0 | **Ratified**: 2026-10-23 | **Last Amended**: 2026-10-23

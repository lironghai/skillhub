# SkillHub Constitution

## Core Principles

### I. SkillHub Owns Business Governance

SkillHub is the source of truth for users, namespaces, skill metadata, skill versions, review, publishing, download, audit, and MCP catalog exposure. Runtime frameworks and external services may assist execution, but they must not bypass SkillHub authorization, visibility, lifecycle, or audit rules.

### II. Neutral Skill Package Protocol

Downloadable skill artifacts must remain compatible with the neutral `<skill>/SKILL.md` package structure. Platform or runtime-specific workspace files, session logs, memory, approvals, plans, credentials, and adapter metadata must not be included in distributed skill packages unless explicitly approved as part of the public skill protocol.

### III. Module Boundaries Before Convenience

New product capabilities should fit existing modular boundaries before changing shared domain code. Workflow orchestration belongs in application or dedicated feature modules; domain services own lifecycle rules; storage and external integrations stay behind explicit ports or adapters. Existing table changes require explicit data-impact review.

### IV. Verification Beyond Unit Tests

Meaningful user-facing or integration work requires layered verification: focused unit tests, integration or contract tests for changed APIs, frontend checks for UI behavior, and image-based service acceptance when the feature affects deployed behavior. A unit-test-only result is not enough for release readiness.

### V. Security, Audit, and Recoverability

Every write path must enforce authorization, path safety, idempotency where applicable, and auditable state transitions. External credentials and private user data must never leak into logs, public traces, downloadable packages, or model prompts beyond the minimum required runtime context.

## Technology and Architecture Constraints

- Backend: Spring Boot 3.x, JDK 21, Maven multi-module structure under `server/`.
- Frontend: React, TypeScript, Vite, Tailwind/shadcn-style components under `web/`.
- Persistence: PostgreSQL with Flyway migrations, Redis for session/cache/coordination, object storage via `ObjectStorageService`.
- Existing SkillHub documents under `docs/` remain source references and must not be silently overwritten.
- SDD artifacts live in `docs/specs/`; durable cross-feature facts live in `docs/knowledge/`.

## Development Workflow

1. Requirements, clarify, checklist, plan, tasks, and analyze must exist before feature implementation starts.
2. New dependencies, runtime services, migrations, security behavior, compatibility changes, or existing table modifications require explicit review.
3. Implementation tasks must include tests and image-based acceptance for deployable behavior.
4. After implementation batches, reconcile actual behavior with spec, plan, tasks, contracts, existing docs, and Repository Memory.

## Governance

This constitution supersedes feature-local convenience. Amendments require an explicit durable project-rule decision, updated documentation, and migration or compatibility notes when existing behavior is affected.

**Version**: 1.0.0 | **Ratified**: 2026-07-30 | **Last Amended**: 2026-07-30

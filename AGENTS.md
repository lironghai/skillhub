# SkillHub — AGENTS.md

**SkillHub** is an enterprise-grade, self-hosted agent skill registry. Frontend: React 19 + TypeScript + Vite + pnpm.

## Quick Reference

| Item       | Value                                          |
|------------|------------------------------------------------|
| Frontend   | React 19, TypeScript, Vite, pnpm               |
| Backend    | Spring Boot 3.2.3, Java 21                      |
| API Types  | `web/src/api/generated/schema.d.ts` (generated) |
| Dev URL    | `http://localhost:3000`                         |
| API URL    | `http://localhost:8080`                         |
| Build      | `make dev-all` (full stack), `make staging`     |

## Directory Map (web/)

```
web/
├── src/
│   ├── api/generated/schema.d.ts  # OpenAPI types (GENERATED — do not edit)
│   ├── app/                       # Router, layout, global providers
│   ├── entities/                  # Domain entity display (skill/, user/, namespace/)
│   ├── features/                  # Business modules
│   │   ├── admin/                 # Admin panel
│   │   ├── auth/                  # Login, OAuth
│   │   ├── governance/            # Skill governance
│   │   ├── namespace/             # Namespace management
│   │   ├── notification/          # User notifications
│   │   ├── promotion/             # Skill promotion workflows
│   │   ├── publish/               # Skill upload/publish
│   │   ├── report/                # Skill reporting
│   │   ├── review/                # Review workflow
│   │   ├── search/                # Skill search & filtering
│   │   ├── security-audit/        # Security audit viewer
│   │   ├── skill/                 # Skill detail, listing
│   │   ├── social/                # Stars, ratings, subscriptions
│   │   └── token/                 # API token management
│   ├── i18n/                      # Internationalization
│   ├── pages/                     # Route-level page components
│   ├── shared/
│   │   ├── components/            # Reusable UI components
│   │   ├── hooks/                 # Custom React hooks
│   │   ├── lib/utils.ts           # cn() class merging utility
│   │   └── ui/                    # Radix UI-based primitives
│   └── types/                     # Additional TypeScript types
├── e2e/                           # Playwright E2E tests
├── nginx.conf.template
├── Dockerfile                     # Multi-stage (Node → Nginx)
└── package.json
```

Backend (`server/`) is a Maven multi-module Spring Boot project (7 modules: app, domain, auth, search, storage, infra, notification). See `docs/` for architecture details.

## Dev Commands

```bash
make dev-all          # Start full stack (DB, Redis, MinIO, backend, frontend)
make dev-all-down     # Stop everything
make dev-server-restart  # Restart backend after Java changes
make generate-api     # Regenerate frontend API types from backend controllers
make typecheck-web    # TypeScript check
make lint-web         # ESLint
make test-frontend    # Vitest unit tests
make test-e2e-frontend # Playwright E2E
make staging          # Full pre-PR regression
```

**Local mock users** (dev only, use `X-Mock-User-Id` header):
- `local-user` (regular) / `local-admin` (super admin)
- Bootstrap admin: `admin` / `ChangeMe!2026`

## Critical Rules

- **Never manually edit** `web/src/api/generated/schema.d.ts` — run `make generate-api`
- API changes: edit backend controller → `make generate-api` → commit generated types
- Frontend: Vite HMR auto-reloads on edit
- Before PR: `make typecheck-web && make lint-web && make staging`

## Frontend Conventions

### State Management
- **TanStack Query**: all server state (API data fetching/caching)
- **Zustand**: local/UI state (theme, sidebar, modals)
- **Never** use `useEffect` for data fetching

### Component Composition
- **Radix UI** primitives for accessible components
- **class-variance-authority** (cva) for component variants
- **clsx** + **tailwind-merge** → `cn()` from `web/src/shared/lib/utils.ts`
- shadcn/ui is NOT used — Radix primitives + utility composition only

### Code Style
- Strict TypeScript. No `any`.
- Use generated OpenAPI types for all API interactions.
- Feature-Sliced Design: place code at the lowest appropriate layer.

## Key Backend Context (for frontend work)

### Skill States
`SkillVersionStatus`: DRAFT → SCANNING → SCAN_FAILED | UPLOADED → PENDING_REVIEW → PUBLISHED | REJECTED | YANKED

`SkillStatus`: ACTIVE, HIDDEN, ARCHIVED (HIDDEN is a governance overlay, not a lifecycle state)

### Skill Coordinate Format
`@{namespace_slug}/{skill_slug}` — e.g. `@global/my-skill`, `@my-team/my-skill`

### Auth
- Web: OAuth2 (GitHub) + local password
- CLI: OAuth Device Flow
- API tokens: prefix-based secure hashing

### Skill Package Upload Limits
- 10MB per file, 100MB total, 500 files max
- Root must contain `SKILL.md` with YAML frontmatter (`name`, `description` required)

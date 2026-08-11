# Tasks: Workbench Agent Runtime

**Input**: Design documents from `specs/001-workbench-agent-runtime/`

**Prerequisites**: `spec.md`, `clarifications.md`, `research.md`, `plan.md`, `data-model.md`, `contracts/workbench-api.md`

**Tests**: Required. This feature must not be accepted with unit tests only; image-based acceptance is mandatory.

## Phase 1: Runtime PoC and Setup

**Purpose**: Prove AgentScope Java can satisfy the required runtime primitives before product implementation.

- [x] T001 Create an isolated PoC branch or module for AgentScope Java dependency resolution in `server/pom.xml` or a disposable Maven module.
- [x] T002 Verify `RuntimeContext(userId, sessionId)` creation and adapter-level mapping.
- [x] T003 Verify session-scoped workspace isolation with two concurrent sessions.
- [x] T004 Verify file read/write tools are restricted to the session workspace.
- [x] T005 Verify mock MCP mutating and unknown tools can produce approval-required events.
- [x] T006 Document PoC result and selected AgentScope Java version in `specs/001-workbench-agent-runtime/research.md`.

**Checkpoint**: Do not proceed to full implementation if T001-T005 fail without a reviewed fallback.

---

## Phase 2: Backend Foundation

**Purpose**: Create workbench-owned persistence and service boundaries without changing existing skill tables.

- [x] T007 Add `server/skillhub-workbench` Maven module.
- [x] T008 Add Flyway migration for new workbench tables in `server/skillhub-app/src/main/resources/db/migration/`; do not alter existing skill tables.
- [x] T009 [P] Implement `WorkbenchSession` domain model and repository port.
- [x] T010 [P] Implement session event, file snapshot, MCP binding, tool approval, and publish candidate models plus repository ports.
- [x] T011 Add app-side adapter package under `server/skillhub-app/src/main/java/com/iflytek/skillhub/workbench/adapter/`; keep controllers transport/auth-only.
- [x] T012 Implement workbench authorization checks for user ownership and namespace membership. B2 note: app adapter now enforces target namespace membership, session ownership, source skill/version consistency, and existing `VisibilityChecker` read access for imports; no table changes.
- [x] T013 Implement source skill version import into a session workspace.
- [x] T014 Implement immutable baseline content snapshot creation with storage keys, retention metadata, and cleanup design. B2 note: immutable baseline snapshots, storage keys, retention metadata, and idempotent cleanup design are documented; scheduled cleanup job remains a later hardening task.
- [x] T015 Implement file list/read/write/delete with path normalization and query-parameter file APIs matching `/file?path=...`. B2 note: service-level file operations and `/api/web/workbench/sessions/{sessionId}/file?path=...` HTTP APIs are implemented; workspace writes/imports now apply the existing package file policy for extension, size, total count/size, and content signature checks.
- [x] T016 Implement diff generation against baseline.
- [x] T017 Add audit events for session creation, import, file edits, and status changes.
- [x] T018 Add backend unit and integration tests for T009-T017. B2 note: workbench module unit tests, app adapter/controller tests, and a Spring Boot MockMvc integration flow cover authorization denial, source version visibility/status, file query-path APIs, empty file writes, import, immutable baseline, path/package-policy rejection, file operations, diff, audit events, non-owner denial, and source version immutability. Image-based acceptance remains in Phase 7.

**Checkpoint**: Backend can create sessions, import a source version, edit files, and return a diff without invoking a model.

---

## Phase 3: Agent Runtime Adapter

**Purpose**: Wrap AgentScope Java behind a SkillHub-owned runtime port.

- [x] T019 Define `AgentRuntimePort` in the workbench module.
- [x] T020 Implement AgentScope Java adapter with `RuntimeContext(userId, sessionId)`.
- [x] T021 Wire session workspace location and session isolation.
- [x] T022 Convert runtime messages and file changes into `WorkbenchSessionEvent`.
- [x] T023 Add controlled cancellation and failure handling.
- [x] T024 Add adapter tests with a fake or mock model/runtime.

B3 note: AgentScope Java is wired behind `AgentRuntimePort`; SkillHub still owns session state,
workspace snapshots, event persistence, and authorization. The adapter currently creates
session-scoped AgentScope `RuntimeContext` and controlled runtime workspace directories, then
emits visible runtime events without invoking a real model executor. Runtime file-change mapping
is covered through fake runtime tests; a live AgentScope model runner that consumes user messages
and writes files remains a required follow-up before the checkpoint can be treated as product-ready.
Tests cover runtime context mapping, workspace isolation, runtime event/file mapping, failure,
non-fatal cancel rejection, single active run guard, cancellation, controller endpoints, and Spring
Boot MockMvc flow.

**Checkpoint**: Partially satisfied. Runtime events are visible through SkillHub APIs and file
changes are mapped through the runtime port in tests; live model execution is not yet connected.

---

## Phase 4: Frontend Workbench UI

**Purpose**: Provide the user-facing authoring, editing, and review surface.

- [x] T025 Add workbench routes and navigation entries.
- [x] T026 Build session creation form with create/update mode and source skill version selector.
- [x] T027 Build chat and runtime event stream panel.
- [x] T028 Build workspace file tree.
- [x] T029 Build file editor with save state and validation errors.
- [x] T029a Add binary-safe file read/write handling or explicitly restrict the workbench editor API to text files.
- [x] T030 Build diff review panel.
- [ ] T031 Build package preview and publish panel.
- [x] T032 Add frontend unit tests for form, editor, diff, and publish states.
- [x] T033 Run visual checks for desktop and common laptop widths.
- [ ] T033a Regenerate web API types for `/api/web/workbench/**` before frontend integration.

**Checkpoint**: A user can create/update files and review diff without MCP.

B4 note: `/dashboard/workbench` is wired into navigation and covers create/update session setup, source import trigger,
workspace file list, UTF-8 text editor/save, message run/cancel controls, event timeline, and file-level diff review.
The editor deliberately blocks non-text files in the browser. Phase 4 keeps publish as a review placeholder because
package preview/publish is owned by Phase 6; generated OpenAPI types for the new workbench endpoints remain a follow-up.
Frontend review fixes covered stale file/session mutation results, source-import editor reset, query/mutation error states,
backend text-extension alignment, and visible enum localization.
Follow-up review warnings were fixed by scoping stale mutation errors to the matching session/file/run and disabling
mutating actions when a session fails to load.

---

## Phase 5: MCP Selection and Human Approval

**Purpose**: Allow selected MCP catalog entries through the workbench runtime adapter while preserving explicit approval for high-risk calls.

- [x] T034 Add workbench MCP catalog endpoint using existing SkillHub MCP visibility rules.
- [x] T035 Add session MCP binding persistence and UI selector.
- [x] T036 Define `WorkbenchMcpCatalogPort` and app-side adapter over the existing MCP catalog service.
- [ ] T037 Implement `WorkbenchMcpRuntimeAdapter` to translate selected catalog entries into AgentScope runtime configuration without exposing credentials.
- [x] T038 Add workbench-owned tool risk policy for read-only, mutating, unknown, and denied tools.
- [x] T039 Implement approval-required event persistence.
- [x] T040 Build approval prompt UI.
- [ ] T041 Implement approve/reject endpoints and runtime resume behavior. Phase 5 note: approve/reject endpoints and session status transitions are implemented; approve returns waiting sessions to runnable state, while reject and runtime DENIED decisions fail the session to avoid an indefinite waiting state. Pending approvals block ready-for-review transitions. Live AgentScope tool-call resume remains blocked on T037.
- [ ] T042 Add tests proving rejected high-risk calls do not execute and unknown MCP tools require approval by default. Phase 5 note: service/controller/UI tests cover unknown-risk approval creation, nested payload and error-message redaction, one-time decisions, invalid/disabled MCP rejection, rejection failure state, and mutation-state gates. Real tool non-execution remains blocked on T037.

Phase 5 note: the delivered slice is a workbench-owned MCP metadata and human-approval loop. The API/UI intentionally do not expose runtime endpoints or credentials. Workbench bindings now resolve selected MCP servers through a server-side catalog port instead of trusting client-supplied refs. Runtime translation into AgentScope MCP configuration remains a follow-up under T037.

**Checkpoint**: Partially satisfied. Mock MCP approval and rejection pass end-to-end; production runtime MCP execution is not connected yet.

---

## Phase 6: Package Preview and Publish

**Purpose**: Convert confirmed workspace content into a new SkillHub version through the existing lifecycle.

- [ ] T043 Implement package candidate builder with explicit workbench allowlist and runtime artifact exclusion before existing validation.
- [ ] T044 Return package preview `includedFiles`, `excludedFiles`, validation messages, and package fingerprint.
- [ ] T045 Persist preview metadata or recompute included/excluded file lists from the stored fingerprint before publish confirmation.
- [ ] T046 Reuse existing package validation policy for preview after workbench filtering.
- [ ] T047 Reuse existing SkillHub publish flow to create a new version.
- [ ] T048 Ensure update sessions never mutate source version files.
- [ ] T049 Add tests for invalid package, runtime artifact exclusion, duplicate version conflict, and old-version immutability.

**Checkpoint**: Create and update flows publish valid new versions and downloads remain neutral.

---

## Phase 7: Acceptance and Release Readiness

**Purpose**: Verify the complete feature under deployable service conditions.

- [ ] T050 Run backend test suite relevant to workbench and skill publish/download.
- [ ] T051 Run frontend unit tests.
- [ ] T052 Run Playwright workbench flows for create, update, MCP approval, package preview, publish, and download.
- [ ] T053 Capture desktop and laptop visual evidence for chat/events, file tree/editor, diff review, approval prompt, package preview, and publish panel.
- [ ] T054 Build server and web Docker images.
- [ ] T055 Start image-based acceptance environment and verify health endpoints.
- [ ] T056 Execute image-based acceptance flows and capture evidence.
- [ ] T057 Run documentation drift review and update `spec.md`, `plan.md`, `tasks.md`, contracts, and knowledge if behavior changed.
- [ ] T058 Run Repository Memory link validation.

Phase 5 acceptance evidence captured on 2026-07-31:
backend focused tests passed with 54 checks across workbench domain, application service, controller, MCP catalog, and integration flow;
frontend tests passed with 42 assertions across workbench, API client, and MCP marketplace coverage; typecheck,
lint, and production build passed. Server and web Docker images were built; because Docker Hub DNS was unavailable
for `node:22-alpine` during the final rebuild, the final web image was repackaged from the verified local production
`dist` output on the cached `nginx:alpine` runtime image. Image services started with PostgreSQL and Redis,
Flyway validated 44 migrations, and only new `workbench_*` tables were created with no database foreign keys.
Chrome checks against the web image confirmed the MCP selector and pending approval prompt render on desktop/mobile,
waiting-approval sessions disable message sending, MCP binding edits, and ready-for-review actions, file sizes render as
`128 B` instead of `NaN`, and runtime endpoint or secret-like fields are not displayed. Server-side mutation guards keep
MCP binding edits and source imports draft-only, file edits limited to draft/running workspaces, and waiting-approval
sessions blocked from direct ready-for-review promotion. Runtime `ERROR` event payloads now recursively sanitize text leaves
so message/detail/cause fields cannot persist token-like values or internal URLs.

## Dependencies & Execution Order

- Phase 1 blocks all implementation.
- Phase 2 blocks model runtime integration and UI publish flow.
- Phase 3 can run after Phase 2 service contracts are stable.
- Phase 4 can start after API contracts are stable, with mock backend data.
- Phase 5 depends on Phase 3 runtime adapter and Phase 4 event UI.
- Phase 6 depends on Phase 2 file/diff foundation and existing publish services.
- Phase 7 depends on all selected MVP phases.

## Parallel Opportunities

- T009 and T010 can run in parallel after table design is agreed.
- Frontend visual components can run in parallel with backend adapter work once API contracts are stable.
- Package preview tests and MCP approval tests can be developed independently after backend foundations exist.

## Implementation Strategy

1. Prove AgentScope Java integration first.
2. Build backend session/workspace without model dependency.
3. Add runtime adapter behind a port.
4. Add UI on stable APIs.
5. Add MCP/HITL.
6. Reuse existing publish flow.
7. Accept only after image-based service testing.

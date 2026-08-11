# Feature Specification: Workbench Agent Runtime

**Feature Branch**: `001-workbench-agent-runtime`

**Created**: 2026-07-30

**Status**: Draft for review

**Input**: User description: "P3 workbench feature: support model-assisted skill creation and update through an online workspace; allow selecting an existing SkillHub skill version and importing it into the user's session directory; allow the model and user to create or edit files; require user review, manual edits, and diff confirmation; allow selected SkillHub MCP usage with approval for high-risk calls; after confirmation, publish through the existing flow as a new skill version; evaluate AgentScope Java and DeepAgents while keeping downloaded packages neutral with the `<skill>/SKILL.md` structure."

## User Scenarios & Testing

### User Story 1 - Create a skill in a workbench session (Priority: P1)

A logged-in developer opens the workbench, starts a new skill creation session, chats with the model, watches generated files appear in an isolated workspace, edits files when needed, and reviews a diff before publishing the result as a new SkillHub skill version.

**Why this priority**: This is the smallest complete value slice: model-assisted authoring, user review, and reuse of existing SkillHub publishing.

**Independent Test**: Start a new session in a test namespace, generate `SKILL.md`, edit it in the workbench, preview the package, publish it, and verify that a new version appears in SkillHub and can be downloaded as a neutral zip.

**Acceptance Scenarios**:

1. **Given** a logged-in namespace member, **When** they create a new workbench session and ask the model to create a skill, **Then** the session owns an isolated workspace and shows generated files without exposing other sessions.
2. **Given** generated files exist, **When** the user edits `SKILL.md` in the workbench editor, **Then** the diff preview reflects both model and user edits.
3. **Given** the user confirms the diff, **When** they publish, **Then** SkillHub creates a new skill version through the normal review or direct-publish lifecycle.

---

### User Story 2 - Update an existing skill version (Priority: P1)

A skill owner chooses an existing skill and version, imports that exact version into a workbench session, asks the model to update it, reviews the diff, and publishes the result as a new version without mutating the original version.

**Why this priority**: Updating existing skills is central to the workbench value and enforces the user-approved rule that updates must always generate a new version.

**Independent Test**: Select a published skill version, import it into a session, modify `SKILL.md`, publish with a new version number, and verify the original version remains downloadable and unchanged.

**Acceptance Scenarios**:

1. **Given** a user can manage a skill, **When** they start an update session, **Then** they must choose a concrete source skill version.
2. **Given** a source version was imported, **When** the model changes files, **Then** the diff is computed against the imported snapshot.
3. **Given** the user publishes the result, **When** SkillHub persists it, **Then** it creates a distinct `skill_version` and does not change source version files or package bytes.

---

### User Story 3 - Use Selectable MCP Catalog Entries with User Approval (Priority: P2)

A developer selects one or more MCP catalog entries exposed through the workbench runtime adapter for a workbench session. The model can call allowed tools, but high-risk calls pause for explicit user approval or rejection.

**Why this priority**: Workbench skill creation may need access to internal tools, but tool execution must remain governed and auditable.

**Independent Test**: Bind a mock MCP server exposing one read-only tool and one mutating tool, verify read-only calls can complete according to policy, verify mutating calls create a pending approval, and verify approve/reject changes execution.

**Acceptance Scenarios**:

1. **Given** a selected MCP catalog entry, **When** the session starts, **Then** only the selected tools are exposed through the workbench runtime adapter.
2. **Given** a high-risk tool call is requested, **When** the approval prompt appears, **Then** the call does not execute until the user approves.
3. **Given** the user rejects the tool call, **When** the session resumes, **Then** the model receives the rejection result and no external side effect is performed.

---

### User Story 4 - Validate and package the workspace (Priority: P2)

A developer previews the final package before publish. SkillHub validates package structure, excludes runtime artifacts, and shows blocking validation errors before creating a new version.

**Why this priority**: The workbench must not weaken existing package compatibility or publish controls.

**Independent Test**: Create workspace files including allowed skill files and runtime-only files, preview packaging, verify runtime-only files are excluded, and verify invalid packages block publishing.

**Acceptance Scenarios**:

1. **Given** the workspace contains runtime files, **When** package preview runs, **Then** those files are excluded.
2. **Given** `SKILL.md` is missing or invalid, **When** the user tries to publish, **Then** publishing is blocked with a clear validation message.
3. **Given** validation passes, **When** the package is published, **Then** existing SkillHub package validation, storage, review, search indexing, and download behavior are reused.

## Edge Cases

- User starts a session but never publishes: session remains draft/expired and no skill version is created.
- User selects a source skill version they can view but not manage: import may be allowed only if product later enables fork; MVP blocks update publish.
- Source skill is archived or namespace is frozen after session creation: publish is revalidated and blocked by existing SkillHub lifecycle rules.
- Two workbench sessions publish the same target version: existing `(skill_id, version)` uniqueness rejects the duplicate and the user must choose another version.
- MCP server disappears or credentials become invalid during a session: the session surfaces a recoverable tool error and does not mark the workspace as published.
- Model attempts path traversal or writes outside workspace: operation is rejected and audited.
- Generated content contains disallowed file types or oversized files: package preview blocks publish before SkillHub version creation.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST allow logged-in users to create a workbench session for either a new skill or an update to a selected existing skill version.
- **FR-002**: The system MUST map each workbench session to an agent runtime context containing the SkillHub user id and workbench session id.
- **FR-003**: The system MUST isolate editable workspace files per workbench session by default.
- **FR-004**: The system MUST import all files from a selected source skill version into the session workspace before model edits begin.
- **FR-005**: The system MUST preserve an immutable baseline snapshot for every imported or initialized workspace so diff review is computed against a stable source.
- **FR-006**: Users MUST be able to view, edit, create, and delete workspace files through the workbench UI subject to package path and file policy.
- **FR-007**: The system MUST stream model messages, file changes, tool-call events, and approval-required events to the workbench UI.
- **FR-008**: The system MUST allow users to select MCP catalog entries exposed through the workbench runtime adapter for a session without exposing MCP credentials in workspace files or downloadable packages.
- **FR-009**: The system MUST require explicit user approval before executing high-risk tool calls.
- **FR-010**: The system MUST generate a package preview that includes only neutral skill package files and excludes runtime artifacts.
- **FR-011**: The system MUST validate package preview output with the existing SkillHub package rules before publish.
- **FR-012**: The system MUST publish confirmed workspace output as a new skill version through the existing SkillHub publish/review lifecycle.
- **FR-013**: The system MUST never mutate existing published version files when updating a skill.
- **FR-014**: The system MUST record audit events for session creation, source import, file edits, MCP selection, approval decisions, package preview, and publish submission.
- **FR-015**: The system MUST show clear recoverable errors for runtime failures, validation failures, permission failures, and publish conflicts.

### Key Entities

- **Workbench Session**: User-owned editing session with source mode, status, workspace key, selected model/runtime, and optional source skill version.
- **Workspace File Snapshot**: Baseline and current file metadata used for diff, validation, and package preview.
- **Workbench Message/Event**: User, model, file-change, tool-call, and status events rendered in the UI.
- **Tool Approval**: Pending or completed user decision for a runtime tool call.
- **MCP Binding**: Session-scoped reference to selected MCP catalog entries and permitted tools exposed through the workbench runtime adapter.
- **Publish Candidate**: Confirmed workspace package metadata prepared for existing SkillHub publish flow.

## Success Criteria

### Measurable Outcomes

- **SC-001**: A user can create a valid skill from an empty session, review the diff, publish it, and download the resulting zip in one end-to-end acceptance run.
- **SC-002**: A user can update an existing skill version and verify the original version download remains byte-for-byte unchanged.
- **SC-003**: Approval-required tool calls cannot execute before a recorded user approval in functional acceptance tests.
- **SC-004**: Package preview excludes runtime artifacts in 100% of tested generated packages.
- **SC-005**: The workbench UI supports the primary authoring workflow without visual overlap or unusable controls at desktop and common laptop widths.
- **SC-006**: Release readiness requires container-image based service testing, not only unit tests.

## Assumptions

- AgentScope Java is the preferred runtime candidate for the MVP because it aligns with the Java service stack and has official session, workspace, permission, MCP, and sandbox concepts; implementation is gated by a local PoC.
- DeepAgents remains a reference alternative, but adopting it would imply a Python runtime service boundary that is not required for the MVP.
- The MVP uses a dedicated SkillHub workbench module and new workbench-owned tables; it does not alter existing `skill`, `skill_version`, or `skill_file` table structure.
- Existing SkillHub publish, review, package validation, storage, and download services are reused instead of reimplemented.
- Workbench package output remains neutral and does not write or require `AGENTS.md` changes for downloaded skills.

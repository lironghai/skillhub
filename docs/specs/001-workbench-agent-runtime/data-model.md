# Data Model: Workbench Agent Runtime

## WorkbenchSession

Represents a user-owned workbench conversation and editable workspace.

Fields:

- `id`: stable session id
- `user_id`: SkillHub user id
- `namespace_id`: target namespace
- `mode`: `CREATE_SKILL` or `UPDATE_SKILL`
- `source_skill_id`: nullable for create
- `source_version_id`: nullable for create; required for update
- `target_slug`: desired skill slug
- `target_version`: desired new version
- `runtime_provider`: initially `AGENTSCOPE_JAVA`
- `isolation_scope`: initially `SESSION`
- `workspace_key`: server-generated workspace locator
- `status`: `DRAFT`, `RUNNING`, `WAITING_APPROVAL`, `READY_FOR_REVIEW`, `PUBLISHING`, `PUBLISHED`, `FAILED`, `CANCELLED`, `EXPIRED`
- `created_at`, `updated_at`, `expires_at`

Rules:

- A session belongs to one user.
- Updating an existing skill requires a concrete source version.
- Publish revalidates user permission and namespace/skill status.

## WorkbenchSessionEvent

Append-only event record for UI replay and audit support.

Fields:

- `id`
- `session_id`
- `event_type`: `USER_MESSAGE`, `MODEL_MESSAGE`, `FILE_CHANGED`, `TOOL_CALL`, `APPROVAL_REQUIRED`, `APPROVAL_DECIDED`, `STATUS_CHANGED`, `ERROR`
- `payload_json`
- `created_at`

Rules:

- Do not store secrets or raw MCP credentials.
- Large model/tool payloads may be summarized or stored through a controlled artifact path.

## WorkbenchFileSnapshot

Tracks baseline and current file metadata plus immutable content references for diff and package preview.

Fields:

- `id`
- `session_id`
- `snapshot_type`: `BASELINE` or `CURRENT`
- `file_path`
- `content_storage_key`
- `sha256`
- `size_bytes`
- `content_type`
- `retention_until`
- `created_at`

Rules:

- Baseline is immutable after session initialization.
- Baseline content is retained separately from the mutable workspace so content-level diff can be produced even after later edits.
- Current snapshot is refreshed after file edits or before diff/package preview.
- Expired sessions and stored snapshots are cleaned through an explicit retention job.
- Cleanup design: a scheduled workbench cleanup process selects non-terminal sessions whose `expires_at` has passed, marks them `EXPIRED`, then removes storage objects referenced by `workbench_file_snapshot.content_storage_key` after `retention_until`. The job must use the workbench repository/storage ports, process bounded batches, and be idempotent so missing storage objects do not fail the run. No database foreign keys are required; logical references are validated by the workbench service before mutation.

## WorkbenchMcpBinding

Session-scoped MCP catalog selection.

Fields:

- `id`
- `session_id`
- `mcp_server_id`
- `catalog_source`: `CONTEXT_FORGE`, `SKILLHUB_READONLY`, or later source types
- `runtime_endpoint_ref`
- `enabled_tools_json`
- `disabled_tools_json`
- `tool_policy_json`
- `policy_version`
- `status`
- `created_at`

Rules:

- Existing SkillHub MCP catalog APIs provide metadata; runtime connection configuration is created by the workbench runtime adapter.
- Credentials are referenced from SkillHub-managed credential storage or runtime secret resolution, never copied into workspace files.
- Tool risk policy belongs to workbench configuration and audit; do not assume catalog metadata alone is sufficient.

## WorkbenchToolApproval

User decision record for approval-required tool calls.

Fields:

- `id`
- `session_id`
- `event_id`
- `tool_name`
- `mcp_server_id`
- `risk_level`
- `arguments_redacted_json`
- `status`: `PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`
- `decision_by`
- `decision_at`
- `created_at`

Rules:

- Tool execution waits while status is `PENDING`.
- Rejected calls resume the model with a rejection result and perform no side effect.

## WorkbenchPublishCandidate

Represents a confirmed workspace package ready for existing SkillHub publish flow.

Fields:

- `id`
- `session_id`
- `package_fingerprint`
- `file_count`
- `total_size`
- `validation_status`
- `validation_report_json`
- `skill_version_id`: set after publish succeeds
- `created_at`

Rules:

- Candidate package excludes runtime artifacts.
- Included and excluded file lists are generated preview output and must either be persisted in `validation_report_json` or recomputed from the stored `package_fingerprint` before publish confirmation.
- Publishing creates a new SkillHub version and records its id.

-- Workbench-owned persistence for online skill authoring sessions.
-- This migration creates only new workbench tables and does not alter existing
-- skill, skill_version, or skill_file definitions. Cross-table ids below are
-- logical references; workbench integrity is enforced by application ports and
-- cleanup jobs instead of database constraints to keep existing data lifecycle
-- independent from workbench drafts.

CREATE TABLE workbench_session (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(128) NOT NULL,
    namespace_id BIGINT NOT NULL,
    mode VARCHAR(32) NOT NULL,
    source_skill_id BIGINT,
    source_version_id BIGINT,
    target_slug VARCHAR(128) NOT NULL,
    target_version VARCHAR(64) NOT NULL,
    runtime_provider VARCHAR(64) NOT NULL DEFAULT 'AGENTSCOPE_JAVA',
    isolation_scope VARCHAR(32) NOT NULL DEFAULT 'SESSION',
    workspace_key VARCHAR(512) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_workbench_session_mode
        CHECK (mode IN ('CREATE_SKILL', 'UPDATE_SKILL')),
    CONSTRAINT chk_workbench_session_runtime_provider
        CHECK (runtime_provider IN ('AGENTSCOPE_JAVA')),
    CONSTRAINT chk_workbench_session_isolation_scope
        CHECK (isolation_scope IN ('SESSION')),
    CONSTRAINT chk_workbench_session_status
        CHECK (status IN (
            'DRAFT',
            'RUNNING',
            'WAITING_APPROVAL',
            'READY_FOR_REVIEW',
            'PUBLISHING',
            'PUBLISHED',
            'FAILED',
            'CANCELLED',
            'EXPIRED'
        )),
    CONSTRAINT chk_workbench_session_create_source
        CHECK (
            (mode = 'CREATE_SKILL' AND source_skill_id IS NULL AND source_version_id IS NULL)
            OR
            (mode = 'UPDATE_SKILL' AND source_skill_id IS NOT NULL AND source_version_id IS NOT NULL)
        )
);

CREATE INDEX idx_workbench_session_user_status
    ON workbench_session(user_id, status, updated_at DESC);

CREATE INDEX idx_workbench_session_namespace_status
    ON workbench_session(namespace_id, status, updated_at DESC);

CREATE INDEX idx_workbench_session_source_version
    ON workbench_session(source_version_id)
    WHERE source_version_id IS NOT NULL;

CREATE TABLE workbench_session_event (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_workbench_session_event_type
        CHECK (event_type IN (
            'AUDIT',
            'USER_MESSAGE',
            'MODEL_MESSAGE',
            'FILE_CHANGED',
            'TOOL_CALL',
            'APPROVAL_REQUIRED',
            'APPROVAL_DECIDED',
            'STATUS_CHANGED',
            'ERROR'
        ))
);

CREATE INDEX idx_workbench_session_event_session_id
    ON workbench_session_event(session_id, id);

CREATE INDEX idx_workbench_session_event_created_at
    ON workbench_session_event(session_id, created_at);

CREATE TABLE workbench_file_snapshot (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    snapshot_type VARCHAR(32) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    content_storage_key VARCHAR(512) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_type VARCHAR(128),
    retention_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_workbench_file_snapshot_type
        CHECK (snapshot_type IN ('BASELINE', 'CURRENT')),
    CONSTRAINT chk_workbench_file_snapshot_size
        CHECK (size_bytes >= 0),
    CONSTRAINT uq_workbench_file_snapshot_path
        UNIQUE (session_id, snapshot_type, file_path)
);

CREATE INDEX idx_workbench_file_snapshot_session_type
    ON workbench_file_snapshot(session_id, snapshot_type);

CREATE TABLE workbench_mcp_binding (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    mcp_server_id VARCHAR(128) NOT NULL,
    catalog_source VARCHAR(32) NOT NULL,
    runtime_endpoint_ref VARCHAR(512) NOT NULL,
    enabled_tools_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    disabled_tools_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    tool_policy_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    policy_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_workbench_mcp_binding_catalog_source
        CHECK (catalog_source IN ('CONTEXT_FORGE', 'SKILLHUB_READONLY')),
    CONSTRAINT chk_workbench_mcp_binding_status
        CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT uq_workbench_mcp_binding_server
        UNIQUE (session_id, catalog_source, mcp_server_id)
);

CREATE INDEX idx_workbench_mcp_binding_session_status
    ON workbench_mcp_binding(session_id, status);

CREATE TABLE workbench_tool_approval (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    tool_name VARCHAR(256) NOT NULL,
    mcp_server_id VARCHAR(128),
    risk_level VARCHAR(32) NOT NULL,
    arguments_redacted_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    decision_by VARCHAR(128),
    decision_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_workbench_tool_approval_risk
        CHECK (risk_level IN ('READ_ONLY', 'MUTATING', 'UNKNOWN', 'DENIED')),
    CONSTRAINT chk_workbench_tool_approval_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT chk_workbench_tool_approval_decision
        CHECK (
            (status = 'PENDING' AND decision_by IS NULL AND decision_at IS NULL)
            OR
            (status <> 'PENDING' AND decision_at IS NOT NULL)
        )
);

CREATE INDEX idx_workbench_tool_approval_session_status
    ON workbench_tool_approval(session_id, status, created_at DESC);

CREATE INDEX idx_workbench_tool_approval_event
    ON workbench_tool_approval(event_id);

CREATE TABLE workbench_publish_candidate (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    package_fingerprint VARCHAR(128) NOT NULL,
    file_count INT NOT NULL,
    total_size BIGINT NOT NULL,
    validation_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    visibility VARCHAR(32) NOT NULL DEFAULT 'PRIVATE',
    validation_report_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    skill_version_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_workbench_publish_candidate_counts
        CHECK (file_count >= 0 AND total_size >= 0),
    CONSTRAINT chk_workbench_publish_candidate_status
        CHECK (validation_status IN ('PENDING', 'PASSED', 'FAILED', 'PUBLISHED')),
    CONSTRAINT chk_workbench_publish_candidate_visibility
        CHECK (visibility IN ('PUBLIC', 'NAMESPACE_ONLY', 'PRIVATE')),
    CONSTRAINT chk_workbench_publish_candidate_published
        CHECK (
            (validation_status = 'PUBLISHED' AND skill_version_id IS NOT NULL)
            OR
            (validation_status <> 'PUBLISHED' AND skill_version_id IS NULL)
        )
);

CREATE INDEX idx_workbench_publish_candidate_session_created
    ON workbench_publish_candidate(session_id, created_at DESC);

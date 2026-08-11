# Contract: Workbench API

Base path: `/api/web/workbench`

All endpoints require an authenticated SkillHub web session.

## Runtime Config

`GET /runtime-config`

Returns non-secret runtime configuration state for the workbench UI. This does not expose API keys or credential values. The current MVP creates AgentScope session/workspace context; live model execution remains disabled unless a real executor is wired behind `AgentRuntimePort`.

Response:

```json
{
  "modelExecutorEnabled": false,
  "modelConfigured": false,
  "modelProvider": null,
  "modelName": null,
  "modelBaseUrlConfigured": false,
  "displayStatus": "MODEL_NOT_CONFIGURED",
  "message": "模型执行器未配置；当前只创建 AgentScope 会话上下文，不会调用真实模型。"
}
```

Config keys:

- `SKILLHUB_WORKBENCH_MODEL_EXECUTOR_ENABLED`
- `SKILLHUB_WORKBENCH_MODEL_PROVIDER`
- `SKILLHUB_WORKBENCH_MODEL_NAME`
- `SKILLHUB_WORKBENCH_MODEL_BASE_URL`

## Create Session

`POST /sessions`

Request:

```json
{
  "namespace": "team-a",
  "mode": "UPDATE_SKILL",
  "sourceSkill": {
    "namespace": "team-a",
    "slug": "report-helper",
    "version": "1.2.0"
  },
  "targetVersion": "1.3.0"
}
```

Response:

```json
{
  "id": "wb_123",
  "status": "DRAFT",
  "workspace": {
    "fileCount": 3
  }
}
```

## Get Session

`GET /sessions/{sessionId}`

Returns session status, source skill summary, selected MCP bindings, workspace summary, pending approvals, and publish readiness.

## Send Message

`POST /sessions/{sessionId}/messages`

Request:

```json
{
  "content": "Update this skill so it can filter report output by department."
}
```

Response:

```json
{
  "accepted": true,
  "sessionStatus": "RUNNING"
}
```

## Events

`GET /sessions/{sessionId}/events`

Streams or polls ordered events:

```json
{
  "eventId": "evt_1",
  "type": "APPROVAL_REQUIRED",
  "createdAt": "2026-07-30T10:00:00Z",
  "payload": {
    "approvalId": "appr_1",
    "toolName": "update_ticket",
    "riskLevel": "HIGH"
  }
}
```

## Files

- `GET /sessions/{sessionId}/files`
- `GET /sessions/{sessionId}/file?path=SKILL.md`
- `PUT /sessions/{sessionId}/file?path=SKILL.md`
- `DELETE /sessions/{sessionId}/file?path=SKILL.md`

Rules:

- `path` is a workspace-relative query parameter so nested paths do not conflict with routing.
- Path traversal is rejected.
- Writes are limited by package policy and session permissions.

## Diff

`GET /sessions/{sessionId}/diff`

Returns file-level and content-level diff against baseline snapshot.

## MCP Bindings

`GET /sessions/{sessionId}/mcp-catalog`

Returns MCP catalog entries the user may select. This endpoint exposes catalog metadata only; runtime connection, credential resolution, and approval policy are handled by the workbench runtime adapter.

Response:

```json
{
  "servers": [
    {
      "id": "mcp-1",
      "name": "ticket-tools",
      "catalogSource": "CONTEXT_FORGE",
      "runtimeCandidate": true,
      "tools": [
        {
          "name": "search_ticket",
          "declaredRisk": "READ_ONLY"
        },
        {
          "name": "update_ticket",
          "declaredRisk": "UNKNOWN"
        }
      ]
    }
  ]
}
```

`POST /sessions/{sessionId}/mcp-bindings`

Updates selected MCP catalog entries and tool filters for the session.

## Tool Approval

- `POST /sessions/{sessionId}/approvals/{approvalId}/approve`
- `POST /sessions/{sessionId}/approvals/{approvalId}/reject`

Response:

```json
{
  "approvalId": "appr_1",
  "status": "APPROVED"
}
```

## Package Preview

`POST /sessions/{sessionId}/package-preview`

Returns included files, excluded runtime files, validation report, package fingerprint, and publish readiness.

Response:

```json
{
  "packageFingerprint": "sha256:...",
  "readyToPublish": true,
  "includedFiles": [
    {
      "path": "SKILL.md",
      "sizeBytes": 1024,
      "sha256": "..."
    }
  ],
  "excludedFiles": [
    {
      "path": "AGENTS.md",
      "reason": "WORKBENCH_RUNTIME_ARTIFACT"
    }
  ],
  "validation": {
    "status": "PASS",
    "messages": []
  }
}
```

## Publish

`POST /sessions/{sessionId}/publish`

Request:

```json
{
  "confirmPackageFingerprint": "sha256:...",
  "visibility": "PUBLIC"
}
```

Response:

```json
{
  "skillId": 456,
  "skillVersionId": 789,
  "namespace": "team-a",
  "slug": "report-helper",
  "version": "1.3.0",
  "status": "PENDING_REVIEW"
}
```

# Research: Workbench Agent Runtime

**Created**: 2026-07-30
**Spec**: `specs/001-workbench-agent-runtime/spec.md`

## Framework Feasibility

### AgentScope Java

AgentScope Java is the preferred P3 runtime candidate, pending a local SkillHub PoC.
Official docs and source show the required primitives, but dependency compatibility,
adapter API shape, sandbox configuration, and production packaging are not locked
until Phase P3-0 passes.

Evidence from official sources:

- Official README lists workspace and sandbox, permission system, HITL, multi-tenant isolation, and session recovery.
- Official examples use `RuntimeContext.builder().sessionId(...).userId(...)`.
- Workspace docs define `IsolationScope.SESSION` and `IsolationScope.USER`, and state that `AgentState` is keyed by `(userId, sessionId)`.
- Workspace docs define `tools.json` with `mcpServers`.
- Skill docs define a skill as a directory with required `SKILL.md` and optional resources, and support workspace skills under `workspace/skills/<skill>/SKILL.md` and per-user overrides.
- Official source contains runtime and integration entry points including `RuntimeContext`, permission classes, file tools, `McpClientManager`, and `McpTool`.

Official links:

- `https://github.com/agentscope-ai/agentscope-java/blob/main/README.md`
- `https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/en/docs/harness/workspace.md`
- `https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/en/docs/harness/skill.md`
- `https://java.agentscope.io/v2/en/docs/building-blocks/permission-system.html`

## P3-0 PoC Result

**Result**: PASS with documented adapter limitation.

**Selected dependency**:

- `io.agentscope:agentscope-harness:2.0.0`
- Resolved transitive core dependency: `io.agentscope:agentscope-core:2.0.0`

**Implementation shape**:

- Added disposable Maven module `server/skillhub-workbench-poc`.
- Added `workbench-poc` Maven profile in `server/pom.xml`; the PoC module is included only when `-Pworkbench-poc` is active.
- Added minimal adapter-level PoC classes for SkillHub user/session to AgentScope `RuntimeContext`, session workspace root resolution, workspace file path policy, and mock MCP approval events.
- Added a direct compile test against AgentScope Harness `McpServerConfig`.

**Commands run**:

```powershell
cd server
.\mvnw.cmd -q dependency:get "-Dartifact=io.agentscope:agentscope-harness:2.0.0"
.\mvnw.cmd -Pworkbench-poc -pl skillhub-workbench-poc test
.\mvnw.cmd -Pworkbench-poc -pl skillhub-workbench-poc dependency:tree "-Dincludes=io.agentscope"
.\mvnw.cmd validate
.\mvnw.cmd -q help:evaluate "-Dexpression=project.modules" -DforceStdout
```

The active `JAVA_HOME` pointed to Java 8, so Maven verification used a temporary Java 21 runtime supplied by the local development environment.

**Verification results**:

- `agentscope-harness:2.0.0` resolved from Maven Central.
- PoC tests passed: 10 tests, 0 failures, 0 errors.
- Dependency tree passed and showed `agentscope-harness:2.0.0` with `agentscope-core:2.0.0`.
- Default `.\mvnw.cmd validate` passed with the normal 9-module reactor and did not include `skillhub-workbench-poc`.
- Default module evaluation returned only `skillhub-app`, `skillhub-domain`, `skillhub-auth`, `skillhub-search`, `skillhub-storage`, `skillhub-infra`, `skillhub-notification`, and `skillhub-mcp`.

**PoC coverage by task**:

- T001 PASS: Profile-gated disposable module proves dependency resolution and direct harness class compilation without changing the default build.
- T002 PASS: `SkillHubRuntimeContextAdapter` creates `RuntimeContext.builder().userId(...).sessionId(...).build()` and preserves the SkillHub mapping.
- T003 PASS: `SessionWorkspaceResolverTest` creates two concurrent sessions for the same user and verifies distinct workspace roots under the configured base root.
- T004 PASS: `SessionWorkspaceFilePolicyTest` verifies read/write inside the session root and rejects `../other/SKILL.md`, absolute paths, traversal writes outside the session root, and symbolic link path segments before read or write.
- T005 PASS with limitation: `MockMcpApprovalPolicyTest` represents mutating and unknown MCP calls as SkillHub approval-required events before execution and wraps the call in AgentScope `RequireUserConfirmEvent` with a `ToolUseBlock`.
- T006 PASS: This section records the result and selected version.

**Limitations and follow-up for product implementation**:

- The approval PoC is adapter-level. It uses AgentScope event primitives, but it does not wire a real AgentScope MCP client call through `PermissionEngine` or a live runtime executor.
- The workspace file policy is a SkillHub adapter boundary proof. The PoC rejects lexical escapes and symbolic link path segments; production implementation still needs AgentScope runtime workspace/sandbox configuration and audit persistence.
- No product `skillhub-workbench` module, frontend, Flyway migration, or changes to existing `skill`, `skill_version`, or `skill_file` tables were added.

### DeepAgents

DeepAgents is a credible alternative but is less suitable for this MVP because it introduces a Python/LangGraph runtime service boundary. It can remain a reference for product patterns such as planning, subagents, file editing, and HITL, but it should not be the default SkillHub integration unless SkillHub intentionally externalizes runtime into a Python service.

Official references:

- `https://docs.langchain.com/oss/python/deepagents/overview`
- `https://docs.langchain.com/oss/python/deepagents/human-in-the-loop`

## SkillHub Fit

Existing SkillHub lifecycle supports the final publish step:

- `SkillPublishService.publishFromEntries(...)` creates `SkillVersion`, stores files, and uploads `packages/{skillId}/{versionId}/bundle.zip`.
- `SkillDownloadService` downloads latest or selected versions and can fall back to a per-file zip.
- Existing documents already define neutral package structure in `docs/07-skill-protocol.md`.

## Decisions

| Decision | Rationale | Alternatives Rejected |
| --- | --- | --- |
| Prefer AgentScope Java after PoC passes | Best fit for Java service stack and official session/workspace/MCP/permission primitives; final version and adapter contract must be pinned by P3-0 | DeepAgents requires a Python runtime service for MVP |
| Add dedicated workbench module | Keeps runtime concerns separate from existing skill lifecycle and reduces data-impact risk | Putting workbench state directly in existing skill tables couples drafts with published lifecycle |
| New workbench tables only | Workbench sessions, workspace snapshots, tool approvals, and MCP bindings are new concepts | Altering existing skill tables is unnecessary for MVP |
| Reuse existing publish flow | Preserves review, validation, audit, storage, and download semantics | Custom publish path risks lifecycle drift |
| Session-scoped workspace isolation by default | Each draft must be independently reviewable and disposable | User-scoped workspaces risk cross-session bleed for draft edits |

## Risks

- AgentScope Java Maven dependency compatibility must be verified with a local PoC before implementation starts; failure reopens the runtime framework decision.
- Production sandbox mode may require additional container runtime configuration.
- MCP credential handling must be kept out of workspace files and logs.
- Runtime event model must be adapted to SkillHub's frontend streaming conventions.

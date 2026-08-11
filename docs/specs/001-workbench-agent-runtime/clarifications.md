# Clarifications: Workbench Agent Runtime

**Created**: 2026-07-30
**Spec**: `specs/001-workbench-agent-runtime/spec.md`

## Resolved Decisions

| Topic | Decision | Basis |
| --- | --- | --- |
| Runtime framework | Prefer AgentScope Java for MVP; keep DeepAgents as reference alternative | Java/Spring integration fit and official runtime primitives for session, workspace, tools, MCP, permission, and sandbox |
| Package format | Keep neutral `<skill>/SKILL.md` package output | User explicitly rejected platform-specific Codex/Claude packaging branches |
| Update semantics | Updating a skill always creates a new version | User explicitly required version creation for management and control |
| Business ownership | SkillHub remains source of truth; runtime is a kernel only | Preserves existing namespace, visibility, review, publish, and download rules |
| Module strategy | Add a dedicated workbench module and new workbench tables | Limits blast radius and avoids existing table data migration for MVP |
| MCP handling | User selects MCP catalog entries exposed through the workbench runtime adapter per session; high-risk calls require approval | Required for internal tool access without silent side effects |
| Runtime readiness gate | AgentScope Java remains a preferred candidate until T001-T005 pass | PoC failure reopens the framework decision before product implementation |

## Open Clarifications

No product clarifications remain. Implementation remains blocked on the runtime PoC gate. Any later change to existing skill tables, runtime service deployment topology, or default tool approval policy is a material design change and requires review.

# Workbench Runtime Security

## Summary

Workbench security is enforced at both SkillHub business boundaries and AgentScope runtime boundaries.

## Durable Rules

- A user may only access their own workbench sessions unless an explicit admin support flow is added later.
- Workspace keys and physical paths are server-generated; user-supplied file paths must be normalized and rejected if they escape the session workspace.
- MCP credentials and connector secrets must never be written into workspace files, downloadable skill packages, session transcripts, or diff output.
- High-risk tools default to explicit user approval; unattended mode must deny approval-required operations instead of silently executing them.
- Host shell execution is not acceptable for untrusted skill work. Production workbench execution should use a sandboxed AgentScope filesystem/runtime mode.
- Packaging must exclude runtime artifacts such as `AGENTS.md`, AgentScope state, session logs, memory, tool approval records, and temporary files.
- Audit records must cover session creation, skill import, file edits, tool approvals, publish preview, and final publish submission.

## Source References

- `docs/03-authentication-design.md`
- `docs/05-business-flows.md`
- `docs/07-skill-protocol.md`
- `specs/001-workbench-agent-runtime/plan.md`
- AgentScope Java permission docs: `https://java.agentscope.io/v2/en/docs/building-blocks/permission-system.html`

## Related Knowledge

- [[architecture/workbench-agent-runtime]]
- [[decisions/0001-repository-memory-workflow]]

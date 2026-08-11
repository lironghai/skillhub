# Workbench Agent Runtime Boundary

## Summary

SkillHub P3 workbench prefers AgentScope Java as the agent runtime candidate while SkillHub remains the business control plane. The runtime decision is not final until the P3-0 PoC passes.

## Durable Rules

- AgentScope Java is expected to own runtime execution primitives after the PoC passes: `RuntimeContext(userId, sessionId)`, session state, workspace filesystem, tools, MCP client registration, permission decisions, event streaming, and sandbox execution.
- PoC failure reopens the framework decision before product implementation.
- SkillHub owns business identity, namespace membership, SkillHub skills and versions, MCP catalog selection, review, publishing, audit, and public download.
- Workbench sessions map SkillHub user/session records to AgentScope `RuntimeContext`; the runtime context is not the business record.
- Editing sessions use session-scoped workspace isolation by default so each draft is independently reviewable and disposable.
- Updating an existing skill must pull a selected SkillHub skill version into the workbench workspace and must publish the result as a new SkillHub version.
- Downloadable skill packages remain runtime-neutral and must only include the skill directory contents, especially `SKILL.md` and optional resources.

## Source References

- `docs/05-business-flows.md`
- `docs/07-skill-protocol.md`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillDownloadService.java`
- `specs/001-workbench-agent-runtime/spec.md`
- AgentScope Java official README: `https://github.com/agentscope-ai/agentscope-java/blob/main/README.md`
- AgentScope Java workspace docs: `https://github.com/agentscope-ai/agentscope-java/blob/main/docs/v2/en/docs/harness/workspace.md`

## Related Knowledge

- [[decisions/0001-repository-memory-workflow]]
- [[security/workbench-runtime-security]]

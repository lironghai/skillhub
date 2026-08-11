# Repository Memory Workflow

## Summary

SkillHub SDD work stores feature requirements in `specs/` and durable cross-feature knowledge in `knowledge/`.

## Context

The SDD documentation root is `docs/` relative to the SkillHub source root. This keeps product and architecture documents together while preventing SDD artifacts from being split across multiple roots.

## Decision

- All SDD feature artifacts live under `specs/`.
- Durable architecture, integration, security, and operational knowledge lives under `knowledge/`.
- Every meaningful knowledge note must be reachable from `knowledge/index.md`.
- Wiki Links are used only for knowledge-to-knowledge references.

## Source References

- `AGENTS.md`
- `.specify/memory/constitution.md`

## Related Knowledge

- [[architecture/workbench-agent-runtime]]
- [[security/workbench-runtime-security]]

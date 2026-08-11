# SkillHub SDD Project Rules

This documentation workspace uses Spec Kit, Spec-Driven Development, and Repository Memory.

## Documentation Location

- Source project root: `..`
- SDD documentation root: `.`
- Storage mode: `external`

All `AGENTS.md`, `.specify/`, `.agents/`, `specs/`, and `knowledge/` paths in this file are relative to the SDD documentation root. Source code and tests are read and changed only under the source project root.

Do not create symlinks, junctions, mirrors, or fallback document copies. The global `AGENTS.md` SDD documentation-root mapping is the binding source for this root pair.

## Required Reading

1. Read `.specify/memory/constitution.md`.
2. Read `knowledge/index.md` and all relevant linked notes.
3. Read the active feature under `specs/`.
4. Load Spec Kit skills from `.agents/skills/` when the runtime does not auto-discover them.
5. Resolve conflicts between user request, constitution, specs, existing docs, and Repository Memory before changing feature code.

## Workflow Gates

- Do not implement feature code until spec, clarify, checklist, plan, tasks, and analyze are complete and implementation is explicitly approved or delegated.
- Return material scope, architecture, dependency, migration, security, or compatibility changes to user review.
- After each implementation batch, reconcile code, tests, specs, plan, tasks, contracts, checklists, durable docs, and Repository Memory.
- Repair document drift before reporting completion.

## Repository Memory

- Use `specs/` for feature-scoped requirements.
- Use `knowledge/` for durable cross-feature architecture, integration, security, and operations facts.
- Keep Wiki Links canonical, reachable from `knowledge/index.md`, and reciprocal for meaningful relationships.
- Never store secrets, credentials, private data, raw chat transcripts, transient notes, or unapproved requirements in Repository Memory.

## SkillHub Boundaries

- SkillHub remains the business source of truth for users, namespaces, skills, versions, review, publishing, download, audit, and MCP catalog.
- Agent runtime integrations must remain adapter-owned and must not bypass SkillHub governance or version lifecycle.
- Skill package export remains neutral: `<skill>/SKILL.md` plus optional package files. Runtime workspace files such as `AGENTS.md`, session logs, memory, plans, and tool approval records must not be included in downloadable skill packages.

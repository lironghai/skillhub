# Repository Memory

Repository Memory stores durable SkillHub project knowledge that should survive a single feature spec.

Use this folder for architecture boundaries, integration decisions, security rules, operational constraints, and code-reference maps. Use `specs/` for feature-specific requirements and implementation plans.

Rules:

- Add every durable note to `knowledge/index.md`.
- Link knowledge notes with Wiki Links such as `[[architecture/workbench-agent-runtime]]`.
- Use repository paths, not Wiki Links, for source code and spec files.
- Do not store secrets, credentials, private data, raw chat transcripts, or transient task notes.

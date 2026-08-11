# Quickstart: Workbench Agent Runtime Acceptance

This quickstart describes the intended acceptance path after implementation.

## Preconditions

- SkillHub server and web images are built from the implementation branch.
- Docker Compose acceptance environment starts PostgreSQL, Redis, object storage, server, web, and any required runtime sandbox service.
- Test user is a member of a test namespace.
- At least one published source skill exists for update testing.
- A mock MCP server is available with one read-only tool and one mutating approval-required tool.

## Flow A: Create New Skill

1. Log in to SkillHub web.
2. Open Workbench.
3. Create a session in the test namespace.
4. Ask the model to create a simple skill.
5. Confirm that `SKILL.md` appears in the file tree.
6. Edit `SKILL.md` manually in the workbench editor.
7. Open diff review and verify both model and manual edits appear.
8. Run package preview.
9. Publish.
10. Verify the new SkillHub version appears and can be downloaded as a zip containing the neutral skill files.

## Flow B: Update Existing Skill

1. Start an update session.
2. Select a concrete source skill version.
3. Verify source files are imported into the workspace.
4. Ask the model to update the skill.
5. Review diff against the imported baseline.
6. Publish as a new version.
7. Download the old and new versions and verify the old version is unchanged.

## Flow C: MCP Approval

1. Select the mock MCP server for a session.
2. Ask the model to call a read-only tool.
3. Verify the read-only tool call follows configured policy.
4. Ask the model to call the mutating tool.
5. Verify the UI shows an approval prompt.
6. Reject once and verify no side effect occurs.
7. Repeat and approve once; verify the approved call executes and is audited.

## Flow D: Runtime Artifact Exclusion

1. Add runtime-like files to the workspace, such as `AGENTS.md`, session logs, or memory files.
2. Run package preview.
3. Verify those files are excluded.
4. Publish and download the resulting package.
5. Verify the zip only contains allowed neutral skill package files.

## Required Verification

- Backend tests pass.
- Frontend tests pass.
- Playwright workbench flows pass.
- Visual evidence is captured at desktop and common laptop widths for chat/events, file tree/editor, diff review, approval prompt, package preview, and publish panel.
- Built server/web images run in acceptance Compose.
- Acceptance verifies create, update, MCP approval, package preview, publish, and download.

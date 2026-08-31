## Why

The expert skill bundle editor can currently select only skills that already exist in SkillHub. A bundle ZIP may contain valid skills that have not been published to the target namespace, forcing users to leave the editor, upload each skill separately, and then return to select them.

## What Changes

- Add a ZIP upload action to the existing skill selection area without changing the current bundle metadata form or manual skill selection flow.
- Accept the existing neutral bundle layout, with one or more top-level skill directories such as `<skill-name>/SKILL.md` plus optional files beneath each directory; an unchanged ZIP produced by the current bundle download remains a valid input.
- Preflight the entire archive before publishing any contained skill. Any archive-structure, package-validation, permission, ownership, or deterministic conflict error prevents all imports.
- Publish each validated directory through a prepared form of the existing single-skill publication lifecycle, fixing the validated metadata, slug, and version before any write while preserving package validation, object storage, scanning, review, and visibility rules.
- Add a user option to publish an uploaded package as a strict new version when its resolved name belongs to the current user. When disabled, an own-skill name collision is reported and not published; when enabled, an existing version is never replaced and a pending review is never withdrawn implicitly.
- Reject every same-name conflict owned by another user, regardless of visibility, publication status, or hidden state, and identify the exact imported skill display name and resolved namespace/slug without exposing the other owner's metadata.
- Preserve successful imports if a rare runtime or concurrent conflict occurs after preflight, clean up or compensate the failed item's side effects, return item-level failures, and automatically select every successfully imported skill in the bundle editor.
- Keep existing skill bundle persistence, creation, editing, download, and manual selection contracts compatible.

## Capabilities

### New Capabilities

- `skill-bundle-batch-import`: Batch preflight and publication of neutral skill directories from the expert skill bundle editor, including ownership-aware conflict handling and automatic selection of successful imports.

### Modified Capabilities

None. This repository has no established OpenSpec capability specs yet, and the existing skill bundle behavior remains compatible.

## Impact

- Frontend skill bundle editor, API client/types, query invalidation, upload feedback, and localization.
- Skill publication transport/application services, archive extraction, authorization policy, rate limiting, metrics, and response DTOs.
- Existing skill package validation, publication, scanning, review, object storage, and search/index update paths are reused.
- No database migration, new runtime service, or change to `skill_bundle` / `skill_bundle_item` storage is required.
- New backend unit/integration tests and frontend interaction tests are required; browser acceptance must cover upload, conflict reporting, partial success, and automatic selection.
- Overall complexity is medium-high but bounded: frontend and bundle persistence changes are medium/low risk, while the prepared-publication refactor and failed-item compensation are the two high-risk backend areas and are isolated behind the existing skill publication boundary.

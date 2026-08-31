## Context

Expert skill bundles persist ordered references from `skill_bundle_item` to existing `skill` rows. The editor searches SkillHub skills and submits `items[].skillId`; it cannot introduce skills that exist only inside a downloaded bundle ZIP.

The current download path emits one directory per skill, such as `planner/SKILL.md`. The current upload path publishes one skill package and rejects archives containing `SKILL.md` in multiple directories. `SkillPublishService` owns metadata parsing, ownership isolation, version handling, object storage, scanning, review submission, and skill pointer updates. This change factors and composes that lifecycle instead of duplicating it.

The repository has unrelated staged work. Implementation must remain scoped to the batch import surface and must not revert other changes.

## Goals / Non-Goals

**Goals:**

- Add batch ZIP import inside the existing bundle editor and automatically select successful results.
- Guarantee zero publication writes when any deterministic preflight error exists.
- Bind formal publication to the exact metadata, slug, version, and content validated in preflight.
- Preserve successful publications after post-preflight failures while leaving no unhandled residue for a failed item.
- Return exact uploaded skill names and resolved namespace/slugs for conflicts without exposing other owners' metadata.
- Allow strict new-version publication only for the current user's own skills.

**Non-Goals:**

- Storing the uploaded ZIP on the bundle or introducing a bundle manifest.
- Pinning bundle items to skill versions or making bundles immutable snapshots.
- Changing bundle metadata, labels, visibility, download layout, or manual selection.
- Adding an asynchronous batch-import workflow, progress streaming, a separate runtime service, new database tables, or new infrastructure in the first iteration. One in-process scheduled scan-intent reconciler is in scope for reliable post-commit delivery.
- Bypassing existing scanning, review, visibility, or version rules.

## Decisions

### 1. Add a skill batch-publication endpoint

Add an authenticated multipart endpoint under the skill publication surface, provisionally `POST /api/web/skills/{namespace}/batch-publish`. Inputs are the ZIP, selected bundle visibility, `publishOwnConflictsAsNewVersion`, `confirmWarnings`, and a required confirmation token whenever `confirmWarnings=true`.

The endpoint never creates or updates a bundle. The frontend maps successful results into the existing selected-skill form state, and the existing bundle save continues to submit `skillId` references.

Browser-side extraction was rejected because it adds a ZIP dependency to the frontend, makes full-batch preflight unreliable, multiplies requests, and weakens central security and quota enforcement.

### 2. Factor a streamed ZIP reader and preserve the download layout

Factor normalized raw ZIP reading out of `SkillPackageArchiveExtractor`. The single-skill path keeps its root-promotion behavior. The batch path groups entries by first path segment and requires each group to contain a direct child `SKILL.md`.

Directory order is the order in which the first non-directory entry for each top-level directory appears. Nested resources remain within that group. Directory names are transport labels; parsed `SKILL.md` metadata determines display name, slug, and declared version. An unchanged archive from the current bundle download endpoint must pass layout validation.

Streaming limits are:

- 50 top-level skill directories;
- 100 MiB total decompressed bytes;
- configurable `maxBatchFileCount`, defaulting to `MAX_BUNDLE_ITEM_COUNT * SkillPackagePolicy.MAX_FILE_COUNT` and currently at least 25,000 file entries;
- existing per-file and per-skill limits.

Entry count is checked before reading content, and byte limits while reading. Deriving the default from the current 50-skill and 500-file limits preserves every valid bundle-download round trip while bounding zero-byte entry abuse. Configuration validation must reject a lower default than the derived compatibility floor unless the download contract is changed in a separate approved change.

### 3. Preflight the full batch into immutable publication plans

`BatchSkillImportService` resolves authorization and archive structure, then composes existing parsing and validation rules into one deeply immutable `PreparedSkillPublication` per item. Each plan contains:

- archive SHA-256 digest and normalized immutable entries;
- parsed metadata and resolved display name/slug;
- resolved incoming version, including a generated default if absent;
- target namespace, visibility, and ownership decision.

Because the current `PackageEntry` exposes a mutable `byte[]`, prepared entries defensively copy content both when constructed and when accessed; `List.copyOf(...)` alone is insufficient. Tests mutate the extractor's original arrays after preflight and prove that published bytes and digest remain unchanged.

Batch preflight also checks duplicate resolved slugs, strict own-skill version rules, and all same-name records owned by another user regardless of visibility, publication status, or hidden state. No publication call occurs if any deterministic error exists.

Warnings return `CONFIRM_REQUIRED`, all warnings, and a short-lived HMAC-signed confirmation token with zero writes. The token binds actor, archive digest, target namespace, visibility, own-conflict option, normalized warning-code hash, and expiry. Confirmation resubmits the retained `File` and token. The server rereads the archive, recomputes all bound context, and reruns complete preflight. Missing, expired, tampered, or mismatched tokens return `CONFIRM_REQUIRED` with a newly issued token when warnings remain. Formal publication consumes the newly prepared plans directly and never reparses metadata or regenerates default versions. The token uses a dedicated secret with required non-placeholder production configuration and current/previous key verification for short rotation overlap; it does not reuse cookie/download secrets and requires no persistence table.

### 4. Use strict, privacy-preserving ownership rules

For an own same-name skill:

- option off: `OWN_SKILL_NAME_CONFLICT`;
- option on: target the existing `Skill` and require a genuinely new version;
- any existing same version in any lifecycle state: `OWN_SKILL_VERSION_CONFLICT`;
- any pending review that would otherwise be withdrawn: `OWN_SKILL_PENDING_REVIEW_CONFLICT`.

The batch path never creates a duplicate owned skill, replaces an existing version, or silently withdraws a pending review.

For any other-owner same-name record, preflight returns uniform `SKILL_NAME_UNAVAILABLE` regardless of its visibility, publication status, or hidden state. The response includes only the uploaded item's display name and resolved namespace/slug. It does not reveal the existing owner, visibility, lifecycle state, or other metadata. This is intentionally stricter than parts of the generic single-upload lookup and implements the approved cross-owner rule deterministically.

If own and other-owner records coexist for the resolved namespace/slug, the other-owner rule takes precedence and blocks the item regardless of the strict-new-version option.

### 5. Publish prepared items independently with a commit-defined success boundary

The batch orchestrator is non-transactional. In archive order, it invokes a proxied `SkillPublishService.publishPrepared(...)` once per plan. That method is a factored form of the existing single-skill lifecycle and owns one database transaction. The existing single-skill endpoint remains compatible and can prepare then call the shared core.

The database commit is the item success boundary. Each attempt follows these rules:

- database changes roll back on failure;
- newly written object keys are tracked outside the transaction and deleted after rollback; if deletion fails, `SkillStorageDeletionCompensationService` records the keys in a separate `REQUIRES_NEW` transaction so its retry task can complete cleanup;
- when scanning is enabled or required, review state and a durable scan intent commit in the item transaction; when scanning is disabled and permitted for the private lifecycle, no scan intent is created;
- Redis scan dispatch, search synchronization, and external events occur after commit and are retryable or reconcilable;
- compensation for a failed item cannot affect committed successful items.

For scanning, reuse the existing `security_audit` row with `scannedAt == null` plus the version's scanning status as the durable intent, avoiding a schema migration. The transaction creates the audit intent but does not write a local scan directory or publish Redis. After commit, the dispatcher publishes a task with a stable audit-derived identity and the already persisted bundle storage key. A scheduled reconciler republishes stale unscanned intents when immediate delivery fails. In local scanner mode, the consumer reconstructs a temporary directory from the stored bundle before invoking the directory scanner and always removes it afterward. Consumer handling is idempotent by audit identity, with database locking or an equivalent guard so duplicate deliveries do not apply a result twice.

No after-commit delivery failure can turn a committed item into a failed batch result. The response reports the item as successful with its current scan/search/event delivery status; reliable reconciliation owns eventual delivery. Object upload, validation, serialization, database, or other pre-commit failures, including a commit known to have failed, remain item failures and trigger rollback/compensation.

The orchestrator maps an unknown `RuntimeException` to sanitized `ITEM_PUBLICATION_FAILED` with a correlation ID only when the item transaction has not committed or commit is known to have failed. Exceptions after a confirmed commit are converted to the applicable delivery-pending success result and scheduled for retry or reconciliation. Stack traces and infrastructure details remain in server logs, and processing always continues to the next prepared item.

Overall outcomes are `PRECHECK_FAILED`, `CONFIRM_REQUIRED`, `COMPLETED`, `PARTIAL_SUCCESS`, and `FAILED`. Authentication, authorization, multipart-size, and rate-limit rejection continue to use normal HTTP error handling.

### 6. Return batch-level and item-level results

```text
BatchSkillImportResponse
  outcome
  confirmationToken? # CONFIRM_REQUIRED only
  batchErrors[]
    errorCode
    message
    sourcePath?       # sanitized, when safely attributable
  items[]
    sourceDirectory
    displayName?
    namespace?
    slug?
    version?
    outcome
    skillId?          # success only
    publicationStatus?
    scanDeliveryStatus? # success may be pending retry
    warnings[]
    errorCode?
    message?
    correlationId?   # unexpected runtime failure only
```

`batchErrors` holds archive-level failures that cannot safely be assigned to a directory. `items` contains every safely identifiable top-level directory in deterministic order. Unsafe labels are sanitized.

For completed publication attempts, the frontend merges successes into `selectedSkills` by `skillId`, preserves existing notes, appends new selections in response order, refreshes skill search data, and shows every batch/item failure next to the upload control. Preflight and confirmation states do not change selection.

### 7. Preserve bundle visibility and saveability

Imported skill visibility comes from the current bundle visibility field. Private imports follow the existing private lifecycle. Public and namespace-only imports follow existing scan/review rules.

If the form is currently `PUBLISHED` and any successful import is not immediately publishable, the frontend changes the form status to `DRAFT` and shows a notice naming the affected imported skills. `SkillBundleService` remains the server-side authority for publishability and visibility compatibility.

### 8. Enforce authorization, weighted quotas, and compatibility

- Require namespace owner/admin authority plus existing `skill:publish` API-token scope.
- Apply existing CSRF and authenticated multipart route policies.
- Retain request-level limiting and charge a dedicated batch publication quota at `max(1, safelyGroupedDirectoryCount)`, including attempts that later fail preflight. Reuse existing limiter keys/windows where possible, but add weighted consumption so malformed archives never cost zero and 50 skills cannot cost one request unit.
- Keep the single-skill endpoint and every bundle endpoint unchanged.
- Regenerate the checked-in frontend OpenAPI schema when the controller contract is documented.
- Require no database migration or bundle persistence change.

## Risks / Trade-offs

- **Preflight-to-publish race:** recheck authoritative ownership/version rules inside each item transaction without changing prepared identity or version; report item failure and continue.
- **External side effects:** compensate pre-commit object writes after rollback in a separate transaction; treat committed scan intent as success and reconcile idempotent after-commit delivery.
- **Long synchronous requests:** process sequentially under 50-skill/100-MiB/derived-entry bounds and record timing; consider an asynchronous job only from measured gateway evidence.
- **Warning resubmission:** verify a short-lived signed token, recompute all bound context, and rerun full preflight; changed actor, content, target inputs, or warnings require confirmation again.
- **Public imports not immediately publishable:** keep the bundle draft and name the affected skills; never bypass review.
- **Dirty worktree overlap:** read current files before implementation edits and never revert unrelated staged work.

## Impact and Complexity

- **Frontend - medium:** one upload action, one strict-new-version toggle, structured feedback, automatic selection, and published-to-draft form handling. Existing metadata and save contracts remain intact.
- **Archive/API - medium:** a new multipart endpoint, streamed multi-root grouping, typed outcomes, signed confirmation tokens, and weighted quota consumption.
- **Publication domain - high but localized:** immutable preparation and `publishPrepared(...)` must factor the current lifecycle without changing the existing single-upload contract.
- **Failure handling - high but testable:** attempt-scoped storage and post-commit/compensation behavior must be proven at injected failure points.
- **Persistence/deployment - low:** no schema migration, separate service, asynchronous batch job, or bundle-model change; the existing application gains one scheduled scan-intent reconciliation task.

The scope remains controllable because the high-risk work is confined to the skill publication boundary, each item retains an independent transaction, and tasks require focused contract, integration, service-level, and browser verification before release.

## Migration Plan

1. Deploy the backend endpoint, extractor, prepared-publication refactor, orchestrator, compensation, authorization, weighted quota, metrics, and tests. No database migration is needed.
2. Deploy the compatible frontend upload control and result handling.
3. Verify download/upload round trip, zero-write preflight failure, strict own new-version handling, every-status foreign conflicts, token-bound warning confirmation, failure compensation, partial success, and automatic selection against the running service.
4. Roll back by removing the frontend control and endpoint. Successfully imported skills remain valid standalone SkillHub records.

## Open Questions

None. The archive format, strict conflict behavior, preflight guarantee, partial-success behavior, compensation boundary, and compatibility boundary are defined by the approved requirements and this design.

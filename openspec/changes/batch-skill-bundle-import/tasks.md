## 1. Batch archive extraction

- [ ] 1.1 Add extractor tests for one/multiple directories, first-file order, unchanged bundle-download round trip, nested resources, OS metadata, missing `SKILL.md`, top-level files, duplicate normalized paths, unsafe paths, 50-skill limit, derived 25,000-entry compatibility floor, 100-MiB decompressed limit, and per-file limits.
- [ ] 1.2 Refactor the ZIP reader so single-skill root promotion stays compatible while the batch extractor consumes normalized raw entries without stripping top-level directories.
- [ ] 1.3 Implement streamed batch grouping with root-relative `PackageEntry` values, deterministic order, sanitized batch errors, directory errors, and configurable limits enforced before materializing unbounded content.

## 2. Immutable full-batch preflight

- [ ] 2.1 Add service tests proving archive, package, metadata, permission, scanner, duplicate-slug, every-status cross-owner conflict, strict own-version conflict, and pending-review errors are aggregated before any publication call.
- [ ] 2.2 Add ownership tests for the default-disabled option, enabled strict new version, no same-version replacement, no pending-review withdrawal, no duplicate owned skill, mixed own/other-owner precedence, and uniform errors for other-owner public, namespace-only, private, unpublished, and hidden records without metadata disclosure.
- [ ] 2.3 Add warning-confirmation tests proving all warnings and a signed token are returned, no publication occurs before confirmation, missing/expired/tampered tokens and changed actor/archive/namespace/visibility/option/warnings invalidate confirmation, current/previous keys work only during rotation overlap, and full preflight reruns.
- [ ] 2.4 Implement a short-lived HMAC confirmation-token service binding actor, archive digest, namespace, visibility, own-conflict option, normalized warning-code hash, and expiry; add dedicated current/previous secret configuration, reject missing/placeholder production secrets at startup, and do not reuse cookie/download keys.
- [ ] 2.5 Factor current parsing/validation into deeply immutable `PreparedSkillPublication` values fixing digest, entries, metadata, slug, generated or declared version, namespace, visibility, and ownership decision; defensively copy entry bytes on construction/access and test mutation of original arrays after preflight.
- [ ] 2.6 Implement batch duplicate/ownership checks and ensure publication consumes prepared values without reparsing or regenerating defaults; test an omitted version across a clock boundary.
- [ ] 2.7 Define stable batch/item outcomes and error codes plus DTOs containing confirmation token, ordered batch errors, source directory, display name, namespace/slug, version, warnings, publication status, successful `skillId`, and runtime correlation ID.

## 3. Independent publication and failed-item compensation

- [ ] 3.1 Add service tests for complete success, all-runtime-failed, and partial success, including concurrent conflict, object-store failure after partial upload, unknown pre-commit runtime exception, known commit failure, continuation to later items, result order, and every post-commit delivery failure remaining a committed success pending retry.
- [ ] 3.2 Implement proxied transactional `SkillPublishService.publishPrepared(...)`, reusing the existing lifecycle while consuming the exact prepared identity/version and keeping the existing single-upload API compatible.
- [ ] 3.3 Track uploaded keys outside the item transaction, delete them after rollback, and invoke storage-compensation recording with `REQUIRES_NEW` when immediate cleanup fails; prove compensation survives the original rollback and never affects committed successes.
- [ ] 3.4 When scanning is enabled or required, persist scan intent with the item by reusing active unscanned `security_audit`, dispatch a stable audit-identified task after commit from the stored bundle key, add stale-intent reconciliation, reconstruct/clean local-mode temporary directories, and make duplicate delivery idempotent without a schema migration; preserve the permitted scan-disabled private lifecycle without creating an intent.
- [ ] 3.5 Implement the non-transactional loop with stable domain-error mapping, sanitized `ITEM_PUBLICATION_FAILED` plus correlation ID for other pre-commit `RuntimeException` values, continuation after every failure, and successful delivery-pending results after committed scan-dispatch failures.
- [ ] 3.6 Add batch/item metrics and log assertions for sanitized errors, counts, object compensation, scan delivery/retry state, elapsed time, and overall outcome.

## 4. Multipart API, authorization, quota, and compatibility

- [ ] 4.1 Add controller tests for multipart input, namespace owner/admin authority, `skill:publish` scope, CSRF, request limits, visibility, own-conflict option, signed confirmation-token binding, batch errors, and typed outcomes.
- [ ] 4.2 Implement the endpoint and DTO mapping under the skill publication transport boundary; leave existing single-skill and bundle endpoints unchanged.
- [ ] 4.3 Retain request-level limiting and implement weighted batch publication cost `max(1, safelyGroupedDirectoryCount)`, including preflight failures; test zero-directory, first-entry-invalid, early-limit termination, and repeated 50-skill requests.
- [ ] 4.4 Update route security, localized backend messages, API documentation, stable conflict codes, batch errors, and correlation ID handling.
- [ ] 4.5 Regenerate and verify the checked-in frontend OpenAPI schema and assert compatibility of existing bundle and single-upload contracts.

## 5. Bundle editor upload and automatic selection

- [ ] 5.1 Add API-client/query tests for multipart parameters, typed outcomes, signed confirmation-token resubmission, query invalidation, and preserving the selected `File` across confirmation.
- [ ] 5.2 Add editor tests for the ZIP action, default-disabled strict-new-version toggle, loading states, batch/item preflight errors, exact uploaded skill conflicts, warning confirmation, and runtime failures.
- [ ] 5.3 Add selection tests proving successes merge in response order, deduplicate by `skillId`, preserve existing selections/notes, and never select failures.
- [ ] 5.4 Add edit-form tests proving a `PUBLISHED` form switches to `DRAFT` with a named notice when a successful import is not immediately publishable.
- [ ] 5.5 Implement the upload action in the current skill selection area using existing control/dialog/toast patterns without changing metadata fields or manual selection.
- [ ] 5.6 Add Chinese and English text for upload, strict own-skill new-version opt-in, confirmation, exact conflicts, draft-status change, partial success, and publication/review status.

## 6. Integration and release verification

- [ ] 6.1 Run focused backend tests for extraction, preparation, orchestration, compensation, controller, security, quota, metrics, single-skill publication, and bundle behavior.
- [ ] 6.2 Run frontend unit tests, type checking, lint, and production build for the editor, API client, queries, and unchanged manual workflow.
- [ ] 6.3 Run PostgreSQL/object-storage integration tests proving zero-write preflight, prepared-version stability, failed-item rollback/compensation, and partial-success persistence across independent transactions.
- [ ] 6.4 Build server/web images and run service-level acceptance for private success, unchanged download round trip, malformed zero-write, token-bound confirmation, strict own new version, mixed/every-status foreign conflict, and post-preflight partial success.
- [ ] 6.5 Use real browser interactions at desktop and mobile/laptop viewports to verify upload, exact results, auto-selection, preserved notes, draft switching, no overlap, and successful draft save.
- [ ] 6.6 Update user/developer documentation for layout, limits, visibility/review behavior, conflict option, partial-success semantics, and compensation; reconcile OpenSpec artifacts with implementation before completion.

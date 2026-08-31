## ADDED Requirements

### Requirement: Upload skills from the existing bundle editor
The system SHALL add a ZIP upload action to the existing skill selection area of the expert skill bundle create and edit page while preserving all existing bundle metadata fields, manual search, manual selection, notes, and save behavior.

#### Scenario: Upload is available without replacing manual selection
- **WHEN** an authorized user opens the expert skill bundle create or edit page
- **THEN** the user can either search and select existing skills or upload a ZIP containing skills
- **AND** the existing description, avatar, visibility, labels, selected-skill notes, and status controls remain available

### Requirement: Accept the neutral multi-skill archive layout
The system SHALL accept a ZIP containing one or more top-level skill directories, where each directory contains a root `SKILL.md` and may contain files and subdirectories allowed by the existing single-skill package policy.

#### Scenario: Valid multi-skill archive
- **WHEN** an archive contains `planner/SKILL.md` and `writer/SKILL.md` with otherwise valid package contents
- **THEN** the system identifies two independent skill packages
- **AND** directory order is determined by the first non-directory entry encountered for each top-level directory
- **AND** each parsed `SKILL.md` name is the authoritative skill identity

#### Scenario: Existing bundle download is uploaded unchanged
- **WHEN** the user uploads an unchanged ZIP produced by the existing skill-bundle download endpoint
- **THEN** the archive passes layout validation
- **AND** each downloaded top-level skill directory is treated as one import item

#### Scenario: Invalid archive structure
- **WHEN** an archive contains a top-level file, a skill directory without `SKILL.md`, duplicate normalized paths, unsafe paths, more than 50 skill directories, more than the configured `maxBatchFileCount`, or more than 100 MiB of decompressed content
- **THEN** preflight fails with batch-level or directory-specific errors as applicable
- **AND** no contained skill is published
- **AND** entry count, decompressed byte count, and per-file limits are enforced while the archive is streamed
- **AND** the default `maxBatchFileCount` is derived as the bundle item limit multiplied by the single-skill file limit and is currently at least 25,000 file entries

### Requirement: Authorize batch import consistently with bundle management
The system MUST require authentication, skill publication permission, and namespace owner or administrator authority for the target namespace before accepting a batch import.

#### Scenario: Unauthorized namespace member
- **WHEN** an authenticated namespace member who is not a namespace owner or administrator submits a batch import from the bundle editor
- **THEN** the request is rejected before archive publication begins
- **AND** no contained skill is published

### Requirement: Preflight the complete batch before publication
The system SHALL parse and validate every contained skill before performing any publication write. Preflight MUST cover archive structure, per-skill package policy, metadata parsing, target namespace state, permissions, scanner availability, duplicate resolved identities within the batch, incoming version checks, and deterministic ownership conflicts.

#### Scenario: One skill has a deterministic error
- **WHEN** any contained skill fails preflight
- **THEN** the response identifies every detected batch-level and item-level error, including the parsed skill name where safely available
- **AND** the batch remains in a preflight-failed state
- **AND** no skill, skill version, review task, scan task, object, event, or bundle item is created or changed

#### Scenario: Warnings require confirmation
- **WHEN** preflight finds only confirmable warnings and the user has not confirmed them
- **THEN** the response returns all warnings for all affected skills and a short-lived, tamper-resistant confirmation token
- **AND** no contained skill is published
- **AND** the user can explicitly confirm and resubmit the same selected archive

#### Scenario: Confirmation token is missing or its context changed
- **WHEN** the user submits `confirmWarnings=true` without a valid token, or changes the actor, archive digest, target namespace, visibility, own-conflict option, normalized warning set, or token expiry context bound by the token
- **THEN** the server reruns full preflight and returns `CONFIRM_REQUIRED` with a new token when warnings remain
- **AND** no contained skill is published

### Requirement: Bind publication to an immutable preflight plan
Preflight SHALL produce a deeply immutable plan for every item containing the archive digest, normalized entries, parsed metadata, resolved display name and slug, resolved incoming version, target namespace, visibility, and ownership decision. Entry content MUST be defensively copied at construction and access boundaries. Formal publication MUST consume that exact plan without reparsing metadata or regenerating defaults.

#### Scenario: Missing version crosses a clock boundary
- **WHEN** an uploaded `SKILL.md` omits a version and preflight and publication occur in different clock seconds
- **THEN** preflight resolves one default version
- **AND** the same resolved version is checked, published, and returned in the item result

### Requirement: Provide ownership-aware name conflict handling
The upload area SHALL provide an option labelled as publishing conflicts as new versions of the current user's skills. The option SHALL be disabled by default and SHALL affect only skills owned by the current user in the target namespace.

#### Scenario: Own-skill conflict option is disabled
- **WHEN** preflight resolves an uploaded skill to a same-name skill owned by the current user and the option is disabled
- **THEN** preflight fails without publishing any contained skill
- **AND** the response includes the uploaded skill display name and resolved namespace/slug

#### Scenario: Own-skill conflict option is enabled
- **WHEN** preflight resolves an uploaded skill to a same-name skill owned by the current user and the option is enabled
- **THEN** the item targets the current user's existing skill record and proceeds only as a strict new version
- **AND** it does not create a second owned skill record, replace an existing version, or withdraw a pending review
- **AND** any incoming version conflict is reported with the skill display name, resolved namespace/slug, and incoming version

#### Scenario: Existing incoming version or pending review blocks strict new version
- **WHEN** the option is enabled but the incoming version already exists in any lifecycle state or the owned skill has a pending review
- **THEN** preflight fails with a stable, specific error code
- **AND** no existing version or review state is changed

#### Scenario: Same-name skill belongs to another user
- **WHEN** preflight resolves an uploaded skill to a same-name skill owned by another user
- **THEN** the item fails regardless of the own-skill conflict option
- **AND** the response explicitly identifies the uploaded skill display name and resolved namespace/slug
- **AND** the same uniform error code is used regardless of the existing skill's visibility, publication status, or hidden state
- **AND** the response does not expose the other owner, visibility, lifecycle state, or other private metadata

#### Scenario: Own and other-owner records share the resolved identity
- **WHEN** both a current-user skill and one or more other-owner skills share the resolved namespace/slug
- **THEN** the uniform `SKILL_NAME_UNAVAILABLE` other-owner rule takes precedence
- **AND** the item fails regardless of the strict-new-version option

### Requirement: Reuse the existing single-skill lifecycle
For each item that proceeds past preflight, the system MUST reuse the existing single-skill publication lifecycle for visibility, metadata, package files, object storage, security scanning, review submission, and search synchronization, except for ownership and version-conflict behavior explicitly overridden by the strict-new-version requirements above. Imported skill visibility SHALL use the current bundle visibility selected in the editor.

#### Scenario: Private bundle imports private skills
- **WHEN** a user imports a valid batch while the bundle visibility is private
- **THEN** every successfully imported skill follows the existing private-skill publication lifecycle
- **AND** successful skill identifiers are immediately eligible for selection in a draft bundle

#### Scenario: Public or namespace bundle requires review
- **WHEN** a non-super-administrator imports a valid batch while the bundle visibility is public or namespace-only
- **THEN** each successful skill follows the existing scan and review lifecycle
- **AND** the bundle can retain the skills in draft state
- **AND** existing bundle publication rules continue to prevent publishing the bundle until all contained skills are publishable at the selected visibility

#### Scenario: Import into an editor currently marked published
- **WHEN** the current bundle form status is `PUBLISHED` and a successful imported skill is not immediately publishable
- **THEN** the editor changes the form status to `DRAFT`
- **AND** it displays a notice identifying the imported skills that require review or scanning

### Requirement: Preserve successes and clean failed items after runtime failures
After a clean and confirmed preflight, the system SHALL publish items independently in deterministic archive order. A runtime or concurrent failure for one item MUST NOT roll back previously successful item publications and MUST NOT prevent later items from being attempted.

Each item MUST execute in an independent database transaction. The database commit is the item success boundary. When an item fails before commit, its database changes MUST roll back, and any uploaded object MUST be removed or recorded for guaranteed cleanup in an independent compensation transaction. When scanning is enabled or required for an item, review state and a durable scan intent MUST commit with the successful item; when scanning is disabled and permitted by the existing private lifecycle, no scan intent is required. Redis scan dispatch, search synchronization, and external events MUST occur after commit and use retryable or reconcilable delivery. Cleanup MUST NOT affect successful items.

#### Scenario: Concurrent conflict during publication
- **WHEN** the first item publishes successfully and a later item encounters a conflict that appeared after preflight
- **THEN** the response has a partial-success outcome
- **AND** it reports the successful item and the failed item separately
- **AND** the successfully published skill remains available

#### Scenario: Item fails after uploading objects but before commit
- **WHEN** an item fails after uploading one or more objects but before its database transaction commits
- **THEN** that item's database changes are rolled back
- **AND** uploaded keys are deleted or recorded after rollback for guaranteed cleanup in an independent transaction
- **AND** the next item is still attempted

#### Scenario: Scan dispatch fails after a successful commit
- **WHEN** a skill, version, review state, and durable scan intent commit successfully but immediate Redis scan dispatch fails
- **THEN** the item remains successful with a scanning or delivery-pending publication status
- **AND** a reconciler retries delivery from the durable intent using an idempotent task identity
- **AND** the batch does not report the committed skill as a failed item

#### Scenario: Unexpected runtime exception is isolated
- **WHEN** an item throws an unmapped runtime exception before its database transaction commits, or the commit is known to have failed
- **THEN** the item returns the sanitized `ITEM_PUBLICATION_FAILED` code and a correlation identifier
- **AND** no infrastructure details or stack trace are exposed
- **AND** the next item is still attempted

#### Scenario: Post-commit delivery exception cannot reverse success
- **WHEN** an item transaction has committed and scanning, search synchronization, or event delivery then throws an exception
- **THEN** the committed item remains successful with the applicable delivery-pending status
- **AND** retry or reconciliation handles the failed delivery
- **AND** the item is still automatically selected by the editor

### Requirement: Return structured batch and item outcomes
The system SHALL return an overall batch outcome, ordered `batchErrors` for archive-level failures, and an ordered item result for every safely identifiable source directory. Each item result MUST include the source directory, parsed display name when available, resolved namespace/slug when available, incoming version when available, outcome, and a stable error code and user-facing message for failures. Successful results MUST also include the skill identifier and resulting publication status. Unsafe or unassignable archive entries SHALL appear only in `batchErrors` using a sanitized entry label.

#### Scenario: Multiple explicit conflicts
- **WHEN** preflight finds name or version conflicts in multiple directories
- **THEN** the response lists each conflicting uploaded skill by display name and resolved namespace/slug instead of returning only a generic conflict message

#### Scenario: Archive-level error has no source directory
- **WHEN** an unsafe path or aggregate expansion limit prevents assigning an error to a safe top-level directory
- **THEN** the response places the error in ordered `batchErrors`
- **AND** it does not invent an item or expose an unsafe raw path

### Requirement: Prevent batch import from bypassing publication quotas
The system MUST retain request-level rate limiting and charge batch imports a weighted publication cost of `max(1, safelyGroupedDirectoryCount)`, including requests that later fail deterministic preflight.

#### Scenario: Fifty skills consume fifty quota units
- **WHEN** one request contains 50 safely grouped skill directories
- **THEN** it consumes 50 units from the configured batch publication quota
- **AND** repeated batch requests cannot obtain 50 publication attempts for the cost of one ordinary request

#### Scenario: Malformed archive has no safe directory
- **WHEN** an archive fails before any top-level directory can be safely grouped
- **THEN** the request consumes at least one quota unit
- **AND** repeated malformed or early-limit requests remain subject to request-level rate limiting

### Requirement: Automatically select successful imports
The bundle editor SHALL automatically merge every successful batch result into the selected-skill list in response order, deduplicate by skill identifier, preserve already selected skills and their notes, and refresh skill search data.

#### Scenario: Complete success
- **WHEN** all uploaded skills publish successfully
- **THEN** all returned skills appear selected without requiring another search or manual click

#### Scenario: Partial success
- **WHEN** some skills publish successfully and others fail after preflight
- **THEN** only successful skills are automatically selected
- **AND** failed skills remain unselected and are shown with their specific failure details

#### Scenario: Existing selection is also imported
- **WHEN** a successful result refers to a skill already selected in the editor
- **THEN** the selected list contains that skill only once
- **AND** its existing bundle note is preserved

### Requirement: Keep existing bundle and single-skill contracts compatible
The system MUST NOT require a database migration or change the persisted skill bundle item model. Existing bundle create, update, detail, search, download, and manual skill selection behavior, as well as the existing single-skill upload endpoint, SHALL remain compatible.

#### Scenario: Save bundle after batch import
- **WHEN** the user saves a bundle after successful imports have been automatically selected
- **THEN** the existing bundle request continues to submit selected `skillId` values
- **AND** no uploaded archive or embedded package payload is stored on the bundle record

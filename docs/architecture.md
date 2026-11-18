# Jobstein architecture

## Scope
Local-first, self-hosted administrative CLI. Operational start/stop and autonomous job-search execution are deferred.

## Boundaries
- Environment: independent development/production GCP project, OAuth grants, AI integrations, secret namespace and state root.
- User: stable identity, shared job application registry, profile index, Gmail/Calendar cursors.
- Applicant profile: stable UUID, target role, resume versions, validated descriptor, active/completed applications.
- GCP organization is optional; one project per environment, not per applicant profile.
- Google Drive data is user-owned, not a GCP resource. Use desktop OAuth and app-created files with drive.file. Gmail readonly/compose and calendar events readonly scopes. No API keys or service accounts for Workspace access.
- Manual OAuth branding/audience/client creation and user consent remain explicit checkpoints. Testing-mode refresh tokens may expire after seven days.
- Separate Google accounts for development and production are recommended.

## Commands (target scope)
onboard; doctor; environment create/list/show/validate; google configure/authorize/status/validate; ai configure/validate; ai provider list/models; ai integration create/update/list/show/validate/remove; ai agent configure/list/show/reset; user initialize/show/validate; profile create/list/show/validate/activate/deactivate; profile resume upload/update/history; profile descriptor show/regenerate/validate.

## Onboarding order
Environment -> Google project and APIs -> OAuth client import and consent -> AI default and optional bindings -> user Drive state -> first profile -> resume import -> consent to AI processing -> descriptor generation/schema and evidence validation -> user review -> doctor -> ready.
All stages must be resumable and idempotent; never overwrite existing application state during onboarding.

## AI
Declarative provider catalog describes fields and capabilities. xAI only initially. Named integrations specify provider, credential reference, model and provider-specific options. Agent binding resolves a whole integration, otherwise defaults; invalid explicit bindings fail closed. Future incompatible protocols require adapters, not wizard changes. Model availability and reasoning options must be checked against provider support.

## Storage
Production: jobstein/<user-slug>/_shared/ and jobstein/<user-slug>/<role-slug>/.
Development: independent account or jobstein-dev root. Use actual Drive IDs and immutable UUIDs, not paths as identifiers. Shared registry keyed by user and canonical posting ID, retaining completed applications and aliases. Reserve before submission. Single writer initially; Drive JSON is not a transactional database. Unknown submission outcomes must be reconciled before retry.
Profile holds original resume versions/hashes, descriptor JSON, job preferences and application artifacts.
Keep secrets outside Git and Drive. OS credential store preferred; protected local fallback. Doctor is non-destructive, never sends email by default.

## Implementation stages
1. Build and CLI foundation.
2. Config, secrets, environment lifecycle and shared validation contracts.
3. Google provisioning and OAuth/Workspace adapters.
4. xAI adapter, declarative onboarding and agent resolution.
5. Drive user/profile/registry and resume descriptor workflows.
6. Compose onboarding and doctor, integration tests and CI.

## Status
This document specifies target behavior, not implemented functionality. License and final packaging decisions remain open.

## Provisioning recovery
Folder and binary/JSON creation uses persisted allocated Drive IDs. Existing legacy folders may be adopted by their stable marker after metadata validation. Upload conflicts are accepted only with matching metadata and SHA-256 checksum. Descriptor generation journals the exact generated payload locally until its upload and profile index update complete; retry does not call AI again while that journal exists. A new explicit regeneration after completion produces a new descriptor. Finish any pending descriptor generation before replacing its resume. These protections assume a single local environment writer, and do not replace live integration testing or distributed locking.

## Descriptor evidence (schema version 2)
The initial descriptor schema is intentionally extractive: summary and skill strings must occur verbatim in the resume after whitespace normalization; skillEvidence is positional; employment and education records contain only excerpt and evidence, which must match. Unknown claim fields fail validation. Stored descriptors are revalidated against the original uploaded PDF and its SHA-256 digest on read/activation. Provider and model metadata are retained. This is lexical provenance, not semantic entailment: categorization, missing qualifications, and context still require human review. Version 1 descriptors must be regenerated before validation/activation; pending version 1 generation journals require manual recovery before upgrade.

## Local privacy and account binding
Administrative CLI environments and configuration directories use POSIX owner-only directory permissions (0700); new and replaced YAML configuration files use 0600. Existing files are not recursively migrated: doctor audits their ownership and permissions read-only and reports fixed repair guidance, while an explicit migration workflow remains a separate reviewed operation. POSIX filesystems are required for local state. This is access control, not encryption. OAuth reauthorization checks the newly authorized subject against the initialized user before replacing the refresh token. Profile mutations, descriptor retrieval and registry operations also verify the current account. Doctor checks local and remote index equality and fails on divergence. The current implementation still assumes one local writer and does not yet provide an account reset or conflict-resolution command.

## Diagnostic output
Administrative CLI errors and doctor probes use fixed diagnostic codes and recovery guidance. Raw third-party exceptions, response bodies, nested causes and stack traces are not printed by this formatter. Unknown errors receive generic guidance. The onboarding checkpoint journal is validated read-only by doctor (schema, known step IDs, timestamps and completed first-profile selection); invalid state receives repair guidance without rewriting local or remote state. The formatter is not a general-purpose log-redaction system; explicit user-requested profile/descriptor output and the interactive OAuth authorization URL still display their intended contents.

## Profile index reconciliation
Use state status, state validate, state backup, and state reconcile --source local|remote [--apply]. Reconciliation defaults to dry run. Explicit application saves both index copies in an owner-only local backup, validates selected profile ownership and UUID uniqueness, rechecks the snapshot for changes, and updates only the other index. This is index-only recovery, not a full environment backup or restoration of underlying files. No automatic merging or distributed compare-and-swap is provided. Inspect both copies before selecting a source.

## Credential backend selection
The existing POSIX file backend remains the supported default. Backend selection is persisted per live environment in credential-store.yaml; existing configurations continue using files. An experimental Linux Secret Service adapter uses secret-tool with values on stdin/stdout, hashed environment namespaces, and no shell. It intentionally fails closed on ambiguous lookup failures. Because secret-tool may not distinguish an absent entry from a backend failure, native onboarding is not enabled yet. No automatic fallback after a native error, credential migration, macOS Keychain, or Windows Credential Manager is implemented.

## OAuth access token lifecycle
A single GoogleOAuth instance is shared within each live administrative CLI invocation. Access tokens are cached in memory using the returned expires_in lifetime, refreshed ahead of expiration (normally 60 seconds), and invalidated on credential replacement or HTTP 401. Read-only HTTP GET requests, including bounded PDF downloads, retry once after 401. Mutating requests are not automatically repeated; provisioning journals remain responsible for safe retries. Concurrent callers share a synchronized refresh. No access token is persisted. Cross-process reuse and live endpoint testing remain outside this change.

## HTTP adapter tests
JUnit tests use local HTTP servers and synthetic credentials to verify token refresh, read-only 401 retry, no mutation replay, Drive upload conflict checksum matching and xAI JSON/error handling. Package-private test constructors accept only explicit IPv4 loopback origins; public constructors retain fixed provider endpoints. These tests do not establish live provider compatibility.

The loopback HTTP suite also exercises the real authorization callback, PKCE challenge/verifier correspondence, rejected state, account mismatch without refresh-token replacement, successful reauthorization, bounded binary downloads and exhausted authentication retries. Identity endpoint substitution is restricted to the same explicit test origin as the token endpoint.

## Durable profile index updates
Ordinary profile mutations first verify account identity and recover any known pending index update, then require equal local and remote indexes. A private profile-index-intent.yaml records the exact prior and intended index before either copy changes. Recovery accepts only these two known snapshots, completes the remote update before the local update, verifies both copies, and clears the journal. Unrelated state changes fail closed. Doctor and state validate are read-only and flag pending writes; explicit reconciliation refuses to overwrite a pending journal. Retry the interrupted profile operation to resume a recoverable intent. Conflicting intents require manual investigation; distributed conditional writes and full journal conflict resolution are not yet implemented.

### Explicit journal recovery
`jobstein state recovery inspect --env development` reports CLEAN, DIVERGED, RECOVERABLE or CONFLICT, and only classifies each index against the before/after snapshots (no profile contents). `jobstein state recovery resume --env development` previews recovery; add `--apply` to complete a verified recoverable intent. Before applying, both current indexes and the original intent are preserved in an owner-only recovery backup. Unknown modifications, wrong index identity or invalid ownership prevent recovery. This command never discards conflicting journals; manual investigation is still needed. All existing single-writer limitations apply.

## Resumable applicant onboarding
ProfileOnboarding is shared by live creation and the default synthetic sandbox workflow. A previously uploaded resume can be read from storage with a bounded download, validated against its recorded fingerprint and used for descriptor generation without the original local PDF. Explicit AI consent is still required before generation; existing descriptors are reviewed instead of regenerated, and active profiles are validated without repeated approval. Explicitly supplied replacement PDFs retain the existing versioning behavior. Sandbox profile creation with a supplied PDF remains upload-only. Inactive profiles require an affirmative review callback before activation.

First-profile onboarding persists role, slug and stable profile UUID in `onboarding-first-profile.yaml` before provisioning. Retry uses that UUID even if folder creation was interrupted before the index was saved. A conflicting explicit role or changed profile identity fails closed. Completion requires the selected profile to be active with a valid stored descriptor and validated user state; another active profile cannot satisfy it. Completed first-profile checkpoints are revalidated without re-provisioning, resume input, AI consent or review. If they are no longer ready, repair the selected profile through the profile commands and retry onboarding. Legacy checkpoints retain schema version 1: a completed checkpoint with one existing profile can adopt its identity without prompting; multiple profiles require the intended existing `--role` (or interactive role selection). A missing profile is never recreated by a completed checkpoint. Earlier Google/AI/user checkpoints retain their existing skip behavior; doctor still validates overall readiness. Single-writer limitations apply.

Drive HTTP folder recovery now rejects missing or mismatched resource IDs, absent trash status, wrong metadata and parent identity, ambiguous or malformed folder discovery, and successful create responses returning the wrong allocated ID. Loopback tests exercise both exact-match recovery after HTTP 409 and fail-closed alternatives. Root aliases are resolved to the actual Drive root ID before comparison.

The local execution lock reserves the canonical lock-file path in the JVM before opening its channel, preventing a rejected same-JVM acquisition from closing a second channel and potentially releasing POSIX process locks. Separate JVM tests confirm exclusion, independent environment access and release after exit. This remains a local-filesystem single-writer mechanism; hard-link aliases, external lock-file replacement and distributed shared-filesystem semantics are not supported.

Descriptor intent finalization now handles an interruption after the profile index write: the indexed descriptor must match the persisted allocated ID and exact validated payload before the intent is cleared. Onboarding resumes pending descriptor operations before review or replacement upload, without another AI request when a durable payload exists. Activation refuses an unfinished replacement descriptor. Failure injection covers loss of the index update response followed by recovery, approval and a subsequent resume update.

The POSIX file credential backend rejects existing credential files with more than one hard link before reading, replacing or removing them. It requires the filesystem's `unix:nlink` attribute and fails closed if that check is unavailable. This is a point-in-time check under the local single-writer assumption; it does not prevent another process from racing filesystem checks, establish file ownership. Credential operations also reject symlinks and non-directory components in the absolute path above the secret root, including when the credential is absent. Newly created ancestor directories use mode 0700; existing real ancestor directories retain their permissions. Configure a canonical path on hosts where conventional paths contain symlink aliases. These checks do not prevent concurrent path replacement.

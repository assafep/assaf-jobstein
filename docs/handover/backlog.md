# Prioritized development backlog

This is an actionable queue, not a promise that all entries remain bugs. Verify against current code and Git history before making changes. Work through one bounded milestone at a time; fix failing CI first.

## P0 — Correctness and recoverability
- Audit seed JSON and manifest identity: validate expected file ID, parent, MIME, editing/trash status and user ownership on reuse without overwriting mutable remote state. Detect missing local metadata and unsafe adoption; do not silently initialize a new user over an existing user root. Implemented and CI-verified in run 44308313473.
- Audit descriptor journal cleanup, explicit regeneration and activation across crash boundaries; protect pending state from accidental replacement, and distinguish recoverable completion from mismatched payloads. Implemented and CI-verified in run 44309837248.
- Audit profile-index journal and explicit reconciliation for ownership validation, stale snapshots and lost write responses. Implemented and CI-verified in run 44311843552. Keep the single-writer guarantee explicit; do not claim distributed atomicity.
- Continue application registry ownership and allowed-transition audit; strict persisted entry schema validation and user-bound profile ownership checks are implemented and CI-verified in run 44312911023. No real application submission.

## P1 — Security and operational usability
- Evaluate safe missing-entry semantics for native Linux Secret Service; if reliable, enable only with tested behavior and explicit backend selection. Completed audit: `secret-tool` ambiguity is verified, so the backend remains disabled and fail-closed; synthetic cleanup/isolation tests pass in CI. Credential migration needs an explicit reviewed workflow.
- Audit POSIX file ownership, ancestor symlink behavior and configuration migration; avoid claiming encryption. Credential ancestor symlinks, hard links, and existing path ownership now fail closed with synthetic coverage. Configuration permissions are now audited read-only by doctor with a fixed diagnostic; legacy files are never rewritten implicitly. Concurrent filesystem replacement remains outside the single-writer guarantee, and an explicit reviewed migration workflow remains open.
- Review API contract handling, bounded reads, pagination and safe retry behavior where it serves administrative validation. Implemented and CI-verified in run 44314692321; use loopback tests, not real credentials.
- Improve doctor checks and onboarding checkpoint validation, including clear repair guidance and no unintended remote writes. Implemented and CI-verified in run 44317054671.

## P2 — Distribution and readiness
- Reproducible Gradle distribution, checksums, installation/uninstallation documentation and cross-host smoke tests. Implemented and CI-verified in run 44315396663. Keep personal state outside distributions.
- Document release/version policy; do not pick an open-source license or publish releases without user decision. Implemented on 2026-11-18 in [release-policy.md](../release-policy.md): the current `0.1.0-SNAPSHOT`, CI/checksum gates, state compatibility expectations and license decision boundary are explicit; publication remains blocked on owner approval.
- Prepare opt-in authenticated test checklist and redacted evidence format. Completed and CI-verified in run 44315972522. Live test accounts, OAuth consent, billing and xAI credentials still require the user; no live readiness is claimed.
- Once administrative scope has been met, report remaining external blockers and stop rather than implement operational job-search features without approval.

## Parallel migration work (separate DevLoop project)
Read devloop.md. The referenced `devloop.zip` prototype is absent from the repository and scoped project directories, so verification or improvement requires a separate workspace and user-controlled handoff. Coordinate single-writer cutover from existing ChatGPT automation before any unattended writes. Do not add DevLoop runtime dependencies to Jobstein.

## Completed baseline
First-profile onboarding now persists role/slug/UUID before provisioning, revalidates completed checkpoints, and supports explicit legacy selection. Synthetic tests cover lost folder responses, declined review with another active profile, missing/replaced/deactivated profiles and legacy ambiguity. See status.md for verification evidence and architecture.md for repair guidance.

See status.md and architecture.md for CI-verified administrative foundation, profile journal recovery, stored resume onboarding, OAuth/Drive HTTP tests and cross-process locking. Reopen only if a specific defect or requirement warrants it.

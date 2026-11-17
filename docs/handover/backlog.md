# Prioritized development backlog

This is an actionable queue, not a promise that all entries remain bugs. Verify against current code and Git history before making changes. Work through one bounded milestone at a time; fix failing CI first.

## P0 — Correctness and recoverability
- Audit first-profile onboarding checkpoint: persist selected role/profile identity, validate that the intended profile (not merely any active profile) is ready before completing the step, and ensure retry does not ask for unrelated inputs. Cover interrupted provisioning and declined review in tests. Preserve existing environments.
- Audit seed JSON and manifest identity: validate expected file ID, parent, MIME, editing/trash status and user ownership on reuse without overwriting mutable remote state. Detect missing local metadata and unsafe adoption; do not silently initialize a new user over an existing user root. Add fault tests.
- Audit descriptor journal cleanup, explicit regeneration and activation across crash boundaries; protect pending state from accidental replacement, and distinguish recoverable completion from mismatched payloads.
- Audit profile-index journal and explicit reconciliation for ownership validation, stale snapshots and lost write responses. Keep the single-writer guarantee explicit; do not claim distributed atomicity.
- Validate application registry schema, ownership and allowed transitions; preserve user-wide alias deduplication and unknown submission state. No real application submission.

## P1 — Security and operational usability
- Evaluate safe missing-entry semantics for native Linux Secret Service; if reliable, enable only with tested behavior and explicit backend selection. Otherwise document the blocker rather than weaken fail-closed behavior. Credential migration needs an explicit reviewed workflow.
- Audit POSIX file ownership, symlink/hardlink behavior and configuration migration; avoid claiming encryption. Improve actionable sanitized diagnostics.
- Review API contract handling, bounded reads, pagination and safe retry behavior where it serves administrative validation. Use loopback tests, not real credentials.
- Improve doctor checks and onboarding checkpoint validation, including clear repair guidance and no unintended remote writes.

## P2 — Distribution and readiness
- Reproducible Gradle distribution, checksums, installation/uninstallation documentation and cross-host smoke tests. Keep personal state outside distributions.
- Document release/version policy; do not pick an open-source license or publish releases without user decision.
- Prepare opt-in authenticated test checklist and redacted evidence format. Live test accounts, OAuth consent, billing and xAI credentials require the user. Never fake live readiness.
- Once administrative scope has been met, report remaining external blockers and stop rather than implement operational job-search features without approval.

## Parallel migration work (separate DevLoop project)
Read devloop.md. Verify and improve the local runner prototype before enabling continuous writes. This is separate from product runtime. Coordinate single-writer cutover from existing ChatGPT automation. User requested local two-minute development cooldown and one-minute CI polling. Do not add DevLoop runtime dependencies to Jobstein.

## Completed baseline
See status.md and architecture.md for CI-verified administrative foundation, profile journal recovery, stored resume onboarding, OAuth/Drive HTTP tests and cross-process locking. Reopen only if a specific defect or requirement warrants it.

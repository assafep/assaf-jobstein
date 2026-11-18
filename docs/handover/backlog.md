# Prioritized development backlog

This is an actionable queue, not a promise that all entries remain bugs. Verify against current code and Git history before making changes. Work through one bounded milestone at a time; fix failing CI first.

## P0 — Correctness and recoverability
- Audit seed JSON and manifest identity: validate expected file ID, parent, MIME, editing/trash status and user ownership on reuse without overwriting mutable remote state. Detect missing local metadata and unsafe adoption; do not silently initialize a new user over an existing user root. Implemented with manifest-backed seed checks, fail-closed missing identity handling, and detection of untracked remote seeds; 75 tests and the sandbox smoke test pass locally. Publication CI for these changes is pending.
- Audit descriptor journal cleanup, explicit regeneration and activation across crash boundaries; protect pending state from accidental replacement, and distinguish recoverable completion from mismatched payloads. Locally implemented and validated on 2026-11-18: recovery checks journal and Drive resource identity, preserves mismatched journals, and covers pre-upload, lost-upload-response, and post-index-update failures without repeated AI calls. Publication CI pending.
- Audit profile-index journal and explicit reconciliation for ownership validation, stale snapshots and lost write responses. Keep the single-writer guarantee explicit; do not claim distributed atomicity.
- Continue application registry ownership and allowed-transition audit; strict persisted entry schema validation is implemented (54 tests and sandbox smoke passed locally; publication CI pending). Preserve user-wide alias deduplication and unknown submission state. No real application submission.

## P1 — Security and operational usability
- Evaluate safe missing-entry semantics for native Linux Secret Service; if reliable, enable only with tested behavior and explicit backend selection. Otherwise document the blocker rather than weaken fail-closed behavior. Returned native command buffers now clear on success and failure without immutable credential Strings (direct synthetic checks passed; full runner validation pending). Credential migration needs an explicit reviewed workflow.
- Audit POSIX file ownership, ancestor symlink behavior and configuration migration; avoid claiming encryption. Credential ancestor symlinks now fail closed (synthetic direct Java checks passed; full runner validation pending). Existing credential hard links now fail closed (synthetic direct Java checks passed; full runner validation pending); concurrent filesystem replacement remains outside the single-writer guarantee. Improve actionable sanitized diagnostics.
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
First-profile onboarding now persists role/slug/UUID before provisioning, revalidates completed checkpoints, and supports explicit legacy selection. Synthetic tests cover lost folder responses, declined review with another active profile, missing/replaced/deactivated profiles and legacy ambiguity. See status.md for verification evidence and architecture.md for repair guidance.

See status.md and architecture.md for CI-verified administrative foundation, profile journal recovery, stored resume onboarding, OAuth/Drive HTTP tests and cross-process locking. Reopen only if a specific defect or requirement warrants it.

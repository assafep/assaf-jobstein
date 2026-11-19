# Prioritized development backlog

This is an actionable queue, not a promise that all entries remain bugs. Verify against current code and Git history before making changes. Work through one bounded milestone at a time; fix failing CI first.

## P0 — Correctness and recoverability
- Audit seed JSON and manifest identity: validate expected file ID, parent, MIME, editing/trash status and user ownership on reuse without overwriting mutable remote state. Detect missing local metadata and unsafe adoption; do not silently initialize a new user over an existing user root. Implemented and CI-verified in run 44308313473.
- Audit descriptor journal cleanup, explicit regeneration and activation across crash boundaries; protect pending state from accidental replacement, and distinguish recoverable completion from mismatched payloads. Implemented and CI-verified in run 44309837248.
- Audit profile-index journal and explicit reconciliation for ownership validation, stale snapshots and lost write responses. Implemented and CI-verified in run 44311843552. Keep the single-writer guarantee explicit; do not claim distributed atomicity.
- Continue application registry ownership and allowed-transition audit; strict persisted entry schema validation and user-bound profile ownership checks are implemented and CI-verified in run 44312911023. No real application submission.

## P1 — Security and operational usability
- Native credential backend audit complete. The owner chose to keep the POSIX file backend. Linux Secret Service remains disabled because `secret-tool` cannot reliably distinguish missing entries from backend errors. Native credential migration is out of scope.
- Audit POSIX file ownership, ancestor symlink behavior and configuration migration; avoid claiming encryption. Credential ancestor symlinks, hard links, and existing path ownership now fail closed with synthetic coverage. Configuration permissions are now audited read-only by doctor with a fixed diagnostic; legacy files are never rewritten implicitly. Concurrent filesystem replacement remains outside the single-writer guarantee, and an explicit reviewed migration workflow remains open.
- Review API contract handling, bounded reads, pagination and safe retry behavior where it serves administrative validation. Implemented and CI-verified in run 44314692321; use loopback tests, not real credentials.
- Improve doctor checks and onboarding checkpoint validation, including clear repair guidance and no unintended remote writes. Implemented and CI-verified in run 44317054671.

## P2 — Distribution and readiness
- Reproducible Gradle distribution, checksums, installation/uninstallation documentation and cross-host smoke tests. Implemented and CI-verified in run 44315396663. Keep personal state outside distributions.
- Release/version policy is documented in [release-policy.md](../release-policy.md). The owner deferred licensing and release publication. The project remains a snapshot.
- Prepare opt-in authenticated test checklist and redacted evidence format. Completed and CI-verified in run 44315972522. Live test accounts, OAuth consent, billing and xAI credentials still require the user; no live readiness is claimed.
- Once administrative scope has been met, report remaining external blockers and stop rather than implement operational job-search features without approval.

## Parallel migration work (separate DevLoop project)
DevLoop has a [separate repository](https://github.com/assafep/devloop). See [the DevLoop handover](devloop.md) for trial results and single-writer cutover requirements.

## Completed baseline
First-profile onboarding now persists role/slug/UUID before provisioning, revalidates completed checkpoints, and supports explicit legacy selection. Synthetic tests cover lost folder responses, declined review with another active profile, missing/replaced/deactivated profiles and legacy ambiguity. See status.md for verification evidence and architecture.md for repair guidance.

See status.md and architecture.md for CI-verified administrative foundation, profile journal recovery, stored resume onboarding, OAuth/Drive HTTP tests and cross-process locking. Reopen only if a specific defect or requirement warrants it.

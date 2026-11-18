# Development status

## Handover baseline
As of 2026-11-17, the observed main revision immediately before the documentation handover was cf5c7e92e7088dc873723fc622247ee1630a2b18. GitHub Actions run 44076509125 completed successfully. This is historical evidence, not a claim about the current head: fetch and inspect CI at every iteration. The handover commit itself requires a new CI check.

## Implemented and previously CI verified
- Administrative command tree, Gradle wrapper, JDK 21 CI and sandbox smoke tests.
- Environment/configuration repositories, atomic file writes, owner-only POSIX state, local environment locks, separate JVM lock tests.
- Gcloud project/API setup, desktop OAuth PKCE/loopback, remote SSH callback instructions, account binding, token cache, safe read retry, no automatic mutation replay.
- Google Workspace REST adapter and loopback tests for authorization, Drive upload/folder conflicts and bounded binary downloads.
- Declarative xAI provider configuration, named integrations/default/agent overrides, masked secrets, loopback HTTP tests.
- Local sandbox implements shared profile/AI workflows with synthetic adapters; failure injection for onboarding.
- Drive user/profile provisioning with stable IDs and durable folder/upload intents; resume versioning, PDF text extraction and descriptor evidence checks.
- Descriptor intent recovery, including a lost profile index write response; stored resume reuse and explicit review on onboarding retry.
- Durable profile index before/after journal, fail-closed divergence protection, inspection, backup, dry-run reconciliation and explicit recovery.
- User-wide application registry and deduplication foundation; no actual job submission runtime.

Key directories: src/main/java/io/github/assafep/jobstein/{cli,ai,google,storage,environment,config,security,provisioning,validation,sandbox}; matching test packages; scripts/; docs/. Read actual code for current behavior.

## Not verified / known gaps
- No authenticated end-to-end Google or xAI development run has been performed. Local HTTP tests do not establish live API compatibility. See ../live-development.md.
- Native Linux Secret Service backend is experimental and deliberately disabled because missing-entry vs unavailable-backend behavior is ambiguous; no credential migration or cross-platform native backend.
- Single local writer only; no distributed CAS, full storage backup/restore, or automatic conflicting-journal merge.
- Manifest/seed identity, descriptor/profile-index journals, application ownership, API bounds, configuration permissions and onboarding checkpoint validation are implemented and covered by the green CI runs recorded below. Live provider compatibility remains unverified.
- Some diagnostics remain generic. Do not expose source data in logs.
- Release packaging/install documentation is complete; license selection and publication authority remain open owner decisions.
- DevLoop initial Python prototype was reported as a separate ZIP outside Git, but it is absent from the repository and scoped project directories and has not been installed or verified on the user's machine. It remains a separate-project blocker.
- Hourly ChatGPT development automation was active when this handover was prepared. It must be coordinated before starting a second writer.

## Update convention
When implementing, append a short factual entry with the change, test commands/results and open limitations. Do not invent the commit SHA before commit creation or claim CI passed until queried. Maintain backlog separately. Private account details must never appear in this file.

## Windows development setup
Documented Ubuntu WSL 2 setup with explicit JDK 21 JAVA_HOME/PATH and Bash persistence instructions. Added Gradle toolchain resolution and labeled intentional smoke-test failures with a final success marker. In an isolated WSL checkout, `./gradlew --no-daemon test installDist` passed all 51 tests without skips; `bash scripts/smoke-test.sh`, live-script syntax checks and `bash scripts/test-live-guards.sh` passed. Native Windows local state remains unsupported because POSIX permissions are required. No live provider verification was performed; publication CI must be checked for the resulting commit.

## Registry schema validation (locally validated; unpublished)
Application reservations and transitions now validate every persisted entry before deduplication or mutation, including cancelled entries: strict schema version, object shape, canonical UUIDs, unique application IDs, known status, timestamp and nonblank textual aliases. User validation also checks the registry schema. Synthetic tests cover corrupt fields, coerced versions, duplicate IDs and preservation of state after rejected mutations. This does not add submission automation or establish remote manifest ownership.

Validation in the restricted agent sandbox: `git diff --check` passed. `./gradlew --offline --no-daemon test installDist` could not start because the Gradle wrapper lock in the user cache is read-only; `bash scripts/smoke-test.sh` could not start the absent distribution. The trusted DevLoop runner subsequently passed all 54 tests without skips using `./gradlew --no-daemon test installDist`, the full sandbox smoke test, live-script syntax checks and live opt-in guard tests. The patch remains staged for first-iteration human review; publication and CI are pending. No live provider validation was performed.

First-profile checkpoint audit (2026-11-17): selection now persists role/slug/UUID before provisioning in a separate private file, retaining compatibility with checkpoint schema version 1. Interrupted folder provisioning reuses the selected UUID; retries reuse uploaded resumes and generated descriptors. Completion and subsequent onboarding calls validate the selected profile's identity, ownership, active status, stored descriptor and user state. Legacy completed checkpoints adopt a sole existing profile or require explicit role selection when ambiguous. Completed checkpoints never recreate missing profiles or request resume/AI/review input. Synthetic tests cover these cases and refusal of conflicting roles/replaced identities. Publication verification is recorded below.

Final onboarding validation: in an isolated Ubuntu WSL checkout using Java 21, `./gradlew --no-daemon test installDist` passed 68 tests (zero failures/errors/skips); `bash scripts/smoke-test.sh` passed with its final success marker and exit 0. Source files were compared with the publication checkout, ignoring CRLF/LF differences. `git diff --check` passed. No live Google/xAI validation was performed. CI for the publication commit must be queried after push; the prior 61-test evidence below describes the earlier partial patch.

Verification blockers resolved (2026-11-17): explicitly select `wsl -d Ubuntu`; the default WSL distribution is Docker Desktop. Ubuntu already has Java 21 and GitHub CLI at `/home/developer/.local/bin/gh`; neither requires a Windows installation. In an isolated Linux checkout with LF text files, `./gradlew --no-daemon test installDist` passed all 61 tests with no skips, and `bash scripts/smoke-test.sh` ended with `PASS: all sandbox smoke checks completed` (exit 0). Native Windows POSIX failures are an unsupported host limitation. `gh run list --workflow ci.yml --commit 6805f7e78c156438fa7950e175a9fd624f1ac9b6` and `gh run view 44099879279` confirm successful CI for current main. Pending working changes remain uncommitted and have no publication CI. README documents explicit Ubuntu invocation. No live provider validation was performed.

## Credential hard-link safety (unpublished)
The file credential backend now rejects multiply linked credential files for get, put and remove, preserving both links. Missing hard-link attribute support fails closed. Added a synthetic JUnit regression covering rejection, unchanged bytes/link identity and normal operation after unlinking the alias. Direct Java 21 compilation and a standalone synthetic exercise of the changed classes passed. Gradle `--offline --no-daemon test installDist` could not start because its wrapper lock is in the read-only user cache; the runner must run the JUnit suite and rebuild the distribution. Native Secret Service remains disabled. This point-in-time check does not address concurrent filesystem replacement, ancestor aliases or ownership validation. No live credentials were accessed.
`bash scripts/smoke-test.sh` passed against the pre-existing distribution; this does not validate the changed credential code. `git diff --check` passed. Publication and new CI remain runner-owned.

## Credential ancestor path safety (runner validation pending)
The POSIX file credential backend now checks each ancestor above the secret root before reads, writes or removals, rejecting symlinks and non-directory components even when the secret root is absent. Missing real ancestors are created with mode 0700 on writes; absent reads/removals create nothing. Synthetic JUnit tests cover preserved credential bytes, rejection before directory creation, and normal missing-parent behavior. Tests canonicalize their temporary root to accommodate host temporary-directory aliases. Configure canonical credential paths; ownership validation and concurrent filesystem replacement remain outside this change. Native Secret Service remains disabled.

Java 21 compilation of the changed production classes and a standalone synthetic check passed. `./gradlew --offline --no-daemon test installDist` was blocked by the read-only Gradle wrapper cache lock; the runner must rebuild and execute JUnit. No network or live credentials were used. Publication and new CI remain runner-owned.
`bash scripts/smoke-test.sh` passed against the pre-existing distribution; it does not validate this patch. `git diff --check` passed. All edits are left unstaged for runner review and validation.

## Native credential result cleanup (runner validation pending)
The experimental Secret Service adapter now clears returned command-output buffers after lookup, store and clear, including nonzero exits and malformed encoding. Lookup decodes from a temporary byte buffer instead of constructing an immutable credential String, and clears that buffer afterward. Synthetic JUnit coverage checks cleanup, safe errors and preservation of caller-owned input. Native onboarding remains disabled: ambiguous missing-entry semantics are unchanged. This is best-effort buffer hygiene, not a guarantee of JVM memory erasure or subprocess cleanup on every transport failure.

Java 21 compilation and a standalone synthetic cleanup exercise passed. `./gradlew --offline --no-daemon test installDist` could not start because the Gradle wrapper cache lock is read-only; the runner must rebuild and run JUnit. No network, real credentials or live native service were used. Edits remain unstaged; publication and new CI are runner-owned.
`bash scripts/smoke-test.sh` passed against the pre-existing distribution; it does not validate this patch. `git diff --check` passed.

## Drive seed and manifest identity audit (2026-11-18; locally validated, publication pending)
Known seeded JSON resources are now checked against their manifest ID, expected parent/name/MIME, trash and editability state, and current-account ownership before reading. Persisted manifest logical keys and Drive IDs are validated. Initialization refuses to create a new local user identity when a marked remote Jobstein root exists without local user metadata. Synthetic tests cover mismatched Drive metadata, invalid manifest entries, and refusal without provisioning side effects.

The project Java 21 daemon criteria was generated and is present in the shared working tree; README now explains daemon selection and local/remote provisioning. In this shell `JAVA_HOME` and the Gradle launcher still use Java 11, while `./gradlew --version` confirms the Gradle daemon criteria selects Java 21. The follow-up audit now refuses to create duplicate seed JSON when local manifest and upload-intent identity are both missing but a remote file with the expected parent/name exists. A retained upload intent may still recover the allocated resource through the existing exact metadata/checksum validation. `./gradlew --no-daemon test installDist --rerun-tasks` passed all 75 tests (zero failures/errors/skips); `bash scripts/smoke-test.sh` passed using the installed Java 21 at `/home/k/.jdks/openjdk-21.0.2`. `git diff --check` passed. These edits are not yet covered by publication CI. No live Google data was accessed.

## Descriptor journal crash recovery audit (2026-11-18; local validation pending publication CI)
Descriptor journal records now reject non-canonical operation IDs and non-object payloads. Descriptor reads, including the post-index-commit cleanup path, validate Drive ID, expected profile folder/name, JSON MIME type, non-trashed/editable state, and current-account ownership before reading contents. Synthetic tests cover failures after intent persistence but before upload, after upload with a lost response, and after profile-index update; they verify retries reuse the persisted payload without another AI call. Mismatched journal payloads and moved descriptor files fail closed without clearing the journal. Activation refuses an incomplete descriptor; onboarding recovers an indexed valid descriptor and clears its journal. Explicit regeneration after completed recovery still creates a new descriptor.

The focused recovery test passed. `./gradlew --no-daemon test installDist --rerun-tasks` passed all 76 tests (zero failures/errors/skips), and `bash scripts/smoke-test.sh` passed. `git diff --check` passed. No live Google data was accessed; publication CI is pending.

## Profile-index journal ownership audit (2026-11-18; locally validated, publication pending)
Pending profile-index intents now validate canonical index IDs, object-shaped before/after snapshots, current-user ownership, profile-key/slug consistency, and unique profile UUIDs before automatic recovery. Both current local and remote index copies are validated before accepting them as synchronized. Invalid foreign-owner journals remain pending and leave the remote index unchanged. Existing stale-snapshot and lost-write-response recovery paths remain covered by synthetic tests.

`./gradlew --no-daemon test installDist --rerun-tasks` passed all 76 tests (zero failures/errors/skips), and `bash scripts/smoke-test.sh` passed with its final success marker. `git diff --check` passed. No live Google data was accessed; publication CI is pending.

## Application registry ownership audit (2026-11-18; locally validated, publication pending)
The live application registry now verifies that reservation and transition profile IDs belong to the initialized user’s profile index. Persisted entries are rechecked before mutations, so a foreign profile reference fails closed without changing the registry. Generic registry instances retain an explicit no-op ownership policy for offline tests; user-bound services supply the ownership validator. No submission automation or live applications were added.

`./gradlew --no-daemon test installDist --rerun-tasks` passed all 77 tests (zero failures/errors/skips), and `bash scripts/smoke-test.sh` passed with its final success marker. `git diff --check` passed. No live Google data was accessed; publication CI is pending.

## Native Secret Service decision (2026-11-18; verified blocker)
The Linux Secret Service adapter remains deliberately disabled. `secret-tool lookup` returns a nonzero status both for a missing entry and for some unavailable/failing keyring conditions, so the adapter cannot safely distinguish absence from backend failure. Enabling it would weaken fail-closed credential handling. Explicit backend selection continues to reject this experimental backend and refuses implicit fallback or migration. Synthetic coverage verifies namespace isolation, missing-entry failure, malformed output handling, command-output cleanup, and encoded-input cleanup; the full 77-test suite and sandbox smoke test pass, including CI for the preceding commit. No native keyring or real credential was accessed.

## POSIX credential ownership audit (2026-11-18; locally validated, publication pending)
The file credential backend now verifies that existing secret roots, nested secret directories, and credential files are owned by the current user, in addition to the existing mode, symlink, ancestor, and hard-link checks. Missing ownership metadata fails closed. Synthetic tests cover ownership of newly created paths; existing hard-link and ancestor safety tests remain green. This is local ownership validation under the single-writer model and does not prevent concurrent replacement races or provide encryption.

`./gradlew --no-daemon test installDist --rerun-tasks` passed all 77 tests (zero failures/errors/skips), and `bash scripts/smoke-test.sh` passed with its final success marker. No real credentials were accessed; publication CI is pending.

## Workspace API bounds audit (2026-11-18; locally validated, publication pending)
Workspace JSON responses are now bounded to 5 MB, preventing unbounded response buffering. Gmail message and Calendar event listings request at most 100 results per call. Existing read-only 401 retry behavior and no automatic mutation replay remain unchanged. Loopback tests cover oversized JSON rejection, listing limits, token refresh, retry exhaustion, and bounded binary downloads.

`./gradlew --no-daemon test installDist --rerun-tasks` passed all 80 tests (zero failures/errors/skips), and `bash scripts/smoke-test.sh` passed with its final success marker. No live Workspace data was accessed; publication CI is pending.

## Distribution packaging audit (2026-11-18; locally validated, publication pending)
Gradle archive tasks now normalize file ordering and timestamps for reproducible ZIP/TAR output. `distributionChecksums` writes SHA-256 sidecars for both archives. `scripts/verify-distribution.sh` verifies both checksums, extracts each archive, and runs the CLI help command; it reports a clear Java 21 prerequisite because the packaged classes require Java 21. README documents packaging, checksum verification, and removal of unpacked distributions while keeping user state outside the distribution.

`./gradlew --no-daemon test installDist --rerun-tasks` passed all 80 tests (zero failures/errors/skips), `bash scripts/smoke-test.sh` passed, and `JAVA_HOME=/home/k/.jdks/openjdk-21.0.2 bash scripts/verify-distribution.sh` passed. No personal state or credentials were packaged; publication CI is pending.

## Live validation evidence checklist (2026-11-18; documentation only)
Added a manual, opt-in development validation checklist with explicit disposable-account/project preconditions, read-validation-only scope, cleanup steps, and a redacted evidence template. It does not claim live verification and does not authorize production operations, mail, applications, or unattended cloud provisioning. No live credentials or external accounts were accessed.

## Doctor and onboarding checkpoint validation (2026-11-18; local validation pending publication CI)
Doctor now validates the local onboarding checkpoint journal before provider probes. Validation is read-only and rejects unsupported checkpoint IDs, malformed state, and completed first-profile checkpoints without a persisted selection. A fixed diagnostic explains how to preserve and repair the affected local state; no checkpoint or remote Workspace resource is rewritten by doctor. README, sandbox instructions and architecture documentation describe the behavior. Targeted Java 21 tests passed; full suite, distribution build and sandbox smoke test remain required before publication.

## Configuration permission audit (2026-11-18; local validation pending publication CI)
Configuration doctor probes now audit the environment directory and existing YAML files for real-path, current-user ownership and owner-only POSIX permissions. Unsafe legacy files fail closed with a fixed repair diagnostic and remain unchanged; no implicit migration or permission rewrite was added. Synthetic coverage verifies a permissive legacy file is reported without modification. An explicit reviewed migration workflow remains open, and POSIX access control is not encryption.
New environment directories are created owner-only so a fresh environment does not fail its first read-only audit.

## Release/version policy (2026-11-18; documentation only)
Added `docs/release-policy.md` and a README link. The project remains `0.1.0-SNAPSHOT`; release gates require Java 21 tests, install/smoke/distribution verification, an exact CI-green commit and checksums. No license was selected, no release was published, and the owner must decide licensing and release terms. Persisted-state migrations remain explicit and fail closed rather than implicit.

## Handover reconciliation (2026-11-18)
The recent administrative audits are now publication-verified: seed identity (44308313473), descriptor recovery (44309837248), profile-index ownership (44311843552), application registry ownership (44312911023), credential ownership (44313986210), Workspace bounds (44314692321), distributions (44315396663), live checklist (44315972522), onboarding doctor checks (44317054671), configuration permissions (44318335482), and release policy (44318996931). Remaining blockers are external: an explicit reviewed credential migration requires a supported native backend and owner choice, and the separate DevLoop prototype is unavailable for verification. No operational job-search automation or live provider claim is authorized.

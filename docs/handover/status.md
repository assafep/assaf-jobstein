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
- Recovery/validation of all manifest identities and seed metadata needs further audit; folder markers and state ownership must be respected. Completed onboarding checkpoints and selected first role need stronger validation.
- Some diagnostics remain generic. Do not expose source data in logs.
- Release packaging/install documentation and license choice remain open.
- DevLoop initial Python prototype was delivered as a separate ZIP outside Git, with four offline tests reported passing; it was not published as a separate repository or installed/verified on the user's machine. The repository does not contain that prototype at this baseline.
- Hourly ChatGPT development automation was active when this handover was prepared. It must be coordinated before starting a second writer.

## Update convention
When implementing, append a short factual entry with the change, test commands/results and open limitations. Do not invent the commit SHA before commit creation or claim CI passed until queried. Maintain backlog separately. Private account details must never appear in this file.

## Windows development setup
Documented Ubuntu WSL 2 setup with explicit JDK 21 JAVA_HOME/PATH and Bash persistence instructions. Added Gradle toolchain resolution and labeled intentional smoke-test failures with a final success marker. In an isolated WSL checkout, `./gradlew --no-daemon test installDist` passed all 51 tests without skips; `bash scripts/smoke-test.sh`, live-script syntax checks and `bash scripts/test-live-guards.sh` passed. Native Windows local state remains unsupported because POSIX permissions are required. No live provider verification was performed; publication CI must be checked for the resulting commit.

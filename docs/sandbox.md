# Online sandbox

Use GitHub Codespaces with the repository dev container. It installs JDK 21 and builds the CLI with the pinned Gradle wrapper. No Google or xAI credentials are needed for simulated testing.

```bash
bash scripts/setup-sandbox.sh
export PATH="$PWD/build/install/jobstein/bin:$PATH"
jobstein onboard --env sandbox
jobstein doctor --env sandbox
jobstein profile list --env sandbox
jobstein profile create --env sandbox --role "Product Manager"
jobstein profile descriptor show --env sandbox --profile product.manager
bash scripts/smoke-test.sh
```

The reserved sandbox environment routes administrative commands to a local simulated service before constructing live adapters. AI configuration remains under ~/.config/jobstein/environments/sandbox/simulation; shared profile service data and synthetic PDFs live under workspace-v2 within the same sandbox environment. Run onboarding again after upgrading. Old simulated profiles remain untouched but are not migrated. The fixture uses a synthetic applicant, mock model and generated descriptor. The CLI now uses real PDF processing and the shared user/profile service with local storage and mock AI. No external OAuth, email, calendar or Drive requests occur. It is not a test of live external adapters. Named synthetic AI integrations and per-agent overrides use the same AiService, provider validation and resolver as live mode, with an injected offline provider. Resume versioning, descriptor invalidation, regeneration and approval are supported with synthetic fixtures. Optionally pass a synthetic PDF using --resume; its content is validated and stored locally using the same PDF reader and profile service as live mode, but not sent anywhere. Use synthetic files only. When regenerating a descriptor for a user-supplied PDF, supply the same file again with --resume.

For live testing use a separate development environment. Google desktop OAuth requires a browser that can reach its loopback callback; remote Codespaces OAuth is not configured here. The dev container does not install gcloud. Use a local machine with gcloud for current live onboarding.

## AI integration experimentation

```bash
jobstein ai integration create --env sandbox --integration advanced --field model=mock-grok-advanced
jobstein ai agent configure --env sandbox --agent applicant-profile-generator --integration advanced
jobstein ai agent show --env sandbox --agent applicant-profile-generator
jobstein ai agent reset --env sandbox --agent applicant-profile-generator
jobstein ai integration remove --env sandbox --integration advanced
```

## Resume lifecycle

```bash
jobstein profile resume update --env sandbox --profile software.engineer
jobstein profile resume history --env sandbox --profile software.engineer
jobstein profile descriptor regenerate --env sandbox --profile software.engineer
jobstein profile activate --env sandbox --profile software.engineer --approve-descriptor
```

## Failure and recovery

On a fresh sandbox, run:

```bash
jobstein onboard --env sandbox --simulate-failure ai-configure
jobstein doctor --env sandbox
jobstein onboard --env sandbox
```

The first invocation fails before executing the specified checkpoint. Doctor reports incomplete provisioning. The final invocation resumes from the failed checkpoint. Supported checkpoints: google-configure, google-authorize, ai-configure, user-initialize, first-profile. Already completed checkpoints are skipped; the flag is invocation-local and rejected for live environments and other commands. On an already provisioned sandbox, no completed checkpoint is deliberately rerun.

## Concurrent commands

Administrative commands acquire a non-blocking exclusive OS file lock per local environment. A competing command exits with code 4; retry after the first command finishes. The lock file is intentionally retained and the operating system releases locks when a process exits. This is local single-host protection, not a distributed Google Drive lock. Do not operate the same live environment from multiple machines.

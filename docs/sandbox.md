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

For live testing use a separate development environment. Google desktop OAuth can use a private local SSH tunnel as described below. The dev container does not install gcloud; install and authenticate it separately for live provisioning.

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

## Remote desktop OAuth (experimental live workflow)
Use a trusted workstation with GitHub CLI, OpenSSH and a browser. Use a dedicated test Google account/project in a private codespace. Install a current GitHub CLI; older versions before 2.98.0 have a port-forwarding exposure advisory. Prefer explicitly loopback-bound SSH forwarding:

On the workstation (keep this terminal open):

```sh
gh codespace ssh -c YOUR_CODESPACE -- -N -L 127.0.0.1:8765:127.0.0.1:8765 -o ExitOnForwardFailure=yes
```

Inside the codespace, after configuring the development Google project and desktop OAuth client:

```sh
jobstein google authorize --env development --oauth-callback-port 8765 --no-browser
```

Open the printed Google authorization URL in the workstation browser. The Google redirect targets http://127.0.0.1:8765/callback and the tunnel carries it to the Jobstein listener inside the codespace. Do not publish the port, change the redirect host, copy the authorization code, or share the authorization URL. Close the tunnel after authorization. The callback checks state and uses PKCE. A port collision fails rather than selecting another port. The same options can be passed to onboard; the SSH tunnel must be established beforehand. No real Google credentials should be used in the synthetic sandbox. Keep credentials outside the checkout and remove live test state when finished. This workflow has not yet been exercised against Google in a real codespace.

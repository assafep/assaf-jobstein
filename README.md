# Jobstein

Self-hosted, local-first AI-assisted job search automation with user-owned Google infrastructure.

## Try the credential-free sandbox

[Open in Codespaces](https://codespaces.new/assafep/assaf-jobstein)

```bash
bash scripts/setup-sandbox.sh
export PATH="$PWD/build/install/jobstein/bin:$PATH"
jobstein onboard --env sandbox
jobstein doctor --env sandbox
jobstein profile list --env sandbox
```

See [sandbox instructions](docs/sandbox.md). The simulated environment does not contact external services.

## Live development testing (explicit opt-in)

See [live development instructions](docs/live-development.md) for Google Cloud CLI installation, dedicated test project authorization and the guarded live validation script. The regular sandbox and CI remain credential-free.

## Development status

Administrative CLI and live Google/xAI adapters are implemented but live end-to-end authorization and provisioning remain unverified. Operational job search is deferred.

## Build

Requires JDK 21. The Gradle wrapper downloads and runs the pinned Gradle version, so a separate Gradle installation is not needed. Run commands from the repository root; the first wrapper run needs network access to download Gradle and dependencies.

```sh
./gradlew test installDist
bash scripts/smoke-test.sh
```

See [architecture](docs/architecture.md). Never commit credentials, resumes or personal application data.

## Continue development with Codex

Start Codex from the root of a dedicated clone and say `keep going`. [AGENTS.md](AGENTS.md) defines the CI-gated development workflow and [the handover](docs/handover/README.md) contains requirements, implementation status, prioritized backlog and the local DevLoop migration plan. No prior ChatGPT transcript is required. Coordinate with any active remote development automation before enabling unattended local publication.

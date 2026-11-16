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

## Development status

Administrative CLI and live Google/xAI adapters are implemented but live end-to-end authorization and provisioning remain unverified. Operational job search is deferred.

## Build

Requires JDK 21 and Gradle 8.10 or later.

```sh
gradle test installDist
bash scripts/smoke-test.sh
```

See [architecture](docs/architecture.md). Never commit credentials, resumes or personal application data.

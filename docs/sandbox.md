# Online sandbox

Use GitHub Codespaces with the repository dev container. It installs JDK 21 and Gradle and builds the CLI. No Google or xAI credentials are needed for simulated testing.

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

The reserved sandbox environment routes administrative commands to a local simulated service before constructing live adapters. State lives under ~/.config/jobstein/environments/sandbox/simulation. The fixture uses a synthetic applicant, mock model and generated descriptor. No real PDF, OAuth, email, calendar or Drive requests occur. This is a CLI demonstration, not an integration test of real external adapters. Integration mutation and resume upload/regeneration are not supported in this first simulation.

For live testing use a separate development environment. Google desktop OAuth requires a browser that can reach its loopback callback; remote Codespaces OAuth is not configured here. The dev container does not install gcloud. Use a local machine with gcloud for current live onboarding.

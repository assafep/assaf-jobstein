# Live development integration testing

The ordinary sandbox never calls Google or xAI. This separate workflow uses real APIs and may incur costs. Use a disposable GCP project, a dedicated test Google account and synthetic resume data only. Never use production credentials, a personal resume or a production project. The scripts do not prove that an account/project is disposable; verify that yourself.

## 1. Prepare the environment

Inside a private Codespace or local Debian/Ubuntu workstation:

```sh
bash scripts/setup-live-tools.sh
./gradlew test installDist
export PATH="$PWD/build/install/jobstein/bin:$PATH"
gcloud auth login --no-launch-browser
bash scripts/live-preflight.sh
```

Follow the official gcloud login prompts on a trusted workstation. Do not share login codes or copy credentials into the repository. The setup script installs Google's CLI from its signed apt repository; it does not authenticate or create projects. Existing gcloud installations are reused. Other OSes: https://cloud.google.com/sdk/docs/install

## 2. Provision and configure the Google project

Choose a globally unique development project ID. Enable billing if required by your project or organization. Google Auth Platform branding, audience, test users, scopes and desktop OAuth client creation require manual configuration. Download the desktop client JSON into a private directory outside the repository.

```sh
jobstein environment create --env development
jobstein google configure --env development --project YOUR_TEST_PROJECT --create-project --oauth-client "$HOME/private/oauth-client.json"
```

For an existing project omit --create-project. Do not reuse a production project. Follow the [remote OAuth instructions](sandbox.md#remote-desktop-oauth-experimental-live-workflow) if using Codespaces, or authorize locally:

```sh
jobstein google authorize --env development
jobstein google validate --env development
```

## 3. Configure AI and the synthetic applicant

```sh
jobstein ai configure --env development
jobstein user initialize --env development --name "Example Applicant"
jobstein profile create --env development --role "Software Engineer" --resume /absolute/path/to/synthetic-resume.pdf
```

Use a synthetic text-based PDF. Enter provider credentials only through the masked interactive prompt. Profile creation may send resume content to the configured AI provider upon your explicit consent; review the generated descriptor before approving activation. Do not use real applicant information. Neither the app nor the script creates an xAI account or supplies credentials.

## 4. Explicitly run live validation

```sh
JOBSTEIN_LIVE_TESTS=1 bash scripts/live-smoke-test.sh --confirm-development development
```

Only the exact development environment is accepted. The script performs environment, Google, AI-model, user/index and doctor validation; it does not generate descriptors, send mail, or create remote resources. Normal CLI operations may write local credential-backend metadata and lock files. Ordinary CI only checks the opt-in guards and runs the credential-free sandbox.

Record test date, commit and pass/fail checks without account IDs, file IDs, resume content or credentials. Delete or revoke disposable test resources manually when finished. Do not delete the Codespace until necessary state has been safely preserved outside Git.

## Current verification status

The live workflow is supplied for manual execution. No authenticated Google/xAI requests, Codespace provisioning, GCP billing changes or end-to-end live runs have been performed by this repository change.

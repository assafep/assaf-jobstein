# Live validation checklist

This checklist is for a manually authorized development-environment run. It does not authorize production access, job applications, email sending, calendar mutation, or unattended cloud provisioning. Use a disposable Google account, a disposable development GCP project, synthetic resume data, and a separately configured AI integration.

## Before the run

- Confirm the checkout commit and record it in the evidence template below.
- Confirm the named environment is intended for development testing. The script rejects the reserved names `production` and `sandbox`.
- Confirm the Google account and GCP project are disposable and that billing limits are understood.
- Confirm OAuth client JSON, refresh tokens, AI credentials, and local state are outside Git and outside Drive.
- Run `./gradlew test installDist` and `bash scripts/smoke-test.sh`.
- Complete [live onboarding](onboarding.md#set-up-a-live-environment) for the development environment. Use an existing configured environment when repeating validation.
- Run `bash scripts/live-preflight.sh` and resolve every failed check.

## Authorized run

After the preconditions pass, run the guarded script with your chosen environment name. The `--confirm-development` flag confirms that the named environment is safe for development testing.

```sh
JOBSTEIN_LIVE_TESTS=1 bash scripts/live-smoke-test.sh --confirm-development project-dev
```

The guarded script performs read and validation operations for environment, Google, AI, user state, profile state, and doctor checks. It does not generate descriptors, send mail, submit applications, or create remote resources.

## After the run

- Record only pass/fail outcomes and sanitized diagnostic codes.
- Revoke the disposable OAuth grant and clean up the disposable GCP project and user-owned Drive files separately.
- After revoking access and preserving any required test evidence, remove only the disposable development environment directory and its downloaded OAuth client. Verify the paths first.
- Never commit logs, OAuth URLs, account addresses, project IDs, Drive IDs, resume text, tokens, or provider responses.

## Redacted evidence template

```text
date_utc: YYYY-MM-DDThh:mm:ssZ
commit: <40-character commit SHA>
host: <generic label, no username or hostname>
java: 21
environment: <development environment name>
google_account: disposable-account-<redacted>
gcp_project: disposable-project-<redacted>
ai_provider: <provider name only>
checks:
  environment_validate: PASS|FAIL <diagnostic code or redacted reason>
  google_validate: PASS|FAIL <diagnostic code or redacted reason>
  ai_validate: PASS|FAIL <diagnostic code or redacted reason>
  user_validate: PASS|FAIL <diagnostic code or redacted reason>
  state_validate: PASS|FAIL <diagnostic code or redacted reason>
  doctor: PASS|FAIL <diagnostic code or redacted reason>
live_requests: read-validation only
descriptor_generated: no|yes-with-explicit-consent
mail_sent: no
applications_submitted: no
cleanup: pending|complete
```

This evidence proves only the checks recorded for that disposable development run. It does not establish production readiness or live compatibility beyond the tested account, project, commit, and time.

## Live onboarding result

On November 23, 2026, live onboarding was verified for `kumetix-dev` using the bundled Java 21 launcher built from commit `bc31e8cd051159dc42b34151479f321f78d9256c`. A fresh descriptor reached review, the operator approved it, and `profile list` reported `senior.software.engineer ACTIVE`.

`jobstein doctor --env kumetix-dev` exited with code 0 and reported all eight checks as passing.

| Check | Result |
| --- | --- |
| Local environment | PASS |
| Local configuration permissions | PASS |
| Onboarding checkpoint state | PASS |
| GCP project and enabled APIs | PASS |
| Google OAuth and Workspace read access | PASS |
| AI integrations and model availability | PASS |
| User identity and shared Drive state | PASS |
| Applicant profiles | PASS |

Doctor reported `Configured checks passed (no email sent)`. This record covers live onboarding, descriptor approval, and the doctor checks. The guarded live smoke script has a separate validation procedure above.

## Guarded live smoke attempt

The guarded script was invoked for `kumetix-dev` with live opt-in on commit `b4308a4878104d0782685d4a5fe87ce1fc50bf57`.

| Stage | Result |
| --- | --- |
| Environment guard | Rejected with exit code 2. The script accepts only the literal environment name `development`. |
| Preflight | Not run |
| Environment, Google, AI, user, state, and doctor checks | Not run |

The attempt stopped before any live requests. The successful doctor result above came from the live onboarding verification.

## Guarded live smoke result

On November 23, 2026, the updated script ran against `kumetix-dev` with explicit live opt-in and exited with code 0. The bundled Java 21 launcher was built from `b4308a4878104d0782685d4a5fe87ce1fc50bf57`. The environment guard and check reporting changes were in the working tree at the time of the run.

| Check | Result |
| --- | --- |
| Preflight | PASS |
| Environment validation | PASS |
| Google validation | PASS |
| AI validation | PASS |
| User validation | PASS |
| State validation | PASS |
| Doctor | PASS |

Doctor reported `Configured checks passed (no email sent)`. The run used validation requests only.

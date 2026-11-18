# Live validation checklist

This checklist is for a manually authorized development-environment run. It does not authorize production access, job applications, email sending, calendar mutation, or unattended cloud provisioning. Use a disposable Google account, a disposable development GCP project, synthetic resume data, and a separately configured AI integration.

## Before the run

- Confirm the checkout commit and record it in the evidence template below.
- Confirm the environment is `development`, never `production` or `sandbox`.
- Confirm the Google account and GCP project are disposable and that billing limits are understood.
- Confirm OAuth client JSON, refresh tokens, AI credentials, and local state are outside Git and outside Drive.
- Run `./gradlew test installDist` and `bash scripts/smoke-test.sh`.
- Run `bash scripts/live-preflight.sh` and resolve every failed check.

## Authorized run

Run only after the preconditions pass:

```sh
JOBSTEIN_LIVE_TESTS=1 bash scripts/live-smoke-test.sh --confirm-development development
```

The guarded script performs read and validation operations for environment, Google, AI, user state, profile state, and doctor checks. It does not generate descriptors, send mail, submit applications, or create remote resources. If a separate manual onboarding run is needed, obtain explicit consent for AI resume processing and review the descriptor before activation.

## After the run

- Record only pass/fail outcomes and sanitized diagnostic codes.
- Revoke the disposable OAuth grant and remove the disposable GCP project/resources manually when finished.
- Remove local live state and credential references using the environment’s documented cleanup procedure.
- Never commit logs, OAuth URLs, account addresses, project IDs, Drive IDs, resume text, tokens, or provider responses.

## Redacted evidence template

```text
date_utc: YYYY-MM-DDThh:mm:ssZ
commit: <40-character commit SHA>
host: <generic label, no username or hostname>
java: 21
environment: development
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

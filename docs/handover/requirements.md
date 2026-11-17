# Agreed product requirements

Jobstein is an open-source, local-first, self-hosted AI-assisted job-search system. The current milestone is the administrative CLI and its reliable provisioning/validation. Actual autonomous job discovery, correspondence and application submission, operational start/stop, scheduling, and email/calendar processing loops are future scope and must not be implemented without new user direction.

## Environment and identity
Each cloner controls their own Google infrastructure. One GCP project per live environment (development/production), not per role. Sandbox is offline and credential-free. Desktop OAuth for user-owned Workspace resources; no service accounts for Workspace. Gcloud administrative authentication is separate. Enable Drive, Docs, Gmail and Calendar APIs. OAuth branding/audience/client setup and user authorization are explicit manual checkpoints. Scopes: openid, userinfo.email, drive.file, gmail.readonly, gmail.compose, calendar.events.readonly. Testing-mode refresh tokens may expire after seven days. Separate test/production accounts recommended. Store credentials outside Git and Drive; OS credential storage preferred, POSIX protected local fallback supported. No implicit secret-backend switching.

## User and profiles
Production Drive layout: jobstein/<user-slug>/_shared and jobstein/<user-slug>/<role-slug>. Nonproduction roots are environment-specific. Stable user UUID and authenticated account subject; independent role profiles with stable UUID, target role, resume versions/fingerprints, descriptor and preferences, artifacts and active/completed application directories. Store actual Drive IDs in manifests. User-level shared registry, profile index, Gmail/Calendar cursors, preferences and automation policy. Dedupe across roles using canonical posting identity (source, tenant, ID plus aliases); reserve before submitting; unknown outcomes need reconciliation before retry. Single local writer initially; Drive JSON is not transactional.

## AI
Declarative provider fields and capabilities, generic configuration wizard, named integrations with provider-specific credentials, model and options (e.g. reasoning effort). Default integration and per-agent override bind the complete integration; invalid overrides fail closed. xAI initially, future protocols require adapters. Resume processing requires explicit consent. Descriptor schema version 2 is deliberately extractive, with verbatim evidence, provider/model, profile identity and resume SHA-256. Validate before persistence and again using stored PDF before activation. Require explicit human approval. Evidence validation is lexical, not semantic.

## Administrative CLI
- onboard, doctor
- environment create/list/show/validate
- google configure/authorize/status/validate
- ai configure/validate; ai provider list/models
- ai integration create/update/list/show/validate/remove
- ai agent configure/list/show/reset
- user initialize/show/validate
- profile create/list/show/validate/activate/deactivate
- profile resume upload/update/history
- profile descriptor show/regenerate/validate
- state status/validate/backup/reconcile; state recovery inspect/resume

Onboarding order: environment -> Google project/APIs -> desktop OAuth -> AI -> user state -> first profile -> resume -> explicit AI consent -> validated descriptor -> explicit review -> doctor. Resumable/idempotent; preserve prior state and account. Doctor should not send mail or modify remote state. CLI error output should be safe and actionable.

## Engineering
Java 21, Gradle Kotlin DSL, Picocli, Jackson YAML/JSON, PDFBox, JUnit 5, constructor injection. Current project is single-module; do not split without a clear benefit. Prefer Optional-like API contracts for new code. Tests use synthetic data and offline adapters. CI tests and smoke tests are a required gate. License selection and release strategy remain open user decisions.

# Jobstein architecture

## Scope
Local-first, self-hosted administrative CLI. Operational start/stop and autonomous job-search execution are deferred.

## Boundaries
- Environment: independent development/production GCP project, OAuth grants, AI integrations, secret namespace and state root.
- User: stable identity, shared job application registry, profile index, Gmail/Calendar cursors.
- Applicant profile: stable UUID, target role, resume versions, validated descriptor, active/completed applications.
- GCP organization is optional; one project per environment, not per applicant profile.
- Google Drive data is user-owned, not a GCP resource. Use desktop OAuth and app-created files with drive.file. Gmail readonly/compose and calendar events readonly scopes. No API keys or service accounts for Workspace access.
- Manual OAuth branding/audience/client creation and user consent remain explicit checkpoints. Testing-mode refresh tokens may expire after seven days.
- Separate Google accounts for development and production are recommended.

## Commands (target scope)
onboard; doctor; environment create/list/show/validate; google configure/authorize/status/validate; ai configure/validate; ai provider list/models; ai integration create/update/list/show/validate/remove; ai agent configure/list/show/reset; user initialize/show/validate; profile create/list/show/validate/activate/deactivate; profile resume upload/update/history; profile descriptor show/regenerate/validate.

## Onboarding order
Environment -> Google project and APIs -> OAuth client import and consent -> AI default and optional bindings -> user Drive state -> first profile -> resume import -> consent to AI processing -> descriptor generation/schema and evidence validation -> user review -> doctor -> ready.
All stages must be resumable and idempotent; never overwrite existing application state during onboarding.

## AI
Declarative provider catalog describes fields and capabilities. xAI only initially. Named integrations specify provider, credential reference, model and provider-specific options. Agent binding resolves a whole integration, otherwise defaults; invalid explicit bindings fail closed. Future incompatible protocols require adapters, not wizard changes. Model availability and reasoning options must be checked against provider support.

## Storage
Production: jobstein/<user-slug>/_shared/ and jobstein/<user-slug>/<role-slug>/.
Development: independent account or jobstein-dev root. Use actual Drive IDs and immutable UUIDs, not paths as identifiers. Shared registry keyed by user and canonical posting ID, retaining completed applications and aliases. Reserve before submission. Single writer initially; Drive JSON is not a transactional database. Unknown submission outcomes must be reconciled before retry.
Profile holds original resume versions/hashes, descriptor JSON, job preferences and application artifacts.
Keep secrets outside Git and Drive. OS credential store preferred; protected local fallback. Doctor is non-destructive, never sends email by default.

## Implementation stages
1. Build and CLI foundation.
2. Config, secrets, environment lifecycle and shared validation contracts.
3. Google provisioning and OAuth/Workspace adapters.
4. xAI adapter, declarative onboarding and agent resolution.
5. Drive user/profile/registry and resume descriptor workflows.
6. Compose onboarding and doctor, integration tests and CI.

## Status
This document specifies target behavior, not implemented functionality. License and final packaging decisions remain open.

## Provisioning recovery
Folder and binary/JSON creation uses persisted allocated Drive IDs. Existing legacy folders may be adopted by their stable marker after metadata validation. Upload conflicts are accepted only with matching metadata and SHA-256 checksum. Descriptor generation journals the exact generated payload locally until its upload and profile index update complete; retry does not call AI again while that journal exists. A new explicit regeneration after completion produces a new descriptor. Finish any pending descriptor generation before replacing its resume. These protections assume a single local environment writer, and do not replace live integration testing or distributed locking.

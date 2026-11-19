# Jobstein

Jobstein is a self-hosted, AI-assisted job-search system. One user can maintain several role-specific applicant profiles in their own Google Drive. Each profile has its own resume history and reviewed descriptor. All profiles share one job application registry so the same opening can be recognized across roles.

The **Jobstein runtime** is the software that reads and writes an environment's state. Today it runs for the duration of a CLI command. You can provision an environment, manage profiles and AI integrations, check readiness, and recover interrupted index updates. Continuous job search, scheduling, and starting or stopping a long-running runtime are planned.

## Using Jobstein

| I want to… | Read |
| --- | --- |
| Try the offline sandbox or prepare my first live environment | [Onboarding](docs/onboarding.md) — setup, approval, and what to expect on disk and in Drive |
| Work with an existing environment | [Operations](docs/operations.md) — readiness, profile changes, AI settings, and recovery |
| Understand how the pieces fit together | [Architecture](docs/architecture.md) — data flow, state transitions, agent roles, and the planned document UI |

## Developing Jobstein

The project builds with Java 21 and the Gradle wrapper. The test suite uses synthetic data, including local HTTP tests for the Google and xAI adapters. Authenticated end-to-end validation is still outstanding.

- [Development](docs/development.md) covers building, testing, packaging, and Windows development under WSL.
- [Live validation](docs/live-validation-checklist.md) covers opt-in testing with disposable accounts.
- [Open work](docs/backlog.md) and [release policy](docs/release-policy.md) record pending decisions.

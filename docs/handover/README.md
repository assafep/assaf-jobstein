# Continuing Jobstein development

This directory replaces the prior chat as the portable engineering handover. Start a Codex session from the repository root and say **keep going**. Root AGENTS.md defines the workflow and boundaries; this directory records the product decisions, verification baseline and priorities. The repository is public, so this handover intentionally excludes private resumes, credentials, personal account information and the original private job-search guide.

## Reading order
1. ../../AGENTS.md (mandatory agent workflow and publication policy).
2. requirements.md (agreed administrative product scope).
3. ../architecture.md (technical architecture and detailed behavior).
4. status.md (baseline, known limitations and verification).
5. backlog.md (ordered work).
6. devloop.md (local persistent coding agent migration, a separate project).
7. ../sandbox.md and ../live-development.md (execution and validation).

## Immediate continuation
First inspect the actual current main and CI. Repair failing CI before new features. Then address the highest-priority actionable backlog issue. Never assume the baseline in status.md is still the latest commit. Update status and backlog as part of meaningful changes. The hourly ChatGPT development automation was active at handover; coordinate the local cutover before enabling unattended auto-push.

## Local prerequisites
Linux/WSL, Java 21, Git, authenticated GitHub CLI, Codex CLI, network access for Gradle dependencies and GitHub. Use a dedicated clone. Existing project uses the checked-in Gradle wrapper. Sandbox requires no Google/xAI credentials. See devloop.md for local setup and safety requirements.

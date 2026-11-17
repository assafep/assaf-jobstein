# Local Codex / DevLoop migration

The user wants to continue the prior ChatGPT engineering session on their own development machine, using Codex CLI in WSL Ubuntu (verify actual distribution and prerequisites). They also want a reusable persistent development orchestrator named DevLoop, independent of Jobstein. The existing ChatGPT automation is hourly; local execution should support a 120-second cooldown after each completed iteration and 60-second CI polling. These are not Jobstein job-search scheduling features.

## Continue interactively now
In a dedicated clone, authenticate GitHub and Codex locally as needed, check JDK 21, then launch Codex from the Jobstein repository root. Say "keep going". AGENTS.md and this directory supply the full portable development context. Do not expect the old chat history to be available. The initial direct session can run without DevLoop.

Example:
```sh
git clone https://github.com/assafep/assaf-jobstein.git
cd assaf-jobstein
gh auth status
java -version
codex
```
If a clone already exists, inspect it instead of cloning over it. Do not copy authentication tokens into the repository.

## DevLoop prototype and expected deliverables
A separate Python package named devloop.zip was delivered in ChatGPT, containing source, install.sh, example Jobstein JSON configuration, a systemd user service, README and tests. Four offline tests were reported passing; no live Codex or local installation was verified. The ZIP is not part of this Git repository; locate the downloaded package locally, inspect it and reuse it if available. If absent, report that and implement the requirements in a separate workspace rather than assuming a file exists. Do not make Jobstein depend on DevLoop. A separate repository can be created with the user's authorized GitHub account; never publish credentials or local state. Verify existing repos before creating one.

Required management interface: project add/configure/validate (or equivalent add/configure/doctor); start/stop/restart/pause/resume/status/logs/history/run-once/doctor. Multi-project config; dedicated clones; persistent state and logs; process locking and crash recovery; configurable interval and CI polling; safe local builds, commits and non-force pushes; no overlap per repo. Python standard library was selected for the initial implementation. Prefer a small reusable runner, not a new application framework.

Runner workflow:
1. Lock repository/project and check clean dedicated clone, fetch/fast-forward, check exact remote SHA CI.
2. Wait for CI every 60 seconds; repair failed CI before features; fail closed for unknown/missing CI. Avoid infinite blocking on impossible state.
3. Run Codex non-interactively (verify installed CLI syntax via codex exec --help). Give it the repository AGENTS.md and one scoped task. In runner mode, delegate publication to exactly one owner: either runner or agent, not both. Explicitly instruct agent not to commit/push if runner owns those operations.
4. Independently validate diff, local tests, secret exclusion and allowed paths. Preserve work on failure. Review-only by default. Human review of first iteration required before auto-push.
5. Commit/push safely after checking remote has not moved. Verify exact new SHA CI. Record outcomes, blockers and progress, then wait 120 seconds before next iteration. No gratuitous tasks when scope is exhausted.
6. Persistent user systemd service if supported; verify systemd/WSL lifecycle, keep machine awake and online, stop/pause controls, log permissions and shutdown recovery. Don't assume enabling a WSL user service automatically starts Windows or WSL at boot.

## Cutover safety
The hourly ChatGPT automation was active at handover and may have modified main since then. Before enabling unattended local publication, ask the user to pause that automation and verify any in-flight run is complete. Do not allow it and local DevLoop to write concurrently. Review-only local trial may be done first; resync before enabling publication. The local CLI does not have guaranteed ability to manage ChatGPT automations. User can manage automation through ChatGPT. Verify all actual installation and test results before declaring cutover successful.

Use dedicated Linux account/distribution or VM with restricted filesystem and repo-scoped credentials. Never use unrestricted execution as a substitute for proper permissions. No real applicant data, live Google/xAI operations or job applications in unattended runs. Review tool/version-specific sandbox and approval semantics from installed Codex help and current official docs; local execution is not local inference and usage limits apply. Obtain explicit authorization for any privileged setup and authentication; do not ask for passwords or tokens in chat.

The initial prototype used commands such as bash install.sh, devloop add jobstein examples/jobstein.json, devloop doctor jobstein, devloop run-once jobstein and systemctl --user enable --now devloop@jobstein.service. These are historical prototype commands, not guarantees for future versions. Inspect actual package and help before executing. Keep auto_push false until reviewed and the competing writer is paused.

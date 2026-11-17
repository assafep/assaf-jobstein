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

## Live development testing (explicit opt-in)

See [live development instructions](docs/live-development.md) for Google Cloud CLI installation, dedicated test project authorization and the guarded live validation script. The regular sandbox and CI remain credential-free.

## Windows (WSL 2)

Use Ubuntu under WSL 2 for development. Native Windows can compile the project, but local state requires POSIX permissions; the test suite and sandbox cannot run fully on NTFS.

From PowerShell, install Ubuntu if needed, then open it and finish creating your Linux user:

```powershell
wsl --install -d Ubuntu
wsl -d Ubuntu
```

In Ubuntu, install JDK 21 and Git, and clone into the Linux home directory (not `/mnt/c`):

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk git
export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-$(dpkg --print-architecture)"
export PATH="$JAVA_HOME/bin:$PATH"
"$JAVA_HOME/bin/java" -version
mkdir -p ~/projects
cd ~/projects
git clone https://github.com/assafep/assaf-jobstein.git
cd assaf-jobstein
./gradlew test installDist
bash scripts/smoke-test.sh
./build/install/jobstein/bin/jobstein onboard --env sandbox --non-interactive
./build/install/jobstein/bin/jobstein doctor --env sandbox
```

Add the two `export` lines above to `~/.bashrc` to retain the Java configuration in new Ubuntu Bash terminals. `JAVA_HOME` must point to the Linux JDK directory, not its `bin` directory or a Windows JDK path. The version check should report Java 21 before running Gradle.

The smoke test deliberately triggers errors and `FAIL` diagnostics to check failure recovery. A successful run ends with `PASS: all sandbox smoke checks completed` and exit code 0.

The sandbox uses synthetic data and needs no credentials. Run subsequent development commands in Ubuntu. In IntelliJ IDEA, open the Linux checkout through WSL and select its JDK 21 for both the project SDK and Gradle JVM.

## Development status

Administrative CLI and live Google/xAI adapters are implemented but live end-to-end authorization and provisioning remain unverified. Operational job search is deferred.

## Build

Requires JDK 21. The Gradle wrapper downloads and runs the pinned Gradle version, so a separate Gradle installation is not needed. Run commands from the repository root; the first wrapper run needs network access to download Gradle and dependencies.

```sh
./gradlew test installDist
bash scripts/smoke-test.sh
```

See [architecture](docs/architecture.md). Never commit credentials, resumes or personal application data.

## Continue development with Codex

Start Codex from the root of a dedicated clone and say `keep going`. [AGENTS.md](AGENTS.md) defines the CI-gated development workflow and [the handover](docs/handover/README.md) contains requirements, implementation status, prioritized backlog and the local DevLoop migration plan. No prior ChatGPT transcript is required. Coordinate with any active remote development automation before enabling unattended local publication.

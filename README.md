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

`doctor` includes read-only validation of local configuration permissions and the onboarding checkpoint journal before it checks provider readiness. If either check fails, preserve the environment directory and follow the fixed repair guidance; doctor never rewrites legacy files, checkpoints or remote Workspace state.

## Live development testing (explicit opt-in)

See [live development instructions](docs/live-development.md) for Google Cloud CLI installation, dedicated test project authorization and the guarded live validation script. The regular sandbox and CI remain credential-free.
The [live validation checklist](docs/live-validation-checklist.md) defines the manual preconditions, cleanup steps, and redacted evidence format.

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

Add the two `export` lines above to `~/.bashrc` to retain the Java configuration in new Ubuntu Bash terminals. `JAVA_HOME` must point to the Linux JDK directory, not its `bin` directory or a Windows JDK path. This also makes Java 21 the default for other Java commands in that shell.

When launching commands from PowerShell, select Ubuntu explicitly: `wsl -d Ubuntu -- bash -lc 'java -version'`. A bare `bash` or `wsl` may select Docker Desktop instead, which does not provide the development shell. Run builds in the Linux checkout and use the GitHub CLI installed and authenticated there; Windows and Ubuntu have separate PATHs. For example, `wsl -d Ubuntu -- bash -lc 'cd ~/projects/assaf-jobstein && ./gradlew --no-daemon test installDist && bash scripts/smoke-test.sh'`. Check the Linux checkout for existing changes before using it.

The smoke test deliberately triggers errors and `FAIL` diagnostics to check failure recovery. A successful run ends with `PASS: all sandbox smoke checks completed` and exit code 0.

The sandbox uses synthetic data and needs no credentials. Run subsequent development commands in Ubuntu. In IntelliJ IDEA, open the Linux checkout through WSL and select its JDK 21 for the project SDK. IntelliJ's own runtime, the shell's `JAVA_HOME`, and Gradle's daemon JVM are separate settings.

## Development status

Administrative CLI and live Google/xAI adapters are implemented but live end-to-end authorization and provisioning remain unverified. Operational job search is deferred.

## Build

Requires Java 21. The Gradle wrapper downloads and runs the pinned Gradle version, so a separate Gradle installation is not needed. The project declares Java 21 as its compile/test toolchain in `build.gradle.kts`. It also commits `gradle/gradle-daemon-jvm.properties`, which asks Gradle to run the build daemon on Java 21. This project-level criterion takes precedence over `JAVA_HOME` for the daemon, so a shell that defaults to another Java version can still select the build's required JVM.

The daemon criteria file includes Foojay download URLs for supported operating systems and architectures. Gradle first detects an installed matching JDK; if none is available, it can provision Java 21 into that user's Gradle User Home (normally `~/.gradle/jdks`). This is a per-user Gradle cache, not a system-wide JDK installation. Provisioning requires network access to the configured Foojay URLs. Developers and remote build agents can instead preinstall Java 21 and use the same committed criteria. CI already installs Java 21 before running the wrapper; production or locked-down build agents should likewise provide Java 21 or allow the Gradle user-home download. The wrapper distribution and build dependencies also require network access on first use.

`JAVA_HOME` remains useful for shell commands and tools outside Gradle. The daemon criteria only selects Gradle's build JVM; it does not change the Java version reported by `java -version` in the parent shell or IntelliJ's own runtime. To regenerate the checked-in criteria when changing the required JDK, run `./gradlew updateDaemonJvm --jvm-version=21` from a working Java 21 environment, review the generated platform URLs, and commit the result.

```sh
./gradlew test installDist
bash scripts/smoke-test.sh
```

To package and verify the portable distributions, run `bash scripts/verify-distribution.sh`. It builds the ZIP and TAR archives, writes SHA-256 sidecar files, verifies both checksums, extracts each archive into a temporary directory, and runs the CLI help command. Archive ordering and timestamps are normalized by Gradle for reproducible output. The script uses only synthetic data and does not touch Jobstein state. Remove an unpacked installation by deleting that unpacked distribution directory; user configuration and credentials remain outside the distribution under the environment state directory.

See [architecture](docs/architecture.md). Never commit credentials, resumes or personal application data.

## Continue development with Codex

Start Codex from the root of a dedicated clone and say `keep going`. [AGENTS.md](AGENTS.md) defines the CI-gated development workflow and [the handover](docs/handover/README.md) contains requirements, implementation status, prioritized backlog and the local DevLoop migration plan. No prior ChatGPT transcript is required. Coordinate with any active remote development automation before enabling unattended local publication.

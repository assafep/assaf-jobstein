# Developing Jobstein

## Build and test

Use Java 21 and a POSIX filesystem for local environment state. From the checkout root run the same build and sandbox exercise used by CI.

```sh
./gradlew --no-daemon test installDist
export PATH="$PWD/build/install/jobstein/bin:$PATH"
jobstein --version
bash scripts/smoke-test.sh
```

The smoke test uses a temporary home directory and deliberately triggers failures to test recovery. A passing run exits with code 0 and ends with `PASS: all sandbox smoke checks completed`. CI also verifies distributions and live-script opt-in guards. Authenticated provider testing has a separate [live validation checklist](live-validation-checklist.md).

The wrapper selects the pinned Gradle version. The Java toolchain and daemon criteria select Java 21 for builds. The first build needs network access for dependencies and may provision a JDK in the user's Gradle cache. `installDist`, `distZip`, and `distTar` use that toolchain's `jlink` to include a compressed Java 21 runtime under `runtime/`. The generated launchers prefer this bundled runtime, so an installed distribution does not require Java 21 on the host. `JAVA_HOME` and `PATH` remain fallbacks for development and older/custom distributions.

## Windows with WSL 2

Install Ubuntu from PowerShell with `wsl --install -d Ubuntu` and open it with `wsl -d Ubuntu`. Clone and build in the Linux home directory so that private state has POSIX permissions.

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk git
export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-$(dpkg --print-architecture)"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mkdir -p ~/projects
cd ~/projects
git clone https://github.com/assafep/assaf-jobstein.git
cd assaf-jobstein
```

Run the build and test commands above in Ubuntu. Put the two export lines in `~/.bashrc` for new Bash sessions. In IntelliJ IDEA, open the Linux checkout through WSL and select the Linux JDK 21 as the project SDK.

## Packages

Run `bash scripts/verify-distribution.sh` to build the ZIP and TAR files under `build/distributions/`, verify their SHA-256 sidecars, and run both extracted launchers with their bundled Java runtime. To install locally, extract an archive and use its `bin/jobstein` launcher; Java 21 does not need to be installed separately. Remove the extracted directory to uninstall the launcher. User state lives separately under `~/.config/jobstein/environments/`. See the [release policy](release-policy.md) before publishing.

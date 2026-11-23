#!/usr/bin/env bash
# Purpose: build, checksum and execute both packaged distribution formats.
# Used by: developers and CI before publishing a release archive.
# Lifecycle: packaging verification, after tests and before release or installation.
set -euo pipefail
cd "$(dirname "$0")/.."

./gradlew --no-daemon distZip distTar distributionChecksums
archives=(build/distributions/*.zip build/distributions/*.tar)
if ((${#archives[@]} != 2)); then
  echo 'Expected exactly one ZIP and one TAR distribution' >&2
  exit 1
fi
for archive in "${archives[@]}"; do
  (cd "$(dirname "$archive")" && sha256sum -c "$(basename "$archive").sha256")
done

verification_root=$(mktemp -d)
trap 'rm -rf "$verification_root"' EXIT
unzip -q "${archives[0]}" -d "$verification_root/zip"
mkdir -p "$verification_root/tar"
tar -xf "${archives[1]}" -C "$verification_root/tar"
for extracted in "$verification_root/zip" "$verification_root/tar"; do
  cli=$(find "$extracted" -type f -path '*/bin/jobstein' -print -quit)
  if [[ -z "$cli" ]]; then
    echo "Distribution is missing the jobstein launcher: $extracted" >&2
    exit 1
  fi
  runtime=$(dirname "$(dirname "$cli")")/runtime/bin/java
  if [[ ! -x "$runtime" ]]; then
    echo "Distribution is missing its executable Java runtime: $runtime" >&2
    exit 1
  fi
  # Local HTTP tests use the full JDK and cannot detect a missing TLS provider in the bundle.
  modules=$("$runtime" --list-modules)
  if ! grep -q '^jdk\.crypto\.ec@' <<< "$modules"; then
    echo "Distribution is missing the Java module required for Google OAuth TLS: $runtime" >&2
    exit 1
  fi
  # Prove the launcher uses the bundled runtime, even when host Java is unavailable.
  env -u JAVA_HOME PATH=/usr/bin:/bin "$cli" --version >/dev/null
done
echo 'PASS: reproducible distributions and checksums verified'

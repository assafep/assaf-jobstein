#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

java_version=$(java -version 2>&1 | awk -F'[".]' '/version/ {print ($2 == "1" ? $3 : $2); exit}')
if [[ -z "$java_version" || "$java_version" -lt 21 ]]; then
  echo "Java 21 or newer is required to launch the distribution (detected Java ${java_version:-unknown})" >&2
  exit 1
fi

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
  "$cli" --help >/dev/null
done
echo 'PASS: reproducible distributions and checksums verified'

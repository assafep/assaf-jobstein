#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
failed=0
for tool in java gcloud; do
  if command -v "$tool" >/dev/null 2>&1; then
    echo "PASS $tool installed"
  else
    echo "FAIL $tool missing"
    failed=1
  fi
done
check_java_21() {
  local label="$1" java_command="$2" version
  version=$("$java_command" -version 2>&1 | head -n 1)
  if [[ "$version" =~ \"21([.\"]|-) ]]; then
    echo "PASS $label Java 21"
  else
    echo "FAIL $label Java 21 required (detected: ${version:-unknown})"
    failed=1
  fi
}

bundled_java=./build/install/jobstein/runtime/bin/java
if [[ -x "$bundled_java" ]]; then
  check_java_21 'Bundled' "$bundled_java"
else
  echo "FAIL Bundled Java 21 runtime missing ($bundled_java)"
  failed=1
fi

if command -v java >/dev/null 2>&1; then
  host_version=$(java -version 2>&1 | head -n 1)
  echo "INFO Host Java: ${host_version:-unknown} (the bundled runtime is used by Jobstein)"
else
  echo 'INFO Host Java not installed (the bundled runtime is used by Jobstein)'
fi
if [[ -x ./build/install/jobstein/bin/jobstein ]]; then
  echo 'PASS Jobstein distribution built'
else
  echo 'FAIL Run ./gradlew test installDist'
  failed=1
fi
if command -v gcloud >/dev/null 2>&1; then
  if [[ -n "$(gcloud auth list --filter=status:ACTIVE --format='value(account)' 2>/dev/null)" ]]; then
    echo 'PASS gcloud has an active account (validity not verified)'
  else
    echo 'FAIL Authenticate gcloud separately'
    failed=1
  fi
fi
exit "$failed"

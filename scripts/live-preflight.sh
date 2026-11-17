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
if command -v java >/dev/null 2>&1; then
  version=$(java -version 2>&1 | head -n 1)
  if [[ "$version" =~ \"21([.\"]|-) ]]; then echo 'PASS JDK 21'; else echo 'FAIL JDK 21 required'; failed=1; fi
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

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Never default to live execution. No creation, mail sending, or AI generation.
if [[ $# != 2 || "$1" != --confirm-development || "$2" != development ]]; then
  echo 'Usage: bash scripts/live-smoke-test.sh --confirm-development development' >&2
  exit 2
fi
if [[ "${JOBSTEIN_LIVE_TESTS:-}" != 1 ]]; then
  echo 'Set JOBSTEIN_LIVE_TESTS=1 to authorize external read/validation requests.' >&2
  exit 2
fi
bash scripts/live-preflight.sh
cli=./build/install/jobstein/bin/jobstein
"$cli" environment validate --env development
"$cli" google validate --env development
"$cli" ai validate --env development
"$cli" user validate --env development
"$cli" state validate --env development
"$cli" doctor --env development
echo 'PASS Live development validation completed'

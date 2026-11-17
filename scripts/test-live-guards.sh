#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
expect_rejection(){
  local rc=0
  "$@" >/dev/null 2>&1 || rc=$?
  [[ "$rc" == 2 ]] || { echo "Expected guard rejection (exit 2), got $rc" >&2; exit 1; }
}
expect_rejection env JOBSTEIN_LIVE_TESTS=1 bash scripts/live-smoke-test.sh
expect_rejection env JOBSTEIN_LIVE_TESTS=1 bash scripts/live-smoke-test.sh --confirm-development production
expect_rejection env JOBSTEIN_LIVE_TESTS=1 bash scripts/live-smoke-test.sh --confirm-development sandbox
expect_rejection env JOBSTEIN_LIVE_TESTS=0 bash scripts/live-smoke-test.sh --confirm-development development
echo 'PASS Live smoke test opt-in and environment guards'

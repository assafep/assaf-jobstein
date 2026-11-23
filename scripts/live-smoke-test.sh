#!/usr/bin/env bash
# Purpose: run explicitly authorized read-only checks against a development environment.
# Used by: developers following the live validation checklist; never CI by default.
# Lifecycle: live validation, after onboarding and preflight, before release evidence.
set -euo pipefail
cd "$(dirname "$0")/.."
# Never default to live execution. No creation, mail sending, or AI generation.
if [[ $# != 2 || "$1" != --confirm-development ]]; then
  echo 'Usage: bash scripts/live-smoke-test.sh --confirm-development ENVIRONMENT' >&2
  exit 2
fi
# The flag is the operator's confirmation that the named environment is for development.
environment=$2
if [[ ! "$environment" =~ ^[a-z][a-z0-9-]{0,62}$ || "$environment" == production || "$environment" == sandbox ]]; then
  echo 'Choose a valid development environment name other than production or sandbox.' >&2
  exit 2
fi
if [[ "${JOBSTEIN_LIVE_TESTS:-}" != 1 ]]; then
  echo 'Set JOBSTEIN_LIVE_TESTS=1 to authorize external read/validation requests.' >&2
  exit 2
fi
run_check() {
  local label=$1 result=0
  shift
  "$@" || result=$?
  if ((result != 0)); then
    printf 'FAIL %s (exit %d)\n' "$label" "$result" >&2
    return "$result"
  fi
  printf 'PASS %s\n' "$label"
}

run_check preflight bash scripts/live-preflight.sh
cli=./build/install/jobstein/bin/jobstein
run_check environment_validate "$cli" environment validate --env "$environment"
run_check google_validate "$cli" google validate --env "$environment"
run_check ai_validate "$cli" ai validate --env "$environment"
run_check user_validate "$cli" user validate --env "$environment"
run_check state_validate "$cli" state validate --env "$environment"
run_check doctor "$cli" doctor --env "$environment"
echo 'PASS Live development validation completed'

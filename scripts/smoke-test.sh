#!/usr/bin/env bash
# Purpose: exercise sandbox recovery, profiles, integrations and state commands end to end.
# Used by: developers and CI as a credential-free regression check.
# Lifecycle: verification, after installDist and before distribution or live validation.
set -euo pipefail
cd "$(dirname "$0")/.."
home=$(mktemp -d)
trap 'rm -rf "$home"' EXIT
# The smoke test exercises PDF creation and parsing repeatedly. Commons Logging
# otherwise emits PDFBox's environment/font discovery diagnostics to stderr;
# command failures and the explicit sandbox diagnostics remain visible.
export JAVA_OPTS="${JAVA_OPTS:-} -Duser.home=$home -Dorg.apache.commons.logging.Log=org.apache.commons.logging.impl.NoOpLog"
cli=./build/install/jobstein/bin/jobstein
"$cli" --help
echo 'Checking intentional onboarding failure (expected error follows)'
if "$cli" onboard --env sandbox --non-interactive --simulate-failure ai-configure > "$home/failure.log"; then
  echo 'Expected simulated AI configuration failure' >&2
  exit 1
fi
grep 'SIMULATED FAILURE: ai-configure' "$home/failure.log"
echo 'Checking incomplete sandbox diagnostics (expected FAIL lines follow)'
if "$cli" doctor --env sandbox; then
  echo 'Expected incomplete sandbox diagnostics to fail' >&2
  exit 1
fi
"$cli" onboard --env sandbox --non-interactive > "$home/recovery.log"
grep 'Previously completed: google-configure' "$home/recovery.log"
grep 'Previously completed: google-authorize' "$home/recovery.log"
grep 'Completed: ai-configure' "$home/recovery.log"
"$cli" onboard --env sandbox --non-interactive
"$cli" onboard --env sandbox --non-interactive
"$cli" doctor --env sandbox
"$cli" profile list --env sandbox | grep 'software.engineer ACTIVE'
"$cli" ai integration list --env sandbox | grep standard
"$cli" profile create --env sandbox --role 'Product Manager' --non-interactive
"$cli" profile list --env sandbox | grep 'product.manager ACTIVE'
"$cli" profile descriptor show --env sandbox --profile product.manager
"$cli" profile deactivate --env sandbox --profile product.manager
"$cli" profile list --env sandbox | grep 'product.manager INACTIVE'
"$cli" ai integration create --env sandbox --integration advanced --field model=mock-grok-advanced
"$cli" ai agent configure --env sandbox --agent applicant-profile-generator --integration advanced
"$cli" ai agent show --env sandbox --agent applicant-profile-generator | grep advanced
echo 'Checking referenced integration removal is rejected (expected error follows)'
if "$cli" ai integration remove --env sandbox --integration advanced; then
  echo 'Expected referenced integration removal to fail' >&2
  exit 1
fi
"$cli" ai agent reset --env sandbox --agent applicant-profile-generator
"$cli" ai agent show --env sandbox --agent applicant-profile-generator | grep standard
"$cli" ai integration remove --env sandbox --integration advanced
"$cli" profile resume update --env sandbox --profile product.manager
"$cli" profile list --env sandbox | grep 'product.manager RESUME_UPLOADED'
echo 'Checking invalidated descriptor is rejected (expected error follows)'
if "$cli" profile descriptor validate --env sandbox --profile product.manager; then
  echo 'Expected stale descriptor validation to fail' >&2
  exit 1
fi
"$cli" profile descriptor regenerate --env sandbox --profile product.manager
"$cli" profile list --env sandbox | grep 'product.manager AWAITING_REVIEW'
"$cli" profile activate --env sandbox --profile product.manager --approve-descriptor
"$cli" profile list --env sandbox | grep 'product.manager ACTIVE'
"$cli" profile resume history --env sandbox --profile product.manager | grep 'synthetic resume 2'

"$cli" state recovery inspect --env sandbox | grep CLEAN
"$cli" state recovery resume --env sandbox | grep "Dry run"
"$cli" state recovery resume --env sandbox --apply | grep "No pending update"
echo 'PASS: all sandbox smoke checks completed'

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
home=$(mktemp -d)
trap 'rm -rf "$home"' EXIT
export JAVA_OPTS="${JAVA_OPTS:-} -Duser.home=$home"
cli=./build/install/jobstein/bin/jobstein
"$cli" --help
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

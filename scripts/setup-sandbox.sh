#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
gradle --no-daemon test installDist
echo 'Run: ./build/install/jobstein/bin/jobstein onboard --env sandbox'
echo 'Run: ./build/install/jobstein/bin/jobstein doctor --env sandbox'

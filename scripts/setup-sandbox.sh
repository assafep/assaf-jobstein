#!/usr/bin/env bash
# Purpose: build the CLI and print the commands for starting the offline sandbox.
# Used by: developers and new contributors trying Jobstein without external services.
# Lifecycle: development setup, before sandbox onboarding and local smoke testing.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew --no-daemon test installDist
echo 'Run: ./build/install/jobstein/bin/jobstein onboard --env sandbox'
echo 'Run: ./build/install/jobstein/bin/jobstein doctor --env sandbox'

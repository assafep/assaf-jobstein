#!/usr/bin/env bash
# Purpose: guide an operator through the browser-only Google OAuth setup links.
# Used by: developers configuring a disposable live-validation project.
# Lifecycle: manual setup, between gcloud preflight and the jobstein onboard command.
set -euo pipefail

if [[ $# != 1 || -z "$1" ]]; then
  echo "Usage: bash scripts/live-console-guide.sh PROJECT_ID" >&2
  exit 2
fi

project_id=$1
branding_url="https://console.cloud.google.com/auth/branding?project=${project_id}"
clients_url="https://console.cloud.google.com/auth/clients?project=${project_id}"

open_link() {
  local url=$1
  if command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$url" >/dev/null 2>&1 &
  elif command -v open >/dev/null 2>&1; then
    open "$url" >/dev/null 2>&1 &
  else
    echo 'No desktop browser opener found; copy the link into a browser.'
  fi
}

echo 'Step 1: configure Google Auth Platform branding, audience, test users and scopes.'
echo "$branding_url"
read -r -p 'Press Enter after completing this page (or Ctrl-C to stop): ' _

echo
echo 'Step 2: create a Desktop OAuth client and download its JSON file.'
echo "$clients_url"
read -r -p 'Press Enter after downloading the JSON file (or Ctrl-C to stop): ' _

echo
echo 'Keep the downloaded OAuth client JSON outside the checkout and never commit it.'

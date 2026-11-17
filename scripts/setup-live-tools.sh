#!/usr/bin/env bash
set -euo pipefail
# Explicit opt-in. No authentication or project creation in this script.
if command -v gcloud >/dev/null 2>&1; then
  gcloud version
  exit 0
fi
if [[ "$(uname -s)" != Linux ]] || ! command -v apt-get >/dev/null; then
  echo 'Use the official Google Cloud CLI installation instructions for your OS.' >&2
  exit 1
fi
if [[ "$(id -u)" == 0 ]]; then
  elevate=()
else
  elevate=(sudo)
fi
"${elevate[@]}" apt-get update
"${elevate[@]}" apt-get install -y ca-certificates curl gnupg
temp=$(mktemp -d)
trap 'rm -rf "$temp"' EXIT
curl --fail --silent --show-error --location --proto '=https' https://packages.cloud.google.com/apt/doc/apt-key.gpg -o "$temp/key"
gpg --batch --dearmor --output "$temp/keyring.gpg" "$temp/key"
"${elevate[@]}" install -m 0644 "$temp/keyring.gpg" /usr/share/keyrings/jobstein-google-cloud.gpg
printf '%s\n' 'deb [signed-by=/usr/share/keyrings/jobstein-google-cloud.gpg] https://packages.cloud.google.com/apt cloud-sdk main' | "${elevate[@]}" tee /etc/apt/sources.list.d/jobstein-google-cloud.list >/dev/null
"${elevate[@]}" apt-get update
"${elevate[@]}" apt-get install -y google-cloud-cli
gcloud version

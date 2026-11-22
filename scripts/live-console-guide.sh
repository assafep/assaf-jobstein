#!/usr/bin/env bash
# Purpose: prepare a project safely, then guide an operator through browser-only Google OAuth setup.
# Used by: developers configuring a disposable live-validation project.
# Lifecycle: manual setup, between gcloud preflight and the jobstein onboard command.
set -euo pipefail

authuser=''
project_arg=''
while (($#)); do
  case "$1" in
    --authuser)
      if (( $# < 2 )) || [[ -z "$2" ]]; then
        echo 'Usage: bash scripts/live-console-guide.sh [--authuser ACCOUNT_OR_INDEX] [PROJECT_ID]' >&2
        exit 2
      fi
      authuser=$2
      shift 2
      ;;
    --*)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
    *)
      if [[ -n "$project_arg" ]]; then
        echo 'Only one PROJECT_ID may be provided.' >&2
        exit 2
      fi
      project_arg=$1
      shift
      ;;
  esac
done

if [[ -n "$project_arg" ]]; then
  project_id=$project_arg
else
  if ! command -v gcloud >/dev/null 2>&1; then
    echo 'gcloud is required when PROJECT_ID is omitted.' >&2
    exit 1
  fi
  mapfile -t projects < <(gcloud projects list --format='value(projectId)')
  if ((${#projects[@]} == 0)); then
    echo 'No accessible Google Cloud projects found.' >&2
    exit 1
  fi
  echo 'Choose the Google Cloud project for live onboarding:'
  for index in "${!projects[@]}"; do
    printf '  %d) %s\n' "$((index + 1))" "${projects[$index]}"
  done
  read -r -p 'Project number: ' selection
  if [[ ! "$selection" =~ ^[0-9]+$ ]] || ((selection < 1 || selection > ${#projects[@]})); then
    echo 'Invalid project selection.' >&2
    exit 2
  fi
  project_id=${projects[$((selection - 1))]}
fi

required_apis=(drive.googleapis.com docs.googleapis.com gmail.googleapis.com calendar-json.googleapis.com)
echo 'Checking Google Cloud project access...'
gcloud projects describe "$project_id" --format='value(projectId)' >/dev/null
mapfile -t enabled_apis < <(gcloud services list --enabled --project="$project_id" --format='value(config.name)')
missing_apis=()
for api in "${required_apis[@]}"; do
  api_enabled=false
  for enabled_api in "${enabled_apis[@]}"; do
    if [[ "$enabled_api" == "$api" ]]; then
      api_enabled=true
      break
    fi
  done
  if [[ "$api_enabled" == false ]]; then
    missing_apis+=("$api")
  fi
done
if ((${#missing_apis[@]})); then
  echo "Required APIs not enabled: ${missing_apis[*]}"
  read -r -p 'Enable these APIs now? [Y/n]: ' enable_answer
  if [[ -z "$enable_answer" || "$enable_answer" =~ ^[Yy]$ ]]; then
    gcloud services enable "${missing_apis[@]}" --project="$project_id" --quiet
    echo 'Required APIs enabled.'
  else
    echo 'API enablement skipped; onboarding may fail until they are enabled.' >&2
  fi
else
  echo 'Required APIs already enabled.'
fi

if [[ -z "$authuser" ]]; then
  if ! command -v gcloud >/dev/null 2>&1; then
    echo 'gcloud is required to determine the active account when --authuser is omitted.' >&2
    exit 1
  fi
  authuser=$(gcloud auth list --filter=status:ACTIVE --format='value(account)' | head -n 1)
fi
if [[ -z "$authuser" ]]; then
  echo 'No active gcloud account found; pass --authuser ACCOUNT_OR_INDEX explicitly.' >&2
  exit 1
fi

# Console accepts an account email or browser account index as authuser.
encoded_authuser=${authuser//@/%40}
encoded_authuser=${encoded_authuser//+/%2B}
echo "Using Google Cloud project: $project_id"
echo "Using Google account: $authuser"
branding_url="https://console.cloud.google.com/auth/branding?project=${project_id}&authuser=${encoded_authuser}"
clients_url="https://console.cloud.google.com/auth/clients?project=${project_id}&authuser=${encoded_authuser}"

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

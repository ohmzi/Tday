#!/usr/bin/env bash
# Rebuild and restart the T'Day backend container on the deploy host (includes web SPA).
#
# Usage:
#   ./scripts/redeploy-remote-backend.sh                 # interactive (prompts for SSH password)
#   ./scripts/redeploy-remote-backend.sh --password ';'  # passwordless via sshpass
#   SSH_PASSWORD=';' ./scripts/redeploy-remote-backend.sh # passwordless via env var
#
# Passwordless mode requires `sshpass` (brew install hudochenkov/sshpass/sshpass).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REMOTE_HOST="${REMOTE_HOST:-ohmz@192.168.1.106}"
REMOTE_DIR="${REMOTE_DIR:-/home/ohmz/StudioProjects/Tday}"

# Password can come from --password <pw>, -p <pw>, or the SSH_PASSWORD env var.
SSH_PASSWORD="${SSH_PASSWORD:-}"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --password|-p)
      SSH_PASSWORD="${2:-}"
      shift 2
      ;;
    --password=*)
      SSH_PASSWORD="${1#*=}"
      shift
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

cd "$ROOT"

# Build ssh/rsync wrappers. If a password is supplied, route through sshpass so the
# deploy runs without interactive prompts; otherwise fall back to plain ssh/rsync.
if [[ -n "$SSH_PASSWORD" ]]; then
  if ! command -v sshpass >/dev/null 2>&1; then
    echo "Error: a password was provided but 'sshpass' is not installed." >&2
    echo "Install it (macOS: brew install hudochenkov/sshpass/sshpass) or run without --password." >&2
    exit 1
  fi
  export SSHPASS="$SSH_PASSWORD"
  SSH=(sshpass -e ssh)
  RSYNC=(rsync -az -e "sshpass -e ssh")
  echo "Using sshpass for non-interactive auth."
else
  SSH=(ssh)
  RSYNC=(rsync -az)
fi

echo "Syncing web sources to ${REMOTE_HOST}:${REMOTE_DIR} ..."
"${RSYNC[@]}" \
  tday-web/src/ \
  "${REMOTE_HOST}:${REMOTE_DIR}/tday-web/src/"
"${RSYNC[@]}" \
  tday-web/index.html \
  "${REMOTE_HOST}:${REMOTE_DIR}/tday-web/"

echo "Rebuilding backend on remote host ..."
"${SSH[@]}" "${REMOTE_HOST}" "cd '${REMOTE_DIR}' && docker compose up -d --build tday-backend && docker compose ps tday-backend"

echo "Health check ..."
"${SSH[@]}" "${REMOTE_HOST}" "curl -sf http://127.0.0.1:2525/health || true"

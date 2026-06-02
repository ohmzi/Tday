#!/usr/bin/env bash
# Rebuild and restart the T'Day backend container on the deploy host (includes web SPA).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REMOTE_HOST="${REMOTE_HOST:-ohmz@192.168.1.106}"
REMOTE_DIR="${REMOTE_DIR:-/home/ohmz/StudioProjects/Tday}"

cd "$ROOT"

echo "Syncing web sources to ${REMOTE_HOST}:${REMOTE_DIR} ..."
rsync -az \
  tday-web/src/ \
  "${REMOTE_HOST}:${REMOTE_DIR}/tday-web/src/"
rsync -az \
  tday-web/index.html \
  "${REMOTE_HOST}:${REMOTE_DIR}/tday-web/"

echo "Rebuilding backend on remote host ..."
ssh "${REMOTE_HOST}" "cd '${REMOTE_DIR}' && docker compose up -d --build tday-backend && docker compose ps tday-backend"

echo "Health check ..."
ssh "${REMOTE_HOST}" "curl -sf http://127.0.0.1:2525/health || true"

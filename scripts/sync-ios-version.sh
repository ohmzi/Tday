#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
node "$REPO_ROOT/scripts/version.mjs" sync

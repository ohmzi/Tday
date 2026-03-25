#!/usr/bin/env bash
# Installs project git hooks. Run once after cloning:
#   bash scripts/install-hooks.sh

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
HOOKS_DIR="$REPO_ROOT/.git/hooks"

echo "Installing git hooks..."

cp "$REPO_ROOT/scripts/commit-msg" "$HOOKS_DIR/commit-msg"
chmod +x "$HOOKS_DIR/commit-msg"

echo "Done. commit-msg hook installed at $HOOKS_DIR/commit-msg"

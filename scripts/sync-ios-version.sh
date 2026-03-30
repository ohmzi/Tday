#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGE_JSON="$REPO_ROOT/tday-web/package.json"
INFO_PLIST="$REPO_ROOT/ios-swiftUI/Tday/Info.plist"

if [ ! -f "$PACKAGE_JSON" ]; then
  echo "ERROR: $PACKAGE_JSON not found" >&2
  exit 1
fi

if [ ! -f "$INFO_PLIST" ]; then
  echo "ERROR: $INFO_PLIST not found" >&2
  exit 1
fi

VERSION=$(grep -o '"version"[[:space:]]*:[[:space:]]*"[^"]*"' "$PACKAGE_JSON" | head -1 | sed 's/.*"\([^"]*\)"$/\1/')

if [ -z "$VERSION" ]; then
  echo "ERROR: could not read version from $PACKAGE_JSON" >&2
  exit 1
fi

plutil -replace CFBundleShortVersionString -string "$VERSION" "$INFO_PLIST"
echo "iOS Info.plist version synced to $VERSION"

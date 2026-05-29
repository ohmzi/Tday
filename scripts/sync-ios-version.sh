#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGE_JSON="$REPO_ROOT/tday-web/package.json"
INFO_PLIST="$REPO_ROOT/ios-swiftUI/Tday/Info.plist"
PROJECT_YML="$REPO_ROOT/ios-swiftUI/project.yml"
PBXPROJ="$REPO_ROOT/ios-swiftUI/TdayApp.xcodeproj/project.pbxproj"
ROOT_ENV_EXAMPLE="$REPO_ROOT/.env.example"
BACKEND_ENV_EXAMPLE="$REPO_ROOT/tday-backend/.env.example"

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

perl -0pi -e \
  's#(<key>CFBundleShortVersionString</key>\s*<string>)[^<]*(</string>)#${1}'"$VERSION"'${2}#' \
  "$INFO_PLIST"

for file in "$PROJECT_YML" "$PBXPROJ"; do
  if [ -f "$file" ]; then
    perl -0pi -e 's/(MARKETING_VERSION\s*[=:]\s*)[0-9]+\.[0-9]+\.[0-9]+/${1}'"$VERSION"'/g' "$file"
  fi
done

for file in "$ROOT_ENV_EXAMPLE" "$BACKEND_ENV_EXAMPLE"; do
  if [ -f "$file" ]; then
    perl -0pi -e 's/^(TDAY_APP_VERSION=)[0-9]+\.[0-9]+\.[0-9]+/${1}'"$VERSION"'/m' "$file"
  fi
done

echo "Version mirrors synced to $VERSION"

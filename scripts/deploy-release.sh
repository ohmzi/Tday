#!/usr/bin/env bash
# Deploy the released T'Day backend image to this host.
#
# Production must run the image CI published for a release, not a working-tree build.
# A working-tree build ships whatever happens to be checked out (including uncommitted
# code) and re-hashes every SPA chunk, which strands already-open PWA/Safari clients on
# a precache whose chunks no longer exist. This script pulls
# `ghcr.io/ohmzi/tday:v<version>` for the version in root version.json, recreates the
# backend container from it, and verifies the running server actually reports it.
#
# Usage:
#   ./scripts/deploy-release.sh                       # deploy the version in version.json
#   ./scripts/deploy-release.sh --version 0.7.2       # deploy a specific release
#   ./scripts/deploy-release.sh --image ghcr.io/ohmzi/tday:latest
#   ./scripts/deploy-release.sh --url https://tday.ohmz.cloud   # also verify through ingress
#   ./scripts/deploy-release.sh --dry-run             # resolve and check, change nothing
#
# Flyway has no down-migrations and nothing else backs the database up before a deploy,
# so this script compares the migrations baked into the running image with the ones in
# the target image and refuses to continue when the new image adds any, unless you pass
# --backup (take one now) or --skip-backup (you already have one).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

log()  { printf '[deploy %s] %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*"; }
warn() { printf '[deploy %s] WARNING: %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*" >&2; }
die()  { printf '[deploy %s] ERROR: %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*" >&2; exit 1; }

usage() {
  awk 'NR == 1 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "${BASH_SOURCE[0]}"
  cat <<'EOF'

Options:
  --version X.Y.Z     Release to deploy. Default: "version" from root version.json.
  --image REF         Full image reference; overrides --version and disables the fallback.
  --registry REF      Image repository. Default: ghcr.io/ohmzi/tday (TDAY_IMAGE_REPO).
  --url URL           Public base URL to verify through the ingress as well
                      (TDAY_PUBLIC_URL). Skipped when unset.
  --service NAME      Compose service. Default: tday-backend.
  --backup            Run scripts/backup-database.sh before recreating the container.
  --skip-backup       Proceed even if the target image adds Flyway migrations.
  --no-fallback       Fail instead of falling back to :latest when :v<version> is absent.
  --timeout SECONDS   How long to wait for the container to become healthy. Default: 180.
  --dry-run           Resolve the image and run the checks, but do not deploy.
  -h, --help          This text.

Exit code is non-zero if the deploy fails or if the deployed server does not report the
expected version.
EOF
}

IMAGE_REPO="${TDAY_IMAGE_REPO:-ghcr.io/ohmzi/tday}"
SERVICE="${TDAY_BACKEND_SERVICE:-tday-backend}"
PUBLIC_URL="${TDAY_PUBLIC_URL:-}"
VERSION=""
IMAGE=""
DO_BACKUP=false
SKIP_BACKUP=false
ALLOW_FALLBACK=true
DRY_RUN=false
HEALTH_TIMEOUT=180

while [ $# -gt 0 ]; do
  case "$1" in
    --version)     VERSION="${2:?--version needs a semver}"; shift 2 ;;
    --version=*)   VERSION="${1#*=}"; shift ;;
    --image)       IMAGE="${2:?--image needs a reference}"; shift 2 ;;
    --image=*)     IMAGE="${1#*=}"; shift ;;
    --registry)    IMAGE_REPO="${2:?--registry needs a repository}"; shift 2 ;;
    --registry=*)  IMAGE_REPO="${1#*=}"; shift ;;
    --url)         PUBLIC_URL="${2:?--url needs a URL}"; shift 2 ;;
    --url=*)       PUBLIC_URL="${1#*=}"; shift ;;
    --service)     SERVICE="${2:?--service needs a name}"; shift 2 ;;
    --service=*)   SERVICE="${1#*=}"; shift ;;
    --backup)      DO_BACKUP=true; shift ;;
    --skip-backup) SKIP_BACKUP=true; shift ;;
    --no-fallback) ALLOW_FALLBACK=false; shift ;;
    --timeout)     HEALTH_TIMEOUT="${2:?--timeout needs seconds}"; shift 2 ;;
    --timeout=*)   HEALTH_TIMEOUT="${1#*=}"; shift ;;
    --dry-run)     DRY_RUN=true; shift ;;
    -h|--help)     usage; exit 0 ;;
    *)             die "unknown argument: $1 (try --help)" ;;
  esac
done

case "$HEALTH_TIMEOUT" in ''|*[!0-9]*) die "--timeout must be a whole number of seconds" ;; esac

# ---------------------------------------------------------------------------
# Preflight
# ---------------------------------------------------------------------------

command -v docker >/dev/null 2>&1 || die "docker is not installed or not on PATH"
command -v curl   >/dev/null 2>&1 || die "curl is not installed or not on PATH"
docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required ('docker compose')"

COMPOSE_FILE="$ROOT/docker-compose.yaml"
[ -f "$COMPOSE_FILE" ] || die "compose file not found: $COMPOSE_FILE"
COMPOSE=(docker compose -f "$COMPOSE_FILE")

# version.json is the single source of truth; never hand-maintain a version here.
read_manifest_version() {
  [ -f "$ROOT/version.json" ] || die "version.json not found at $ROOT"
  if command -v node >/dev/null 2>&1; then
    node -e 'process.stdout.write(String(require("'"$ROOT"'/version.json").version || ""))'
  else
    sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$ROOT/version.json" | head -n 1
  fi
}

EXPECTED_VERSION=""
if [ -n "$IMAGE" ]; then
  log "using explicit image $IMAGE (no version check unless it is a :v<semver> tag)"
  case "$IMAGE" in
    *:v[0-9]*) EXPECTED_VERSION="${IMAGE##*:v}" ;;
  esac
else
  [ -n "$VERSION" ] || VERSION="$(read_manifest_version)"
  [ -n "$VERSION" ] || die "could not read \"version\" from $ROOT/version.json"
  IMAGE="${IMAGE_REPO}:v${VERSION}"
  EXPECTED_VERSION="$VERSION"
  log "release version from version.json: $VERSION"
fi

# ---------------------------------------------------------------------------
# Pull, with a sensible fallback
# ---------------------------------------------------------------------------

log "pulling $IMAGE"
if ! docker pull "$IMAGE" >/dev/null 2>&1; then
  if [ "$ALLOW_FALLBACK" = true ] && [ -n "$EXPECTED_VERSION" ]; then
    warn "$IMAGE is not published (yet?) - falling back to ${IMAGE_REPO}:latest"
    IMAGE="${IMAGE_REPO}:latest"
    EXPECTED_VERSION=""   # :latest is whatever CI last published; verify, don't assert.
    docker pull "$IMAGE" >/dev/null || die "could not pull $IMAGE either"
  else
    die "could not pull $IMAGE"
  fi
fi
log "image ready: $IMAGE ($(docker image inspect -f '{{index .RepoDigests 0}}' "$IMAGE" 2>/dev/null || echo 'no digest'))"

# ---------------------------------------------------------------------------
# Migration safety: does the new image add Flyway migrations?
# ---------------------------------------------------------------------------

CONTAINER="$("${COMPOSE[@]}" ps -q "$SERVICE" 2>/dev/null || true)"
NEW_MIGRATIONS=""
if [ -n "$CONTAINER" ]; then
  # Both images carry the migration set at /app/migrations (Dockerfile.backend), so the
  # running container is the honest record of what has already shipped to this database.
  CURRENT_LIST="$(docker exec "$CONTAINER" ls /app/migrations 2>/dev/null | sort || true)"
  TARGET_LIST="$(docker run --rm --entrypoint ls "$IMAGE" -1 /app/migrations 2>/dev/null | sort || true)"
  if [ -n "$CURRENT_LIST" ] && [ -n "$TARGET_LIST" ]; then
    NEW_MIGRATIONS="$(comm -13 <(printf '%s\n' "$CURRENT_LIST") <(printf '%s\n' "$TARGET_LIST") || true)"
  else
    warn "could not compare migration sets - treat this deploy as if it had migrations"
  fi
else
  log "no running '$SERVICE' container - skipping the migration comparison"
fi

if [ -n "$NEW_MIGRATIONS" ]; then
  warn "the target image adds Flyway migrations, which run automatically on start:"
  printf '%s\n' "$NEW_MIGRATIONS" | sed 's/^/    /' >&2
  warn "Flyway has no down-migrations: there is no rollback once these apply."
  if [ "$DO_BACKUP" = true ]; then
    log "taking a database backup first"
    [ "$DRY_RUN" = true ] || "$ROOT/scripts/backup-database.sh"
  elif [ "$SKIP_BACKUP" != true ]; then
    die "refusing to deploy without a backup - rerun with --backup (take one now) or --skip-backup (you already have one)"
  else
    warn "--skip-backup given: continuing without taking a backup"
  fi
elif [ "$DO_BACKUP" = true ]; then
  log "taking a database backup (--backup)"
  [ "$DRY_RUN" = true ] || "$ROOT/scripts/backup-database.sh"
else
  log "no new Flyway migrations in the target image"
fi

if [ "$DRY_RUN" = true ]; then
  log "dry run: would deploy $IMAGE to service '$SERVICE'"
  exit 0
fi

# ---------------------------------------------------------------------------
# Deploy
# ---------------------------------------------------------------------------

log "recreating '$SERVICE' from $IMAGE"
TDAY_BACKEND_IMAGE="$IMAGE" "${COMPOSE[@]}" up -d --no-build "$SERVICE"

CONTAINER="$("${COMPOSE[@]}" ps -q "$SERVICE" 2>/dev/null || true)"
[ -n "$CONTAINER" ] || die "'$SERVICE' did not come up"

log "waiting up to ${HEALTH_TIMEOUT}s for the container healthcheck"
deadline=$(( $(date +%s) + HEALTH_TIMEOUT ))
while :; do
  state="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$CONTAINER" 2>/dev/null || echo unknown)"
  case "$state" in
    healthy|running) break ;;
    exited|dead)     die "'$SERVICE' is $state - check: docker compose logs --tail=100 $SERVICE" ;;
  esac
  [ "$(date +%s)" -lt "$deadline" ] || die "'$SERVICE' never became healthy (last state: $state)"
  sleep 3
done

# ---------------------------------------------------------------------------
# Verify what the server actually reports
# ---------------------------------------------------------------------------

# Compose maps ${TDAY_HOST_PORT:-2525} on ${TDAY_HOST_BIND:-127.0.0.1}; loopback always works.
HOST_PORT="$(docker port "$CONTAINER" 8080/tcp 2>/dev/null | head -n 1 | sed 's/.*://')"
[ -n "$HOST_PORT" ] || HOST_PORT="${TDAY_HOST_PORT:-2525}"
LOCAL_URL="http://127.0.0.1:${HOST_PORT}"

# /version.json reports the release as "version"; /api/mobile/probe reports it as
# "appVersion" and uses "version" for its own schema number - so appVersion wins.
extract_version() {
  local body value key
  body="$(tr ',{}' '\n\n\n')"
  value=""
  for key in appVersion version; do
    if [ -z "$value" ]; then
      value="$(printf '%s\n' "$body" | sed -n 's/.*"'"$key"'"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"
    fi
  done
  printf '%s' "$value"
}

verify_endpoint() {
  local base="$1" label="$2" path body reported failures=0
  for path in /version.json /api/mobile/probe; do
    # Retry briefly: the JVM can answer /health slightly before the routes settle.
    body=""
    for _ in 1 2 3 4 5; do
      body="$(curl -fsS --max-time 10 "${base}${path}" 2>/dev/null || true)"
      [ -n "$body" ] && break
      sleep 2
    done
    if [ -z "$body" ]; then
      warn "$label ${path}: no response"
      failures=$((failures + 1))
      continue
    fi
    reported="$(printf '%s' "$body" | extract_version)"
    log "$label ${path} -> ${body}"
    if [ -n "$EXPECTED_VERSION" ] && [ "$reported" != "$EXPECTED_VERSION" ]; then
      warn "$label ${path} reports '${reported}', expected '${EXPECTED_VERSION}'"
      failures=$((failures + 1))
    fi
  done
  return "$failures"
}

STATUS=0
verify_endpoint "$LOCAL_URL" "local" || STATUS=1

if [ -n "$PUBLIC_URL" ]; then
  # Trailing slashes would produce //version.json, which the SPA fallback happily
  # answers with HTML instead of JSON.
  PUBLIC_URL="${PUBLIC_URL%/}"
  verify_endpoint "$PUBLIC_URL" "public" || STATUS=1
else
  log "no public URL given (--url / TDAY_PUBLIC_URL) - skipped the ingress check"
fi

if [ "$STATUS" -ne 0 ]; then
  die "deploy finished but verification failed - see the warnings above"
fi

if [ -n "$EXPECTED_VERSION" ]; then
  log "done. $SERVICE is serving $EXPECTED_VERSION from $IMAGE"
else
  log "done. $SERVICE is serving $IMAGE"
fi

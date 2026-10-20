#!/bin/sh
# Restart the T'Day backend when it is *hung*, not when it has exited.
#
# `restart: always` in docker-compose.yaml covers the failure Docker can see: a container whose
# process exits is started again. A JVM that stops answering while its process stays alive is
# invisible to that — the healthcheck in the same file marks such a container `unhealthy`, and
# nothing acts on health. This script is the act: it reads the health the healthcheck already
# writes, and restarts the container once the evidence is unambiguous.
#
# It is built to run from cron every minute, so silence is the normal outcome and it must never
# bounce a server that is merely slow:
#
#   * one unhealthy check is not enough — --failures consecutive ones are needed (default 3);
#   * two restarts are never closer together than --cooldown (default 15 minutes);
#   * `starting` (the healthcheck's start_period) is not a failure;
#   * a container Docker is already restarting is left alone.
#
# Usage:
#   scripts/backend-watchdog.sh                 # one check; restart only if it has earned it
#   scripts/backend-watchdog.sh --status        # print the current state and exit
#   scripts/backend-watchdog.sh --dry-run       # describe the decision, change nothing
#   scripts/backend-watchdog.sh --help
#
# Install (docs/DEPLOYMENT.md has the cron and launchd forms). Decisions are one line each on
# stderr; the redirect is what keeps cron quiet and keeps the history:
#
#   * * * * * /path/to/backend-watchdog.sh >>/var/log/tday-watchdog.log 2>&1
#
# Written for POSIX sh on purpose: the host is often a NAS with busybox ash and no bash, and cron
# runs what it is given with /bin/sh.
set -eu

PROG=backend-watchdog
CONTAINER=${TDAY_WATCHDOG_CONTAINER:-tday_backend}
FAILURES=${TDAY_WATCHDOG_FAILURES:-3}
COOLDOWN_MINUTES=${TDAY_WATCHDOG_COOLDOWN:-15}
HEALTH_URL=${TDAY_WATCHDOG_HEALTH_URL:-}
LOG_FILE=${TDAY_WATCHDOG_LOG:-}
DOCKER=${TDAY_WATCHDOG_DOCKER:-docker}
STATE_DIR=${TDAY_WATCHDOG_STATE_DIR:-}
DRY_RUN=0
STATUS_ONLY=0

usage() {
  # Print the header comment block — everything after the shebang up to the first non-comment
  # line — so the usage text and the file header cannot drift apart.
  awk 'NR == 1 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "$0"
}

log() {
  line="[$PROG $(date -u '+%Y-%m-%dT%H:%M:%SZ')] $*"
  printf '%s\n' "$line" >&2
  if [ -n "$LOG_FILE" ]; then
    printf '%s\n' "$line" >> "$LOG_FILE"
  fi
}

die() {
  log "ERROR: $*"
  exit 1
}

while [ $# -gt 0 ]; do
  case "$1" in
    --container) [ $# -ge 2 ] || die "--container needs the container name"; CONTAINER=$2; shift 2 ;;
    --container=*) CONTAINER=${1#*=}; shift ;;
    --failures) [ $# -ge 2 ] || die "--failures needs a count"; FAILURES=$2; shift 2 ;;
    --failures=*) FAILURES=${1#*=}; shift ;;
    --cooldown) [ $# -ge 2 ] || die "--cooldown needs a number of minutes"; COOLDOWN_MINUTES=$2; shift 2 ;;
    --cooldown=*) COOLDOWN_MINUTES=${1#*=}; shift ;;
    --health-url) [ $# -ge 2 ] || die "--health-url needs a URL"; HEALTH_URL=$2; shift 2 ;;
    --health-url=*) HEALTH_URL=${1#*=}; shift ;;
    --log-file) [ $# -ge 2 ] || die "--log-file needs a path"; LOG_FILE=$2; shift 2 ;;
    --log-file=*) LOG_FILE=${1#*=}; shift ;;
    --state-dir) [ $# -ge 2 ] || die "--state-dir needs a path"; STATE_DIR=$2; shift 2 ;;
    --state-dir=*) STATE_DIR=${1#*=}; shift ;;
    --docker) [ $# -ge 2 ] || die "--docker needs a command"; DOCKER=$2; shift 2 ;;
    --docker=*) DOCKER=${1#*=}; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    --status) STATUS_ONLY=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument '$1' (try --help)" ;;
  esac
done

case "$FAILURES" in
  ''|*[!0-9]*) die "--failures takes a whole number, got '$FAILURES'" ;;
esac
[ "$FAILURES" -ge 1 ] || die "--failures has to be at least 1"

case "$COOLDOWN_MINUTES" in
  ''|*[!0-9]*) die "--cooldown takes a whole number of minutes, got '$COOLDOWN_MINUTES'" ;;
esac

# The failure count and the last restart have to outlive a single run: a cron entry decides once a
# minute, and "three consecutive" spans three of them. XDG_STATE_HOME is honoured because a host
# with a read-only or shared HOME usually has it set for exactly this kind of state.
if [ -z "$STATE_DIR" ]; then
  if [ -z "${HOME:-}" ] && [ -z "${XDG_STATE_HOME:-}" ]; then
    die "no HOME and no XDG_STATE_HOME to keep state in; pass --state-dir DIR"
  fi
  STATE_DIR="${XDG_STATE_HOME:-$HOME/.local/state}/tday-watchdog"
fi

state_file() { printf '%s/%s' "$STATE_DIR" "$1"; }

now() { date +%s; }

read_state_number() {
  # A torn or hand-edited file must not stop the watchdog; anything unreadable reads as zero.
  value=$(cat "$(state_file "$1")" 2>/dev/null || echo 0)
  case "$value" in
    ''|*[!0-9]*) value=0 ;;
  esac
  printf '%s' "$value"
}

write_state_number() {
  if [ ! -d "$STATE_DIR" ]; then
    mkdir -p "$STATE_DIR"
    chmod 700 "$STATE_DIR" 2>/dev/null || true
  fi
  printf '%s\n' "$2" > "$(state_file "$1")"
}

read_failures() { read_state_number consecutive-failures; }
read_last_restart() { read_state_number last-restart; }

cooldown_remaining() {
  last=$(read_last_restart)
  if [ "$last" -eq 0 ]; then
    printf '0'
    return 0
  fi
  elapsed=$(( $(now) - last ))
  # A clock that jumped backwards must not extend a cooldown for as long as it is wrong.
  if [ "$elapsed" -lt 0 ]; then
    elapsed=0
  fi
  limit=$(( COOLDOWN_MINUTES * 60 ))
  if [ "$elapsed" -ge "$limit" ]; then
    printf '0'
  else
    printf '%s' "$(( limit - elapsed ))"
  fi
}

# The documented seam: a command whose stdout is the health word. It is how the self-test drives
# this script without Docker, and how an operator whose Docker CLI lives somewhere unusual — or
# who reads health from another host — replaces the check without patching the script.
read_health() {
  if [ -n "${TDAY_WATCHDOG_HEALTH_CMD:-}" ]; then
    sh -c "$TDAY_WATCHDOG_HEALTH_CMD" || return 1
    return 0
  fi
  "$DOCKER" inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$CONTAINER" || return 1
  return 0
}

# Whether Docker is mid-restart. Reported as an inspect failure rather than a state, because a
# container that vanished between the two calls is not something to act on either.
read_restarting() {
  "$DOCKER" inspect --format '{{.State.Restarting}}' "$CONTAINER" || return 1
  return 0
}

# curl, then wget: a host with neither cannot answer the question, and saying so beats reporting a
# working backend as down. Returns 0 healthy, 1 unhealthy, 2 cannot probe.
probe_health_url() {
  if command -v curl >/dev/null 2>&1; then
    if curl -fsS -o /dev/null --max-time 5 "$HEALTH_URL" >/dev/null 2>&1; then
      return 0
    fi
    return 1
  fi
  if command -v wget >/dev/null 2>&1; then
    if wget -q --spider --timeout=5 "$HEALTH_URL" >/dev/null 2>&1; then
      return 0
    fi
    return 1
  fi
  return 2
}

# Healthy is silent — that is what keeps cron quiet — except when it ends a run of failures, which
# is the one transition an operator wants to find in the log.
record_healthy() {
  count=$(read_failures)
  if [ "$count" -gt 0 ]; then
    if [ "$DRY_RUN" -eq 1 ]; then
      log "dry-run: healthy again after $count consecutive unhealthy checks — would reset the count"
      return 0
    fi
    write_state_number consecutive-failures 0
    log "healthy again after $count consecutive unhealthy checks"
  fi
}

restart_container() {
  count=$1
  if ! command -v "$DOCKER" >/dev/null 2>&1; then
    log "cannot restart: docker command '$DOCKER' is not on PATH"
    return 1
  fi
  # The cooldown is claimed before the restart, not after it. Two runs that overlap must not both
  # decide to restart, and a `docker restart` that hangs must not be retried every minute while it
  # hangs. A restart that then fails keeps the claimed window: the operator sees the failure, and
  # the backend is not hammered while whatever broke Docker is still broken.
  write_state_number last-restart "$(now)"
  write_state_number consecutive-failures 0
  log "restarting '$CONTAINER' after $count consecutive unhealthy checks"
  if restart_output=$("$DOCKER" restart "$CONTAINER" 2>&1); then
    log "restarted '$CONTAINER'"
    return 0
  fi
  log "restart of '$CONTAINER' failed: $restart_output"
  return 1
}

handle_unhealthy() {
  restarting=$(read_restarting 2>/dev/null || printf 'false')
  case "$restarting" in
    true|True|1)
      log "'$CONTAINER' is already restarting — leaving it alone"
      return 0
      ;;
  esac

  count=$(( $(read_failures) + 1 ))

  # --dry-run owns every write in this function, in one place: it reports the decision the state on
  # disk would produce and leaves that state exactly as it found it.
  if [ "$DRY_RUN" -eq 1 ]; then
    remaining=$(cooldown_remaining)
    if [ "$count" -lt "$FAILURES" ]; then
      log "dry-run: unhealthy ($count/$FAILURES consecutive) — would wait for $((FAILURES - count)) more"
    elif [ "$remaining" -gt 0 ]; then
      log "dry-run: unhealthy ($count/$FAILURES consecutive) — would wait, ${remaining}s of the ${COOLDOWN_MINUTES}m cooldown remain"
    else
      log "dry-run: $count consecutive unhealthy checks — would restart '$CONTAINER'"
    fi
    return 0
  fi

  if [ "$count" -lt "$FAILURES" ]; then
    write_state_number consecutive-failures "$count"
    log "unhealthy ($count/$FAILURES consecutive) — waiting for $((FAILURES - count)) more before restarting"
    return 0
  fi

  remaining=$(cooldown_remaining)
  if [ "$remaining" -gt 0 ]; then
    # Keep counting: the log should show how long the backend has been down, not just that it once
    # was.
    write_state_number consecutive-failures "$count"
    log "unhealthy ($count/$FAILURES consecutive) but ${remaining}s of the ${COOLDOWN_MINUTES}m cooldown remain — not restarting"
    return 0
  fi

  restart_container "$count"
}

# A container with no healthcheck can still be watched, if the operator says where health lives.
handle_unknown_health() {
  if [ -z "$HEALTH_URL" ]; then
    log "'$CONTAINER' reports no healthcheck and no --health-url/TDAY_WATCHDOG_HEALTH_URL is set — nothing to read, no action"
    return 1
  fi
  probe_status=0
  probe_health_url || probe_status=$?
  case "$probe_status" in
    0) record_healthy ;;
    1) handle_unhealthy ;;
    *) log "no curl or wget on this host to probe $HEALTH_URL — nothing to read, no action"; return 1 ;;
  esac
}

# A health seam without Docker is a supported setup — the self-test is one — so the CLI is only
# required when it is about to be used.
if [ -z "${TDAY_WATCHDOG_HEALTH_CMD:-}" ] && ! command -v "$DOCKER" >/dev/null 2>&1; then
  die "docker command '$DOCKER' is not on PATH — install the Docker CLI, or set --docker/TDAY_WATCHDOG_DOCKER"
fi

# The error text is merged in so a missing container and an unreachable daemon can be told apart
# and reported as themselves. On success only the first word is kept, so a stray line cannot be
# mistaken for a health state.
if read_output=$(read_health 2>&1); then
  health=$(printf '%s\n' "$read_output" | head -n 1 | tr -d '[:space:]')
else
  case "$read_output" in
    *"No such object"*) die "container '$CONTAINER' does not exist — is the stack up? (docker compose ps)" ;;
    *"Is the docker daemon running"*|*"Cannot connect"*) die "cannot reach the Docker daemon: $read_output" ;;
    *) die "could not read the health of '$CONTAINER': $read_output" ;;
  esac
fi

if [ "$STATUS_ONLY" -eq 1 ]; then
  last=$(read_last_restart)
  if [ "$last" -eq 0 ]; then
    last_display=never
  else
    last_display=$(date -u -r "$last" '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || date -u -d "@$last" '+%Y-%m-%dT%H:%M:%SZ' 2>/dev/null || printf '%s' "$last")
  fi
  printf 'container:            %s\n' "$CONTAINER"
  printf 'health:               %s\n' "$health"
  printf 'consecutive failures: %s/%s\n' "$(read_failures)" "$FAILURES"
  printf 'last restart:         %s\n' "$last_display"
  printf 'cooldown remaining:   %ss\n' "$(cooldown_remaining)"
  printf 'state directory:      %s\n' "$STATE_DIR"
  exit 0
fi

case "$health" in
  healthy)
    record_healthy
    ;;
  starting)
    log "'$CONTAINER' is starting (healthcheck start_period) — not a failure"
    ;;
  unhealthy)
    handle_unhealthy
    ;;
  none)
    handle_unknown_health
    ;;
  *)
    log "unrecognised health '$health' for '$CONTAINER' — no action"
    ;;
esac

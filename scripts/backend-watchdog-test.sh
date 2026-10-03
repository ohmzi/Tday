#!/bin/sh
# Prove scripts/backend-watchdog.sh's decision rule without Docker.
#
# The watchdog makes every decision from three inputs: the health it reads, the consecutive-failure
# count, and the time of the last restart. This test replaces the first with a file — through the
# script's own TDAY_WATCHDOG_HEALTH_CMD seam, so the documented hook is what gets exercised — and
# the docker CLI with a stub that counts restarts. That is enough to drive every branch exactly,
# including the ones that need a container to be missing or Docker to be absent:
#
#   * healthy                          -> silence, no restart, no state written
#   * one unhealthy check              -> no restart, the count moves to 1
#   * three consecutive unhealthy      -> one restart, the count resets
#   * still unhealthy inside cooldown  -> no second restart
#   * healthy again                    -> the count resets
#   * cooldown elapsed                 -> a later run of failures restarts again
#   * starting                         -> not a failure
#   * already restarting               -> left alone
#   * a missing container              -> says so, exits non-zero
#   * a missing docker CLI             -> says so, exits non-zero
#   * no healthcheck and no health URL -> says so, exits non-zero
#   * no healthcheck with a URL        -> the probe decides
#   * --dry-run                        -> reports, and writes nothing
#   * --status / --help                -> print and exit 0
#
# Usage: scripts/backend-watchdog-test.sh
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
WATCHDOG="$ROOT/scripts/backend-watchdog.sh"
FAIL_AFTER=3

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/bin"

# Stand-in for the docker CLI, handling only what the watchdog asks of it: the two inspect formats
# and restart. Restarts are counted in a file, which is what the assertions read.
cat > "$WORK/bin/docker" <<'STUB'
#!/bin/sh
# TDAY_STUB_DIR is set by the test that installed this stub.
dir=${TDAY_STUB_DIR:?the test sets TDAY_STUB_DIR}
printf '%s\n' "$*" >> "$dir/docker.calls"
verb=$1
shift
case "$verb" in
  inspect)
    # inspect --format <format> <container>
    format=$2
    if [ -f "$dir/missing" ]; then
      echo "Error: No such object: ${3:-tday_backend}" >&2
      exit 1
    fi
    case "$format" in
      *Health*) cat "$dir/health" ;;
      *Restarting*) cat "$dir/restarting" ;;
      *) echo "stub docker: unexpected inspect format: $format" >&2; exit 2 ;;
    esac
    ;;
  restart)
    count=$(cat "$dir/restarts" 2>/dev/null || echo 0)
    echo $((count + 1)) > "$dir/restarts"
    ;;
  *)
    echo "stub docker: unsupported verb: $verb" >&2
    exit 2
    ;;
esac
STUB
chmod +x "$WORK/bin/docker"

PASS=0
FAIL=0
ok() { PASS=$((PASS + 1)); printf 'ok %2d - %s\n' "$PASS" "$1"; }
no() { FAIL=$((FAIL + 1)); printf 'not ok - %s\n' "$1"; }
is() {
  # expected actual description
  if [ "$1" = "$2" ]; then ok "$3"; else no "$3 (expected '$1', got '$2')"; fi
}
contains() {
  # haystack needle description
  case "$1" in
    *"$2"*) ok "$3" ;;
    *) no "$3 (expected output to contain '$2', got: $1)" ;;
  esac
}

health() { printf '%s\n' "$1" > "$WORK/health"; }
restarting() { printf '%s\n' "$1" > "$WORK/restarting"; }
restarts() { cat "$WORK/restarts" 2>/dev/null || echo 0; }
failures() { cat "$WORK/state/consecutive-failures" 2>/dev/null || echo 0; }
last_restart() { cat "$WORK/state/last-restart" 2>/dev/null || echo 0; }
state_written() { [ -e "$WORK/state" ] && echo yes || echo no; }

reset() {
  rm -rf "$WORK/state"
  rm -f "$WORK/restarts" "$WORK/missing" "$WORK/docker.calls"
  health healthy
  restarting false
}

# Runs the watchdog the way the self-test drives it: health through the documented seam, docker
# through the stub. OUT and RC are the results the assertions read.
#   run_seam [watchdog args...]
run_seam() {
  RC=0
  OUT=$(TDAY_WATCHDOG_HEALTH_CMD="cat $WORK/health" TDAY_STUB_DIR="$WORK" \
    "$WATCHDOG" --docker "$WORK/bin/docker" --state-dir "$WORK/state" "$@" 2>&1) || RC=$?
}

# Runs it with no health seam, so the default `docker inspect` read is the one under test.
#   run_default [watchdog args...]
run_default() {
  RC=0
  OUT=$(unset TDAY_WATCHDOG_HEALTH_CMD; TDAY_STUB_DIR="$WORK" \
    "$WATCHDOG" --docker "$WORK/bin/docker" --state-dir "$WORK/state" "$@" 2>&1) || RC=$?
}

printf '# backend-watchdog decision rule (failures=%s)\n\n' "$FAIL_AFTER"

# --- healthy: nothing happens, and nothing is said -------------------------------------------------
reset
run_seam
is "$RC" 0 "healthy: exits 0"
is "$OUT" "" "healthy: prints nothing, so cron stays quiet"
is "$(restarts)" 0 "healthy: does not restart"
is "$(state_written)" no "healthy: writes no state"

# --- one unhealthy check is not enough ------------------------------------------------------------
reset
health unhealthy
run_seam
is "$(restarts)" 0 "one unhealthy check: does not restart"
is "$(failures)" 1 "one unhealthy check: the count moves to 1"
contains "$OUT" "1/$FAIL_AFTER consecutive" "one unhealthy check: says how many are still needed"

# --- the third consecutive unhealthy check restarts -----------------------------------------------
run_seam
is "$(restarts)" 0 "third check pending: the second does not restart"
is "$(failures)" 2 "third check pending: the count reaches 2"
run_seam
is "$(restarts)" 1 "three consecutive unhealthy: restarts exactly once"
is "$(failures)" 0 "restart: the failure count resets"
if [ "$(last_restart)" -gt 0 ]; then ok "restart: the restart time is recorded"; else no "restart: the restart time is recorded"; fi
contains "$OUT" "restarting 'tday_backend'" "restart: says what it is doing"

# --- but not again inside the cooldown ------------------------------------------------------------
run_seam
run_seam
run_seam
is "$(restarts)" 1 "cooldown: three more unhealthy checks do not restart again"
contains "$OUT" "cooldown remain" "cooldown: says the cooldown is the reason"
is "$(failures)" 3 "cooldown: keeps counting while it waits"

# --- healthy again resets the count ---------------------------------------------------------------
health healthy
run_seam
is "$(failures)" 0 "recovery: the failure count resets to 0"
contains "$OUT" "healthy again after 3" "recovery: reports how long it had been down"

# --- once the cooldown has passed, a new run of failures restarts again ---------------------------
printf '%s\n' "$(( $(date +%s) - 100000 ))" > "$WORK/state/last-restart"
health unhealthy
run_seam
run_seam
run_seam
is "$(restarts)" 2 "cooldown elapsed: a later run of failures restarts again"

# --- starting is the healthcheck's start_period, not a failure ------------------------------------
reset
health starting
run_seam
is "$(failures)" 0 "starting: is not counted as a failure"
is "$(restarts)" 0 "starting: does not restart"
contains "$OUT" "start_period" "starting: says why it is waiting"

# --- a container Docker is already restarting is left alone ---------------------------------------
reset
health unhealthy
restarting true
run_seam
run_seam
run_seam
is "$(restarts)" 0 "already restarting: never restarts it"
is "$(failures)" 0 "already restarting: leaves the count alone"
contains "$OUT" "already restarting" "already restarting: says so"

# --- graceful failures: missing container, missing CLI, missing healthcheck -----------------------
reset
touch "$WORK/missing"
run_default
is "$RC" 1 "container missing: exits non-zero"
contains "$OUT" "does not exist" "container missing: names the problem"
contains "$(cat "$WORK/docker.calls")" "State.Health" "container missing: reached for docker's health, not something else"

# The default read has to parse a real inspect answer too, not only its errors.
reset
run_default
is "$RC" 0 "default health read: a healthy inspect result means no action"
is "$OUT" "" "default health read: stays quiet"

reset
RC=0
OUT=$(TDAY_STUB_DIR="$WORK" "$WATCHDOG" --docker "$WORK/bin/absent-docker" --state-dir "$WORK/state" 2>&1) || RC=$?
is "$RC" 1 "docker missing: exits non-zero"
contains "$OUT" "not on PATH" "docker missing: says the CLI was not found"

reset
health none
run_default
is "$RC" 1 "no healthcheck: exits non-zero"
contains "$OUT" "--health-url" "no healthcheck: points at the flag that fixes it"

# --- with no healthcheck, --health-url is what answers --------------------------------------------
if command -v curl >/dev/null 2>&1 || command -v wget >/dev/null 2>&1; then
  reset
  health none
  run_default --health-url "http://127.0.0.1:1/"
  is "$RC" 0 "health URL: a refused probe is a decision, not an error"
  is "$(failures)" 1 "health URL: a refused probe counts as unhealthy"
else
  printf 'skip   - health URL probe (no curl or wget on this host)\n'
fi

# --- --dry-run reports and writes nothing ---------------------------------------------------------
reset
health unhealthy
run_seam --dry-run
contains "$OUT" "would wait for 2 more" "dry-run: reports the decision a single check has earned"
is "$(state_written)" no "dry-run: writes no state at all"
is "$(restarts)" 0 "dry-run: never restarts"

# A restart that is already due has to be reported as one — without being taken, and without the
# state it was decided from moving.
mkdir -p "$WORK/state"
printf '2\n' > "$WORK/state/consecutive-failures"
run_seam --dry-run
contains "$OUT" "would restart" "dry-run: reports the restart it would have made"
is "$(failures)" 2 "dry-run: leaves the failure count as it found it"
is "$(last_restart)" 0 "dry-run: records no restart time"

# --- --status and --help print and exit 0 --------------------------------------------------------
reset
run_seam --status
is "$RC" 0 "status: exits 0"
contains "$OUT" "health:" "status: prints the health"
contains "$OUT" "consecutive failures:" "status: prints the failure count"

run_seam --help
is "$RC" 0 "help: exits 0"
contains "$OUT" "Restart the T'Day backend when it is" "help: prints the header block"

printf '\n%s assertions, %s failed\n' "$((PASS + FAIL))" "$FAIL"
[ "$FAIL" -eq 0 ] || exit 1

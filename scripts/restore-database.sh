#!/usr/bin/env bash
# Restore a T'Day database dump produced by scripts/backup-database.sh.
#
# THIS IS DESTRUCTIVE. It DROPS the live database inside the Postgres container
# (compose service `database`, container `tday_db`) and recreates it from the
# dump. Every task, list, user, API key and webhook created since that dump is
# gone. There is no undo beyond the safety dump this script takes first.
#
# Usage:
#   ./scripts/restore-database.sh backups/tday-db-20260805T031500Z.dump.gz
#   ./scripts/restore-database.sh backups/....dump.gz.age        # age-encrypted
#   ./scripts/restore-database.sh backups/....dump.gz.enc        # openssl-encrypted
#   ./scripts/restore-database.sh --inspect backups/....dump.gz  # verify only, change nothing
#
# You must confirm by typing the exact phrase the script prints. For an
# unattended disaster-recovery run, export TDAY_RESTORE_CONFIRM with that same
# phrase. There is no bare --yes flag, by design.
#
# Works from any directory.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

timestamp_utc() { date -u '+%Y-%m-%dT%H:%M:%SZ'; }
log()  { printf '[restore %s] %s\n' "$(timestamp_utc)" "$*"; }
warn() { printf '[restore %s] WARNING: %s\n' "$(timestamp_utc)" "$*" >&2; }
die()  { printf '[restore %s] ERROR: %s\n' "$(timestamp_utc)" "$*" >&2; exit 1; }

usage() {
  awk 'NR == 1 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "${BASH_SOURCE[0]}"
  cat <<'EOF'

Options:
  --inspect              Decrypt + verify the dump and print what it contains, then exit
                         without touching the database. Always safe.
  --no-pre-backup        Skip the automatic safety dump of the CURRENT database.
                         Not recommended; the safety dump is your only undo.
  --no-stop-backend      Leave tday-backend running during the restore. Not recommended:
                         it will reconnect mid-restore and can write into a half-restored schema.
  --compose-file PATH    Default: <repo>/docker-compose.yaml   (TDAY_BACKUP_COMPOSE_FILE)
  --service NAME         Postgres compose service. Default: database   (TDAY_BACKUP_DB_SERVICE)
  --backend-service NAME Backend compose service. Default: tday-backend
  -h, --help             This text.

Decryption:
  *.age  needs `age` plus your identity file in TDAY_BACKUP_AGE_IDENTITY_FILE
         (or age's default identity; you will be prompted if it is passphrase-protected).
  *.enc  needs TDAY_BACKUP_PASSPHRASE in the environment. It is never printed.
EOF
}

# ---------------------------------------------------------------------------
# Arguments
# ---------------------------------------------------------------------------

DUMP_FILE=""
INSPECT_ONLY=false
PRE_BACKUP=true
STOP_BACKEND=true
COMPOSE_FILE="${TDAY_BACKUP_COMPOSE_FILE:-$ROOT/docker-compose.yaml}"
DB_SERVICE="${TDAY_BACKUP_DB_SERVICE:-database}"
BACKEND_SERVICE="tday-backend"

while [ $# -gt 0 ]; do
  case "$1" in
    --inspect)          INSPECT_ONLY=true; shift ;;
    --no-pre-backup)    PRE_BACKUP=false; shift ;;
    --no-stop-backend)  STOP_BACKEND=false; shift ;;
    --compose-file)     COMPOSE_FILE="${2:?--compose-file needs a path}"; shift 2 ;;
    --compose-file=*)   COMPOSE_FILE="${1#*=}"; shift ;;
    --service)          DB_SERVICE="${2:?--service needs a name}"; shift 2 ;;
    --service=*)        DB_SERVICE="${1#*=}"; shift ;;
    --backend-service)  BACKEND_SERVICE="${2:?--backend-service needs a name}"; shift 2 ;;
    --backend-service=*) BACKEND_SERVICE="${1#*=}"; shift ;;
    -h|--help)          usage; exit 0 ;;
    -*)                 die "unknown option: $1 (try --help)" ;;
    *)
      [ -z "$DUMP_FILE" ] || die "only one dump file can be restored at a time"
      DUMP_FILE="$1"; shift ;;
  esac
done

[ -n "$DUMP_FILE" ] || { usage; exit 1; }
[ -f "$DUMP_FILE" ] || die "dump file not found: $DUMP_FILE"
[ -r "$DUMP_FILE" ] || die "dump file is not readable: $DUMP_FILE"
DUMP_FILE="$(cd "$(dirname "$DUMP_FILE")" && pwd)/$(basename "$DUMP_FILE")"

# ---------------------------------------------------------------------------
# Preflight
# ---------------------------------------------------------------------------

command -v docker >/dev/null 2>&1 || die "docker is not installed or not on PATH"
command -v gzip   >/dev/null 2>&1 || die "gzip is not installed or not on PATH"
[ -f "$COMPOSE_FILE" ] || die "compose file not found: $COMPOSE_FILE"

if docker compose version >/dev/null 2>&1; then
  COMPOSE=(docker compose -f "$COMPOSE_FILE")
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE=(docker-compose -f "$COMPOSE_FILE")
else
  die "neither 'docker compose' nor 'docker-compose' is available"
fi

DB_CONTAINER="$("${COMPOSE[@]}" ps -q "$DB_SERVICE" 2>/dev/null || true)"
[ -n "$DB_CONTAINER" ] || die "compose service '$DB_SERVICE' is not running - start it first (docker compose up -d $DB_SERVICE)"
[ "$(docker inspect -f '{{.State.Running}}' "$DB_CONTAINER" 2>/dev/null || echo false)" = "true" ] \
  || die "container for service '$DB_SERVICE' exists but is not running"

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/tday-restore.XXXXXX")"
cleanup() { rm -rf "$WORK_DIR"; }
trap cleanup EXIT

# ---------------------------------------------------------------------------
# Decrypt (if needed) + verify the dump BEFORE anything destructive happens
# ---------------------------------------------------------------------------

PLAIN_DUMP="$WORK_DIR/restore-source.dump.gz"

case "$DUMP_FILE" in
  *.age)
    command -v age >/dev/null 2>&1 || die "'$DUMP_FILE' is age-encrypted but 'age' is not installed"
    log "decrypting with age"
    if [ -n "${TDAY_BACKUP_AGE_IDENTITY_FILE:-}" ]; then
      [ -f "$TDAY_BACKUP_AGE_IDENTITY_FILE" ] || die "TDAY_BACKUP_AGE_IDENTITY_FILE does not exist: $TDAY_BACKUP_AGE_IDENTITY_FILE"
      age --decrypt --identity "$TDAY_BACKUP_AGE_IDENTITY_FILE" -o "$PLAIN_DUMP" "$DUMP_FILE" || die "age decryption failed"
    else
      age --decrypt -o "$PLAIN_DUMP" "$DUMP_FILE" || die "age decryption failed (set TDAY_BACKUP_AGE_IDENTITY_FILE to your identity file)"
    fi
    ;;
  *.enc)
    command -v openssl >/dev/null 2>&1 || die "'$DUMP_FILE' is openssl-encrypted but 'openssl' is not installed"
    [ -n "${TDAY_BACKUP_PASSPHRASE:-}" ] || die "'$DUMP_FILE' is openssl-encrypted - set TDAY_BACKUP_PASSPHRASE in the environment"
    log "decrypting with openssl"
    # -pass env: keeps the passphrase out of argv; it is never echoed.
    openssl enc -d -aes-256-cbc -md sha512 -pbkdf2 -iter 600000 \
      -in "$DUMP_FILE" -out "$PLAIN_DUMP" -pass env:TDAY_BACKUP_PASSPHRASE \
      || die "openssl decryption failed (wrong passphrase, or the file was encrypted with different settings)"
    ;;
  *.gz)
    PLAIN_DUMP="$DUMP_FILE"
    ;;
  *)
    die "unrecognised dump: expected a .dump.gz from scripts/backup-database.sh (optionally .age / .enc)"
    ;;
esac

gzip -t "$PLAIN_DUMP" 2>/dev/null || die "dump failed gzip integrity check - it is truncated or corrupt, do NOT restore from it"

# `pg_restore --list` reads only the header + TOC at the front of the archive and exits,
# leaving the feeding gzip to die on SIGPIPE. Under `set -o pipefail` that would reject a
# healthy dump (and make --inspect useless), so this is judged on pg_restore's own exit
# status; the gzip -t above already established that the stream itself is intact.
TOC_STATUS=0
set +o pipefail
gzip -dc "$PLAIN_DUMP" 2>/dev/null \
  | "${COMPOSE[@]}" exec -T "$DB_SERVICE" pg_restore --list > "$WORK_DIR/toc.txt" 2>"$WORK_DIR/toc.err" \
  || TOC_STATUS=$?
set -o pipefail
if [ "$TOC_STATUS" -ne 0 ]; then
  warn "$(head -n 3 "$WORK_DIR/toc.err" 2>/dev/null || true)"
  die "dump is not a readable pg_restore archive"
fi
TOC_ENTRIES="$(grep -c '^[0-9]' "$WORK_DIR/toc.txt" || true)"
TABLE_COUNT="$(grep -c 'TABLE DATA' "$WORK_DIR/toc.txt" || true)"
ARCHIVE_HEADER="$(grep -m 1 'Archive created at' "$WORK_DIR/toc.txt" | sed 's/^;[[:space:]]*//' || true)"

log "dump verified: ${TOC_ENTRIES} restorable objects, ${TABLE_COUNT} tables with data"
if [ -n "$ARCHIVE_HEADER" ]; then log "$ARCHIVE_HEADER"; fi

if [ "$INSPECT_ONLY" = true ]; then
  log "--inspect: nothing was changed."
  exit 0
fi

# ---------------------------------------------------------------------------
# Read the target identity from the container, then demand confirmation
# ---------------------------------------------------------------------------

DB_NAME="$("${COMPOSE[@]}" exec -T "$DB_SERVICE" printenv POSTGRES_DB 2>/dev/null | tr -d '\r\n' || true)"
DB_USER="$("${COMPOSE[@]}" exec -T "$DB_SERVICE" printenv POSTGRES_USER 2>/dev/null | tr -d '\r\n' || true)"
[ -n "$DB_NAME" ] || die "could not read POSTGRES_DB from the '$DB_SERVICE' container"
[ -n "$DB_USER" ] || die "could not read POSTGRES_USER from the '$DB_SERVICE' container"

CONFIRM_PHRASE="RESTORE $DB_NAME"

cat >&2 <<EOF

  ##########################################################################
  #  DESTRUCTIVE OPERATION - READ THIS                                     #
  ##########################################################################

  Target container : $DB_SERVICE (${DB_CONTAINER:0:12})
  Target database  : $DB_NAME  (owner: $DB_USER)
  Source dump      : $DUMP_FILE

  The database "$DB_NAME" will be DROPPED and rebuilt from that dump.
  ALL current data is overwritten: every task, list, user account,
  credential hash, API key, webhook and calendar-feed token created after
  the dump was taken is permanently lost.

  Type exactly:  $CONFIRM_PHRASE
  Anything else aborts.

EOF

if [ -n "${TDAY_RESTORE_CONFIRM:-}" ]; then
  [ "$TDAY_RESTORE_CONFIRM" = "$CONFIRM_PHRASE" ] \
    || die "TDAY_RESTORE_CONFIRM is set but does not match the required phrase - aborting"
  log "confirmation supplied via TDAY_RESTORE_CONFIRM"
elif [ -t 0 ]; then
  printf 'Confirm: ' >&2
  read -r REPLY_PHRASE || die "no confirmation read - aborting"
  [ "$REPLY_PHRASE" = "$CONFIRM_PHRASE" ] || die "confirmation did not match - nothing was changed"
else
  die "not running on a terminal and TDAY_RESTORE_CONFIRM is not set - refusing to restore unconfirmed"
fi

# ---------------------------------------------------------------------------
# Safety dump of what is about to be destroyed
# ---------------------------------------------------------------------------

if [ "$PRE_BACKUP" = true ]; then
  # Restoring an encrypted dump only proves the operator has the DECRYPT key; it says
  # nothing about TDAY_BACKUP_ENCRYPTION. Without that set, the safety dump lands
  # unencrypted next to the encrypted ones, which is a surprise worth naming out loud.
  case "$DUMP_FILE" in
    *.age|*.enc)
      case "${TDAY_BACKUP_ENCRYPTION:-none}" in
        age|openssl) ;;
        *) warn "restoring an ENCRYPTED dump but TDAY_BACKUP_ENCRYPTION is not set - the safety dump will be written in PLAINTEXT (password hashes, API keys, webhook secrets). Set TDAY_BACKUP_ENCRYPTION to match if that is not what you want." ;;
      esac
      ;;
  esac
  log "taking a safety dump of the CURRENT database first"
  # Inherits the operator's own TDAY_BACKUP_* settings (including encryption).
  # Retention is forced off so a recovery run can never prune older dumps.
  # --compose-file/--service are forwarded explicitly: without them a non-default
  # target would be dropped here but safety-dumped from the DEFAULT service, i.e. the
  # operator's only undo would be a dump of the wrong database.
  if "$ROOT/scripts/backup-database.sh" --retention-days 0 \
       --compose-file "$COMPOSE_FILE" --service "$DB_SERVICE"; then
    log "safety dump written - if this restore goes wrong, that file is your way back"
  else
    die "safety dump failed - refusing to overwrite the live database (use --no-pre-backup to override, at your own risk)"
  fi
fi

# ---------------------------------------------------------------------------
# Restore
# ---------------------------------------------------------------------------

BACKEND_WAS_RUNNING=false
if [ "$STOP_BACKEND" = true ]; then
  BACKEND_CONTAINER="$("${COMPOSE[@]}" ps -q "$BACKEND_SERVICE" 2>/dev/null || true)"
  if [ -n "$BACKEND_CONTAINER" ] && [ "$(docker inspect -f '{{.State.Running}}' "$BACKEND_CONTAINER" 2>/dev/null || echo false)" = "true" ]; then
    BACKEND_WAS_RUNNING=true
    log "stopping '$BACKEND_SERVICE' so it cannot write during the restore"
    "${COMPOSE[@]}" stop "$BACKEND_SERVICE" >/dev/null || warn "could not stop '$BACKEND_SERVICE' - continuing anyway"
  fi
fi

# Drop and recreate rather than pg_restore --clean: a fresh database restores to
# exactly the dump's state, with no leftovers from the schema it replaced.
# Heredoc (not sh -c '...') so the psql :'var' quoting survives intact.
log "dropping and recreating database '$DB_NAME'"
if ! "${COMPOSE[@]}" exec -T "$DB_SERVICE" sh -s <<'INNER'
set -e
export PGPASSWORD="${POSTGRES_PASSWORD:-}"
psql -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres \
  -v dbname="$POSTGRES_DB" \
  -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = :'dbname' AND pid <> pg_backend_pid();" \
  > /dev/null
# Separate psql invocations: DROP/CREATE DATABASE cannot run inside a transaction block.
psql -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres \
  -v dbname="$POSTGRES_DB" \
  -c "DROP DATABASE IF EXISTS :\"dbname\";"
psql -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d postgres \
  -v dbname="$POSTGRES_DB" -v owner="$POSTGRES_USER" \
  -c "CREATE DATABASE :\"dbname\" OWNER :\"owner\";"
INNER
then
  die "could not drop/recreate '$DB_NAME' - the database is untouched or partially dropped; check 'docker compose logs $DB_SERVICE'"
fi

log "restoring ${TOC_ENTRIES} objects into '$DB_NAME'"
RESTORE_STATUS=0
gzip -dc "$PLAIN_DUMP" \
  | "${COMPOSE[@]}" exec -T "$DB_SERVICE" sh -c '
      PGPASSWORD="${POSTGRES_PASSWORD:-}" exec pg_restore \
        --no-owner --no-privileges --single-transaction \
        --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"
    ' 2>"$WORK_DIR/restore.err" || RESTORE_STATUS=$?

if [ "$RESTORE_STATUS" -ne 0 ]; then
  warn "pg_restore output:"
  cat "$WORK_DIR/restore.err" >&2 || true
  if [ "$BACKEND_WAS_RUNNING" = true ]; then
    warn "leaving '$BACKEND_SERVICE' stopped - do not start it against a half-restored database"
  fi
  die "restore FAILED (pg_restore exit $RESTORE_STATUS). --single-transaction means nothing was committed; '$DB_NAME' is empty. Fix the cause and re-run, or restore the safety dump."
fi

if [ -s "$WORK_DIR/restore.err" ]; then cat "$WORK_DIR/restore.err" >&2; fi

if [ "$BACKEND_WAS_RUNNING" = true ]; then
  log "starting '$BACKEND_SERVICE'"
  "${COMPOSE[@]}" start "$BACKEND_SERVICE" >/dev/null || warn "could not restart '$BACKEND_SERVICE' - start it manually"
fi

log "restore complete."
log "Now verify: log in, check a task list, and confirm 'GET /health' returns {\"status\":\"ok\"}."

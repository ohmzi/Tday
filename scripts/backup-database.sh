#!/usr/bin/env bash
# Full-database backup for the self-hosted T'Day stack.
#
# Runs pg_dump *inside* the running Postgres container (compose service
# `database`, container `tday_db`) and writes a timestamped, gzip-compressed
# custom-format dump to a backup directory on the host.
#
# Unlike the in-app JSON export (GET /api/export), this captures EVERYTHING:
# the users table, credential hashes, API keys, webhooks, calendar-feed tokens,
# sessions and the encrypted task fields exactly as stored. See
# docs/security/backups.md.
#
# Usage:
#   ./scripts/backup-database.sh                          # dump to ./backups, no encryption
#   ./scripts/backup-database.sh --dir /mnt/nas/tday      # different destination
#   ./scripts/backup-database.sh --retention-days 90      # keep 90 days
#   ./scripts/backup-database.sh --encrypt age            # encrypt to an age recipient
#   ./scripts/backup-database.sh --encrypt openssl        # encrypt with a passphrase
#   ./scripts/backup-database.sh --list                   # show what is already there
#
# Encryption is OPT-IN and off by default; see --help and the docs.
# Works from any directory. Safe to run repeatedly (each run is a new file).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

timestamp_utc() { date -u '+%Y-%m-%dT%H:%M:%SZ'; }
log()  { printf '[backup %s] %s\n' "$(timestamp_utc)" "$*"; }
warn() { printf '[backup %s] WARNING: %s\n' "$(timestamp_utc)" "$*" >&2; }
die()  { printf '[backup %s] ERROR: %s\n' "$(timestamp_utc)" "$*" >&2; exit 1; }

usage() {
  # Print the header comment block (everything after the shebang up to the first
  # non-comment line) so usage text and file header can never drift apart.
  awk 'NR == 1 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "${BASH_SOURCE[0]}"
  cat <<'EOF'

Configuration (CLI flag wins over environment variable):

  --dir DIR / TDAY_BACKUP_DIR
      Destination directory. Default: <repo>/backups (created mode 700).
  --retention-days N / TDAY_BACKUP_RETENTION_DAYS
      Delete this script's own dumps older than N days. Default: 30. 0 disables pruning.
  --encrypt MODE / TDAY_BACKUP_ENCRYPTION
      none (default) | age | openssl.
        age     - needs TDAY_BACKUP_AGE_RECIPIENT (an age public key, `age1...`)
                  or TDAY_BACKUP_AGE_RECIPIENTS_FILE. Public-key encryption, so the
                  host never holds the key that can decrypt the backup. Preferred.
        openssl - needs TDAY_BACKUP_PASSPHRASE in the environment. AES-256-CBC,
                  PBKDF2-SHA512, 600000 iterations. The passphrase is never printed
                  and never passed on the command line.
  --no-encrypt
      Force plaintext even if TDAY_BACKUP_ENCRYPTION is set.
  --min-bytes N / TDAY_BACKUP_MIN_BYTES
      Refuse to keep a dump smaller than this. Default: 1024.
  --compose-file PATH / TDAY_BACKUP_COMPOSE_FILE
      Default: <repo>/docker-compose.yaml.
  --service NAME / TDAY_BACKUP_DB_SERVICE
      Compose service name of Postgres. Default: database.
  --env-file PATH / TDAY_BACKUP_ENV_FILE
      File to read TDAY_BACKUP_* defaults from. Default: <repo>/.env.
      Only lines starting with TDAY_BACKUP_ are read, and the real environment wins.
  --list
      List existing dumps in the backup directory and exit.
  -h, --help
      This text.

Exit code is non-zero on any failure; a failed dump is never left behind as a
usable-looking file.
EOF
}

# ---------------------------------------------------------------------------
# Config: env file -> environment -> CLI flags
# ---------------------------------------------------------------------------

# cron has almost no environment, so allow TDAY_BACKUP_* defaults to live in a
# file. Parsed line-by-line rather than sourced: this file is never executed.
load_env_file() {
  local file="$1" line key value current
  [ -f "$file" ] || return 0
  while IFS= read -r line || [ -n "$line" ]; do
    [[ "$line" =~ ^TDAY_BACKUP_[A-Za-z0-9_]+= ]] || continue
    key="${line%%=*}"
    value="${line#*=}"
    case "$value" in
      \"*\") value="${value#\"}"; value="${value%\"}" ;;
      \'*\') value="${value#\'}"; value="${value%\'}" ;;
    esac
    current="${!key:-}"
    [ -n "$current" ] || export "$key=$value"
  done < "$file"
}

ENV_FILE="${TDAY_BACKUP_ENV_FILE:-$ROOT/.env}"
# --env-file has to be honoured before the file is read, so pre-scan for it.
prev=""
for arg in "$@"; do
  if [ "$prev" = "--env-file" ]; then ENV_FILE="$arg"; fi
  case "$arg" in --env-file=*) ENV_FILE="${arg#*=}" ;; esac
  prev="$arg"
done
load_env_file "$ENV_FILE"

BACKUP_DIR="${TDAY_BACKUP_DIR:-$ROOT/backups}"
RETENTION_DAYS="${TDAY_BACKUP_RETENTION_DAYS:-30}"
ENCRYPTION="${TDAY_BACKUP_ENCRYPTION:-none}"
MIN_BYTES="${TDAY_BACKUP_MIN_BYTES:-1024}"
COMPOSE_FILE="${TDAY_BACKUP_COMPOSE_FILE:-$ROOT/docker-compose.yaml}"
DB_SERVICE="${TDAY_BACKUP_DB_SERVICE:-database}"
LIST_ONLY=false

while [ $# -gt 0 ]; do
  case "$1" in
    --dir)             BACKUP_DIR="${2:?--dir needs a path}"; shift 2 ;;
    --dir=*)           BACKUP_DIR="${1#*=}"; shift ;;
    --retention-days)  RETENTION_DAYS="${2:?--retention-days needs a number}"; shift 2 ;;
    --retention-days=*) RETENTION_DAYS="${1#*=}"; shift ;;
    --encrypt)         ENCRYPTION="${2:?--encrypt needs none|age|openssl}"; shift 2 ;;
    --encrypt=*)       ENCRYPTION="${1#*=}"; shift ;;
    --no-encrypt)      ENCRYPTION="none"; shift ;;
    --min-bytes)       MIN_BYTES="${2:?--min-bytes needs a number}"; shift 2 ;;
    --min-bytes=*)     MIN_BYTES="${1#*=}"; shift ;;
    --compose-file)    COMPOSE_FILE="${2:?--compose-file needs a path}"; shift 2 ;;
    --compose-file=*)  COMPOSE_FILE="${1#*=}"; shift ;;
    --service)         DB_SERVICE="${2:?--service needs a name}"; shift 2 ;;
    --service=*)       DB_SERVICE="${1#*=}"; shift ;;
    --env-file)        [ $# -ge 2 ] || die "--env-file needs a path"; shift 2 ;;  # already consumed in the pre-scan
    --env-file=*)      shift ;;
    --list)            LIST_ONLY=true; shift ;;
    -h|--help)         usage; exit 0 ;;
    *)                 die "unknown argument: $1 (try --help)" ;;
  esac
done

case "$RETENTION_DAYS" in ''|*[!0-9]*) die "--retention-days must be a whole number, got '$RETENTION_DAYS'" ;; esac
case "$MIN_BYTES"      in ''|*[!0-9]*) die "--min-bytes must be a whole number, got '$MIN_BYTES'" ;; esac
case "$ENCRYPTION"     in none|age|openssl) ;; *) die "--encrypt must be none, age or openssl (got '$ENCRYPTION')" ;; esac

FILE_PREFIX="tday-db-"

if [ "$LIST_ONLY" = true ]; then
  [ -d "$BACKUP_DIR" ] || die "no backup directory at $BACKUP_DIR"
  log "dumps in $BACKUP_DIR:"
  ls -lh "$BACKUP_DIR" | grep -- "$FILE_PREFIX" || log "(none)"
  exit 0
fi

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
[ -n "$DB_CONTAINER" ] || die "compose service '$DB_SERVICE' is not running - start the stack first (docker compose up -d $DB_SERVICE)"
[ "$(docker inspect -f '{{.State.Running}}' "$DB_CONTAINER" 2>/dev/null || echo false)" = "true" ] \
  || die "container for service '$DB_SERVICE' exists but is not running"

# Encryption tooling has to exist BEFORE we spend minutes on a dump.
AGE_ARGS=()
case "$ENCRYPTION" in
  age)
    command -v age >/dev/null 2>&1 || die "--encrypt age requested but 'age' is not installed (https://github.com/FiloSottile/age)"
    if [ -n "${TDAY_BACKUP_AGE_RECIPIENTS_FILE:-}" ]; then
      [ -f "$TDAY_BACKUP_AGE_RECIPIENTS_FILE" ] || die "TDAY_BACKUP_AGE_RECIPIENTS_FILE does not exist: $TDAY_BACKUP_AGE_RECIPIENTS_FILE"
      AGE_ARGS=(--recipients-file "$TDAY_BACKUP_AGE_RECIPIENTS_FILE")
    elif [ -n "${TDAY_BACKUP_AGE_RECIPIENT:-}" ]; then
      AGE_ARGS=(--recipient "$TDAY_BACKUP_AGE_RECIPIENT")
    else
      die "--encrypt age needs TDAY_BACKUP_AGE_RECIPIENT (age1... public key) or TDAY_BACKUP_AGE_RECIPIENTS_FILE"
    fi
    ;;
  openssl)
    command -v openssl >/dev/null 2>&1 || die "--encrypt openssl requested but 'openssl' is not installed"
    # Never echo, log or interpolate this value anywhere.
    [ -n "${TDAY_BACKUP_PASSPHRASE:-}" ] || die "--encrypt openssl needs TDAY_BACKUP_PASSPHRASE in the environment"
    ;;
esac

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR" 2>/dev/null || warn "could not chmod 700 $BACKUP_DIR - check who can read your dumps"
[ -w "$BACKUP_DIR" ] || die "backup directory is not writable: $BACKUP_DIR"

# The default destination sits inside the repo; keep dumps out of git status and
# out of any accidental `git add -A`. Self-contained: never edits the repo's .gitignore.
if [ ! -e "$BACKUP_DIR/.gitignore" ]; then
  printf '# Database dumps - never commit these.\n*\n' > "$BACKUP_DIR/.gitignore"
fi

# ---------------------------------------------------------------------------
# Dump
# ---------------------------------------------------------------------------

STAMP="$(date -u '+%Y%m%dT%H%M%SZ')"
case "$ENCRYPTION" in
  age)     SUFFIX=".dump.gz.age" ;;
  openssl) SUFFIX=".dump.gz.enc" ;;
  *)       SUFFIX=".dump.gz" ;;
esac
# The stamp is second-granular, so two runs in the same second would collide.
# Never overwrite an existing dump - pick the next free name instead.
FINAL_NAME="${FILE_PREFIX}${STAMP}${SUFFIX}"
n=1
while [ -e "$BACKUP_DIR/$FINAL_NAME" ]; do
  [ "$n" -le 50 ] || die "cannot find a free filename in $BACKUP_DIR for ${FILE_PREFIX}${STAMP}*"
  FINAL_NAME="${FILE_PREFIX}${STAMP}-${n}${SUFFIX}"
  n=$((n + 1))
done
BASE_NAME="${FINAL_NAME%.age}"
BASE_NAME="${BASE_NAME%.enc}"
FINAL_PATH="$BACKUP_DIR/$FINAL_NAME"
# Staged inside the destination directory so the final rename is same-filesystem
# and therefore atomic: a reader never sees a partly written dump. The dot
# prefix keeps it out of --list and out of the retention glob.
PART_PATH="$BACKUP_DIR/.${FINAL_NAME}.part"
# The plaintext dump is assembled in TMPDIR, not in the backup directory, so an
# encrypted backup never leaves cleartext on the backup medium.
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/tday-backup.XXXXXX")"
RAW_PATH="$WORK_DIR/$BASE_NAME"

cleanup() { rm -rf "$WORK_DIR"; rm -f "$PART_PATH"; }
trap cleanup EXIT

log "dumping compose service '$DB_SERVICE' (container ${DB_CONTAINER:0:12}) -> $FINAL_PATH"

# Credentials are read from the container's own environment (POSTGRES_USER /
# POSTGRES_PASSWORD / POSTGRES_DB set by docker-compose.yaml), so nothing is
# hardcoded here and the password never reaches the host process table.
# Custom format (-Fc) keeps pg_restore's selective-restore ability; compression
# is done by gzip on the host so `gzip -t` can prove the file is not truncated.
if ! "${COMPOSE[@]}" exec -T "$DB_SERVICE" sh -c '
    set -e
    [ -n "${POSTGRES_USER:-}" ] || { echo "POSTGRES_USER is not set in the database container" >&2; exit 1; }
    [ -n "${POSTGRES_DB:-}" ]   || { echo "POSTGRES_DB is not set in the database container" >&2; exit 1; }
    PGPASSWORD="${POSTGRES_PASSWORD:-}" exec pg_dump \
      --format=custom --compress=0 --no-owner --no-privileges \
      --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"
  ' | gzip -9 > "$RAW_PATH"
then
  die "pg_dump failed - no backup was written"
fi

# ---------------------------------------------------------------------------
# Verify before anything is allowed into the backup directory
# ---------------------------------------------------------------------------

file_size() { wc -c < "$1" | tr -d '[:space:]'; }

SIZE="$(file_size "$RAW_PATH")"
[ "$SIZE" -gt 0 ] 2>/dev/null || die "pg_dump produced a zero-byte dump - refusing to keep it"
[ "$SIZE" -ge "$MIN_BYTES" ] || die "dump is only ${SIZE} bytes (minimum ${MIN_BYTES}) - refusing to keep a truncated backup"

# gzip -t walks the whole stream and validates the CRC + length trailer, so a
# dump cut short by a full disk or a killed container fails here.
gzip -t "$RAW_PATH" 2>/dev/null || die "dump failed gzip integrity check (truncated or corrupt) - refusing to keep it"

# Structural check: pg_restore must be able to parse the archive's table of contents.
# A custom-format archive keeps its header and TOC at the front, so `pg_restore --list`
# prints them and exits WITHOUT draining the rest of stdin. The feeding gzip then dies on
# SIGPIPE (141), and under `set -o pipefail` that would reject a perfectly good dump - so
# judge this pipeline on pg_restore's own exit status. gzip -t above already proved the
# stream is complete and uncorrupted, which is why gzip's status is not needed here (and
# why its broken-pipe noise is dropped rather than mailed out by cron on every run).
TOC_STATUS=0
set +o pipefail
gzip -dc "$RAW_PATH" 2>/dev/null \
  | "${COMPOSE[@]}" exec -T "$DB_SERVICE" pg_restore --list > "$WORK_DIR/toc.txt" 2>"$WORK_DIR/toc.err" \
  || TOC_STATUS=$?
set -o pipefail
if [ "$TOC_STATUS" -ne 0 ]; then
  if [ -s "$WORK_DIR/toc.err" ]; then head -n 3 "$WORK_DIR/toc.err" >&2; fi
  die "dump is not a readable pg_restore archive - refusing to keep it"
fi
TOC_ENTRIES="$(grep -c '^[0-9]' "$WORK_DIR/toc.txt" || true)"
[ "${TOC_ENTRIES:-0}" -gt 0 ] || die "dump contains no restorable objects - refusing to keep it"

log "verified: ${SIZE} bytes gzip-clean, ${TOC_ENTRIES} restorable objects"

# ---------------------------------------------------------------------------
# Optional encryption at rest
# ---------------------------------------------------------------------------

case "$ENCRYPTION" in
  age)
    age "${AGE_ARGS[@]}" -o "$WORK_DIR/$FINAL_NAME" "$RAW_PATH" || die "age encryption failed"
    rm -f "$RAW_PATH"
    log "encrypted with age (decrypt with your age identity)"
    ;;
  openssl)
    # -pass env: keeps the passphrase out of argv and out of any log line.
    openssl enc -aes-256-cbc -md sha512 -pbkdf2 -iter 600000 -salt \
      -in "$RAW_PATH" -out "$WORK_DIR/$FINAL_NAME" -pass env:TDAY_BACKUP_PASSPHRASE \
      || die "openssl encryption failed"
    rm -f "$RAW_PATH"
    log "encrypted with openssl aes-256-cbc (pbkdf2, 600000 iterations)"
    ;;
  none)
    log "NOT encrypted - this file contains credential hashes, API keys and task data in restorable form"
    ;;
esac

[ -s "$WORK_DIR/$FINAL_NAME" ] || die "post-processing produced an empty file - refusing to keep it"

# ---------------------------------------------------------------------------
# Publish + checksum + prune
# ---------------------------------------------------------------------------

chmod 600 "$WORK_DIR/$FINAL_NAME"
cat "$WORK_DIR/$FINAL_NAME" > "$PART_PATH" || die "could not write to $BACKUP_DIR (out of space?)"
chmod 600 "$PART_PATH"
[ "$(file_size "$PART_PATH")" = "$(file_size "$WORK_DIR/$FINAL_NAME")" ] \
  || die "short write into $BACKUP_DIR - refusing to publish a truncated backup (out of space?)"
mv "$PART_PATH" "$FINAL_PATH"

if command -v shasum >/dev/null 2>&1; then
  (cd "$BACKUP_DIR" && shasum -a 256 "$FINAL_NAME" > "${FINAL_NAME}.sha256")
elif command -v sha256sum >/dev/null 2>&1; then
  (cd "$BACKUP_DIR" && sha256sum "$FINAL_NAME" > "${FINAL_NAME}.sha256")
else
  warn "no shasum/sha256sum available - skipping checksum sidecar"
fi

log "wrote $FINAL_PATH ($(file_size "$FINAL_PATH") bytes)"

if [ "$RETENTION_DAYS" -gt 0 ]; then
  # -name is anchored on this script's own prefix so nothing else in the
  # directory can be deleted, whatever the operator points --dir at.
  # The new dump is already published at this point, so a prune error must not
  # exit non-zero and report a good backup as a failed one to cron.
  if PRUNED="$(find "$BACKUP_DIR" -maxdepth 1 -type f -name "${FILE_PREFIX}*" -mtime "+${RETENTION_DAYS}" -print -delete | wc -l | tr -d '[:space:]')"; then
    log "retention ${RETENTION_DAYS}d: pruned ${PRUNED} old file(s)"
  else
    warn "retention sweep failed in $BACKUP_DIR - the new backup is fine, but old dumps were not pruned"
  fi
else
  log "retention disabled (0) - nothing pruned"
fi

log "done. Restore with: $ROOT/scripts/restore-database.sh $FINAL_PATH"

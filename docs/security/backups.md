# Database Backups

Everything T'Day knows lives in one place: the Docker named volume `postgres_data`, mounted at
`/var/lib/postgresql/data` inside the `database` service (container `tday_db`, see
`docker-compose.yaml`). There is no replica and no second copy. A corrupted volume, a failed
migration, a dying disk, or a single `docker compose down -v` destroys every task, list and
account permanently.

This page covers the two scripts that fix that:

| Script | Purpose |
|---|---|
| `scripts/backup-database.sh` | `pg_dump` the whole database to a timestamped, verified, optionally encrypted file |
| `scripts/restore-database.sh` | Restore one of those files back into the running stack (destructive, requires typed confirmation) |

Both are plain Bash, `set -euo pipefail`, executable, and work from any directory.

> **Nothing runs automatically after you clone this repo.** No timer, no cron entry, no compose
> service. Backups start happening only once you schedule them yourself — see
> [Scheduling](#scheduling). That is deliberate: a self-hosted box should not quietly fill its disk
> with dumps the owner never asked for.

---

## Quick start

```bash
# one manual backup into ./backups, unencrypted
./scripts/backup-database.sh

# what do I have?
./scripts/backup-database.sh --list

# verify a dump without touching anything
./scripts/restore-database.sh --inspect backups/tday-db-20260805T031500Z.dump.gz
```

---

## What is and is not backed up

### In the dump

`backup-database.sh` runs `pg_dump --format=custom` against `$POSTGRES_DB` as `$POSTGRES_USER`,
so the file contains **the entire application database**:

- every user row (`public."User"`), including password hashes and security questions
- `user_api_keys`, `calendar_feed_tokens`, `webhook_subscriptions`, `push_subscriptions`
- `account` / `verificationtoken` (OAuth links and tokens)
- all task data — `todos`, `todo_instances`, `task_steps`, `completedtodo`, `project`,
  floaters, lists and `list_shares` / `floater_list_shares`
- `userpreferences`
- security and operational tables — `eventlog`, `authsignal`, `auththrottle`, `cronlog`
- the Flyway `flyway_schema_history` table, so a restored database knows its own migration state

Encrypted columns are dumped **exactly as stored, still ciphertext**. See
[the encryption-key trap](#the-encryption-key-trap-read-this) — the dump alone is not enough to
recover them.

### Not in the dump

- **Your secrets.** `.env`, `.env.docker`, `AUTH_SECRET`, `DATA_ENCRYPTION_KEY`,
  `DATA_ENCRYPTION_KEYS`, `AUTH_CREDENTIALS_PRIVATE_KEY`, VAPID keys. Back these up separately,
  in a password manager or secret store. Without them a restored database is partly unreadable
  and every existing session/API key becomes invalid.
- **The Ollama models volume** (`ollama_data`). Re-pulled on demand; not worth backing up.
- **Uploaded blobs outside Postgres**, if you ever add any.
- **Client-side Local Mode data** on Android/iOS. That never reaches the server.

### The in-app JSON export is NOT a backup

T'Day has a per-user export/import feature — `GET /api/export` and `POST /api/import`
(`tday-backend/src/main/kotlin/com/ohmz/tday/routes/ExportRoutes.kt`,
`services/ExportService.kt`), surfaced in the web Settings screen. It is a portability feature,
not a backup, and it must not be relied on as one:

- **User-scoped.** `exportAll(userId)` selects only the calling user's rows. Other accounts are not
  in the file at all.
- **No accounts.** The `User` table, password hashes, security questions, OAuth `account` rows and
  sessions are omitted entirely. You cannot log in to a server rebuilt from it.
- **No credentials or integrations.** `user_api_keys`, `calendar_feed_tokens`,
  `webhook_subscriptions` and `push_subscriptions` are not exported.
- **No operational tables.** No `eventlog`, `authsignal`, `auththrottle`, `cronlog`, no Flyway
  history.
- **Plaintext.** The bundle is decrypted on the way out and written as clear JSON wherever the
  user saves it. Field encryption at rest protects the database; it does not protect that file.
- **Manual.** Someone has to remember to press the button.

Use it to move one account between servers. Use `pg_dump` to survive a disk failure.

---

## Running a backup

```bash
./scripts/backup-database.sh
```

What it does, in order:

1. Finds `docker compose` (or `docker-compose`) and confirms the `database` service is actually
   running. If it is not, it exits non-zero and writes nothing.
2. Runs `pg_dump` **inside** the container via `docker compose exec -T database`, reading
   `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` from that container's own environment.
   No credential is hardcoded in the script, and the password never appears on the host's process
   list.
3. Streams the archive to a temp file and gzips it on the host.
4. **Verifies before keeping anything**: non-zero size, at least `--min-bytes` (1024 by default),
   a clean `gzip -t` (which validates the CRC and length trailer, so a dump cut short by a full
   disk fails here), and a successful `pg_restore --list` with at least one restorable object.
   Any failure exits non-zero and leaves nothing behind — there is never a half-written file in
   the backup directory pretending to be a backup.
5. Optionally encrypts (see below).
6. Moves the finished file into place `chmod 600`, writes a `.sha256` sidecar.
7. Prunes its own dumps older than the retention window.

Output looks like `backups/tday-db-20260805T031500Z.dump.gz` (UTC timestamp). Two runs in the
same second get a `-1`, `-2` suffix; an existing file is never overwritten.

The backup directory is created mode `700` and gets its own `.gitignore` containing `*`, so dumps
can sit in the repo without ever landing in `git status` or a `git add -A`.

### Options

| Flag | Env var | Default | Meaning |
|---|---|---|---|
| `--dir DIR` | `TDAY_BACKUP_DIR` | `<repo>/backups` | Where dumps go |
| `--retention-days N` | `TDAY_BACKUP_RETENTION_DAYS` | `30` | Delete this script's own dumps older than N days. `0` disables pruning |
| `--encrypt MODE` | `TDAY_BACKUP_ENCRYPTION` | `none` | `none` \| `age` \| `openssl` |
| `--no-encrypt` | — | — | Force plaintext regardless of config |
| `--min-bytes N` | `TDAY_BACKUP_MIN_BYTES` | `1024` | Truncation floor |
| `--compose-file PATH` | `TDAY_BACKUP_COMPOSE_FILE` | `<repo>/docker-compose.yaml` | |
| `--service NAME` | `TDAY_BACKUP_DB_SERVICE` | `database` | Postgres compose service |
| `--env-file PATH` | `TDAY_BACKUP_ENV_FILE` | `<repo>/.env` | Where `TDAY_BACKUP_*` defaults are read from |
| `--list` | — | — | Show existing dumps and exit |

`--env-file` exists because cron runs with almost no environment. Only lines beginning with
`TDAY_BACKUP_` are read, the file is **parsed, never sourced or executed**, and a variable already
set in the real environment always wins.

`--retention-days` deletes only files matching this script's own `tday-db-*` prefix, so pointing
`--dir` at a shared directory cannot make it delete something else.

---

## Encryption at rest

**Encryption is opt-in and off by default.** An unencrypted dump is a complete copy of every
password hash, API key, calendar-feed token and webhook secret on your server. If it leaves the
box — NAS, external disk, cloud sync — encrypt it.

### age (preferred)

```bash
# once: create a keypair, keep the private key somewhere the server cannot reach
age-keygen -o ~/tday-backup-identity.txt      # prints the public key: age1...

export TDAY_BACKUP_ENCRYPTION=age
export TDAY_BACKUP_AGE_RECIPIENT=age1qz...     # public key only
./scripts/backup-database.sh
# -> backups/tday-db-20260805T031500Z.dump.gz.age
```

Public-key encryption, so the backup host never holds anything that can decrypt its own backups.
That is the property you want if the server is what gets compromised.
`TDAY_BACKUP_AGE_RECIPIENTS_FILE` works too, if you want more than one recipient.

To restore, put the private key where the restore runs:

```bash
export TDAY_BACKUP_AGE_IDENTITY_FILE=~/tday-backup-identity.txt
./scripts/restore-database.sh backups/tday-db-....dump.gz.age
```

### openssl (passphrase)

Use when `age` is not available.

```bash
export TDAY_BACKUP_ENCRYPTION=openssl
export TDAY_BACKUP_PASSPHRASE='...'            # long and random
./scripts/backup-database.sh
# -> backups/tday-db-20260805T031500Z.dump.gz.enc
```

AES-256-CBC, PBKDF2-SHA512, 600000 iterations, random salt. The passphrase is passed to `openssl`
via `-pass env:` — it never appears in the command line, in the logs, or in any error message the
script prints. Two honest caveats:

- The passphrase has to be readable by whatever runs the backup, so a symmetric passphrase on the
  same host is strictly weaker than `age`'s public-key model.
- If you put `TDAY_BACKUP_PASSPHRASE` in a file, make it a **separate root-owned file** (e.g.
  `/etc/tday-backup.env`, `chmod 600`) referenced by `--env-file`, not the repo's `.env`.

**Lose the key or the passphrase and the backup is gone.** There is no recovery path. Store it in
the same password manager as your other T'Day secrets, and store it somewhere that is not the
server being backed up.

### The encryption-key trap (read this)

T'Day encrypts some database fields at rest with `DATA_ENCRYPTION_KEY` /
`DATA_ENCRYPTION_KEYS` (see `.env.example` and `docs/security/operations-hardening.md`). Those
keys live in `.env.docker`, **not** in the database — so they are **not in the dump**.

A `pg_dump` restored onto a server with a different `DATA_ENCRYPTION_KEY` will come up, log in,
and then fail to read every encrypted field. Likewise a changed `AUTH_SECRET` invalidates existing
sessions and every persisted auth-throttle bucket.

> Back up `.env.docker` (or your secret store's copy of those values) at the same time as the
> database, and keep the key that was current when the dump was taken. A database backup without
> its keys is a partial backup.

---

## Scheduling

Pick one. Both run the same script; neither is enabled for you.

### Host cron (simplest)

```bash
# put non-secret settings somewhere cron can read them
sudo install -m 600 /dev/null /etc/tday-backup.env
sudo tee /etc/tday-backup.env >/dev/null <<'EOF'
TDAY_BACKUP_DIR=/srv/backups/tday
TDAY_BACKUP_RETENTION_DAYS=30
TDAY_BACKUP_ENCRYPTION=age
TDAY_BACKUP_AGE_RECIPIENT=age1qz...
EOF

crontab -e
```

```cron
# T'Day database backup, 03:15 every day. Logs to /var/log/tday-backup.log.
15 3 * * * /home/ohmz/StudioProjects/Tday/scripts/backup-database.sh --env-file /etc/tday-backup.env >> /var/log/tday-backup.log 2>&1
```

Use the absolute path to the script; it resolves the repo root from its own location, so the
working directory does not matter. The user running it must be in the `docker` group (or be root).

`cron` mails you the output on a non-zero exit if a local MTA is configured; if it is not, watch
the log — a backup that fails silently for three months is the classic way to discover you have no
backups on the day you need one. Check `tail -n 20 /var/log/tday-backup.log` after the first
scheduled run, and consider a cheap external heartbeat (healthchecks.io or similar) appended to
the cron line.

### systemd timer

`/etc/systemd/system/tday-backup.service`:

```ini
[Unit]
Description=T'Day database backup
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
User=ohmz
EnvironmentFile=/etc/tday-backup.env
ExecStart=/home/ohmz/StudioProjects/Tday/scripts/backup-database.sh
```

`/etc/systemd/system/tday-backup.timer`:

```ini
[Unit]
Description=Daily T'Day database backup

[Timer]
OnCalendar=*-*-* 03:15:00
Persistent=true
RandomizedDelaySec=15m

[Install]
WantedBy=timers.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now tday-backup.timer
systemctl list-timers tday-backup.timer
journalctl -u tday-backup.service -n 50
```

`Persistent=true` catches up on a missed run after the machine was off, which is exactly what you
want on a home server.

### Why there is no compose service for this

A container that backs up the database has to either hold the Docker socket (which is root on the
host — a much worse hole than the one it plugs) or duplicate the credentials and reach Postgres
over the network. Neither is worth it for a single-owner deployment when the host already has
cron. `docker-compose.yaml` is intentionally left untouched by this feature; nothing new starts
with `docker compose up`.

### Get it off the box

A dump sitting in `./backups` on the same disk as `postgres_data` survives a bad migration and a
`down -v`. It does not survive the disk, the filesystem, or the house. Point `TDAY_BACKUP_DIR` at
a different physical device, or add a second step that copies the (encrypted) dumps off-site —
`rsync`, `rclone`, `restic`, anything. Two copies on two devices, one of them off-site.

---

## Restoring

`scripts/restore-database.sh` **overwrites everything**. It drops the live database and rebuilds
it from the dump; anything created after that dump is gone.

```bash
# 1. always look first - this changes nothing
./scripts/restore-database.sh --inspect backups/tday-db-20260805T031500Z.dump.gz

# 2. the real thing
./scripts/restore-database.sh backups/tday-db-20260805T031500Z.dump.gz
```

The script will:

1. Decrypt (`.age` / `.enc`) into a temp directory, then verify the archive with `gzip -t` and
   `pg_restore --list`. A corrupt dump is rejected **before** anything is dropped.
2. Read the target database name from the container and print a block naming the container, the
   database and the dump, then demand you type the exact phrase — for the stock configuration:

   ```
   RESTORE mydb
   ```

   Anything else aborts. There is no bare `--yes`. If stdin is not a terminal it refuses outright
   unless `TDAY_RESTORE_CONFIRM` is set to that same phrase (that is the escape hatch for a
   scripted disaster-recovery run, and it is deliberately awkward).
3. Take a **safety dump of the current database first**, using the same backup script and your own
   encryption settings, with retention forced off. If that fails, the restore aborts. Skip with
   `--no-pre-backup` only if you know the current database is already worthless.
4. Stop `tday-backend` so it cannot write mid-restore (`--no-stop-backend` to override).
5. Terminate remaining connections, `DROP DATABASE` / `CREATE DATABASE`, then `pg_restore
   --single-transaction`. Because the restore is one transaction, a failure commits nothing — you
   are left with an empty database and a non-zero exit, not a half-restored one.
6. Start `tday-backend` again.

Afterwards: log in, open a list, and check `GET /health` returns `{"status":"ok"}`. Flyway will
find the restored `flyway_schema_history` and apply any migrations newer than the dump on the next
backend start.

If the dump is older than your current code, that is fine — Flyway migrates forward. The reverse
is not: a dump from a *newer* schema restored onto an *older* backend is not supported.

---

## Test your restore

**An untested backup is not a backup.** Until a dump has actually been restored, all you know is
that a file exists. Do this once now, and again after any change to Postgres version, compose
file, or encryption settings.

### Drill A — restore into a throwaway container (safe, do this one)

Touches nothing in your live stack. Uses a scratch Postgres container with its own volume.

```bash
DUMP=backups/tday-db-20260805T031500Z.dump.gz

# 1. a disposable Postgres of the SAME major version as docker-compose.yaml (postgres:15)
docker run -d --name tday_restore_drill \
  -e POSTGRES_USER=drill -e POSTGRES_PASSWORD=drill -e POSTGRES_DB=drill \
  postgres:15
sleep 5

# 2. restore into it (decrypt first if the file is .age/.enc)
gzip -dc "$DUMP" | docker exec -i tday_restore_drill \
  sh -c 'PGPASSWORD=drill pg_restore --no-owner --no-privileges --single-transaction -U drill -d drill'

# 3. prove there is real data in there
docker exec -i tday_restore_drill psql -U drill -d drill -c '\dt'
docker exec -i tday_restore_drill psql -U drill -d drill -c 'SELECT count(*) FROM "User";'
docker exec -i tday_restore_drill psql -U drill -d drill -c 'SELECT count(*) FROM todos;'
docker exec -i tday_restore_drill psql -U drill -d drill \
  -c 'SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;'

# 4. clean up - this deletes only the drill container and its volume
docker rm -f -v tday_restore_drill
```

Pass criteria: `pg_restore` exits 0, `\dt` lists the tables above, the row counts match roughly
what you expect, and the last `flyway_schema_history` row has `success = t`.

### Drill B — full end-to-end (once, deliberately)

Drill A proves the file is restorable. Only Drill B proves *you* can bring the service back.

1. Take a fresh backup and copy it somewhere off the machine.
2. Note a few facts to check afterwards: number of accounts, the title of your top task, whether a
   calendar feed URL works.
3. Run `./scripts/restore-database.sh <that dump>` against the live stack and confirm at the
   prompt. Time it.
4. Log in on web, Android and iOS. Confirm tasks, lists and completed items are all there.
5. Check the things that depend on secrets rather than data: an existing API key still works,
   the calendar feed URL still resolves, push notifications still arrive. If any of those broke,
   your `.env.docker` and the database are out of sync — that is the finding this drill exists to
   produce, and it is much better to learn it today.

Write down how long step 3 took. That number is your actual recovery time.

### Drill C — verify an encrypted dump is decryptable

Run this whenever you rotate the passphrase or the age key, on a machine that has the key:

```bash
# age
age --decrypt --identity ~/tday-backup-identity.txt backups/....dump.gz.age | gzip -t && echo OK

# openssl
openssl enc -d -aes-256-cbc -md sha512 -pbkdf2 -iter 600000 \
  -in backups/....dump.gz.enc -pass env:TDAY_BACKUP_PASSPHRASE | gzip -t && echo OK
```

`--inspect` does the same thing plus a `pg_restore --list`, and is the easier habit:

```bash
./scripts/restore-database.sh --inspect backups/....dump.gz.age
```

### Checksums

Each dump gets a `.sha256` sidecar. Verify after copying a dump anywhere:

```bash
cd backups && shasum -a 256 -c tday-db-20260805T031500Z.dump.gz.sha256
```

---

## Troubleshooting

**`compose service 'database' is not running`** — start it: `docker compose up -d database`. The
backup script never writes a file when it cannot reach Postgres.

**`dump failed gzip integrity check`** — the file is truncated or corrupt. Do not restore from it.
Check disk space on the backup device, then take a fresh dump. If the same thing happens twice,
the source disk is the suspect.

**Restore aborts on a single `pg_restore` error** — `--single-transaction` is deliberate: it means
a partial restore can never be committed. If you hit a genuinely benign error (a comment or
extension your target cluster cannot apply), restore manually without the all-or-nothing flag and
read the errors yourself:

```bash
gzip -dc backups/....dump.gz | docker compose exec -T database \
  sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_restore --no-owner --no-privileges -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

**`safety dump failed - refusing to overwrite the live database`** — the current database could not
be dumped, usually because it is already broken. That is often exactly when you want to restore, so
re-run with `--no-pre-backup` once you have accepted that the current state is unrecoverable.

**Backup runs fine by hand, does nothing under cron** — cron has no environment and no shell
profile. Use the absolute path to the script and pass `--env-file`, and check that the cron user is
in the `docker` group.

## Known limits

- The dump is taken from a running database. `pg_dump` gives a transactionally consistent
  snapshot, so the file is coherent — but it is a point in time, and everything written after it
  is not in it. Daily backups mean up to 24 hours of loss. There is no WAL archiving / PITR here;
  if you need minutes instead of hours, that is a separate piece of work.
- Restore drops and recreates the database, so anything living outside `$POSTGRES_DB` in that
  Postgres instance (other databases, cluster-level roles) is not touched and not backed up.
- `pg_dump --no-owner --no-privileges` means GRANTs are not preserved. For a single-owner
  deployment where the app connects as the owner, this is what you want; if you have added extra
  roles, recreate them yourself.
- Restoring a dump into a *different* Postgres major version usually works forwards
  (15 → 16), not backwards. Keep the major version in `docker-compose.yaml` in mind when you
  rebuild a host.

## See also

- `docs/security/operations-hardening.md` — the backup policy checklist this implements
- `docs/DEPLOYMENT.md` — deploy, update and rollback procedures
- `docs/security/SECURITY_POSTURE.md` — control inventory, including what this closes

# Backend setup

How to stand up the T'Day backend from nothing, what each security-relevant setting actually does,
and how to keep it running.

Companion documents:
[secrets-and-deploy.md](secrets-and-deploy.md) (generating and rotating secrets) ·
[backups.md](backups.md) (backup and restore) ·
[SECURITY_POSTURE.md](SECURITY_POSTURE.md) (what is and is not protected).

---

This is the operational half of the security story: what you actually have to configure, what the
defaults do, and which settings quietly change the posture. Everything below was verified against
the code and scripts in this repo.

### Prerequisites

- **Docker with Compose v2** (`docker compose`). The backup/restore scripts fall back to a
  standalone `docker-compose` binary if v2 is absent (`scripts/backup-database.sh:157-163`), but v2
  is the assumed path.
- **Nothing else.** There is no Node, JDK or Gradle requirement on the host — the image is built in
  Docker: `node:20-alpine` builds the SPA, `eclipse-temurin:21-jdk-alpine` builds the fat jar, and
  the runtime stage is `eclipse-temurin:21-jre-alpine` running as a non-root `tday` user
  (`Dockerfile.backend:1,19,36-42`). Postgres is pinned to `postgres:15` (`docker-compose.yaml:3`).
- **A domain only if you want a public URL.** The stack is usable on a LAN or over a VPN with no
  domain at all. If you do want `https://tday.example.com`, the supported model is a Cloudflare
  Tunnel (see [Exposing it](#exposing-it)), which needs a Cloudflare account and a domain whose
  authoritative DNS is Cloudflare (`docs/remote-access/cloudflare-tunnel.md:29-31`).
- **Disk for backups**, ideally on a different physical device than the Postgres volume. The
  default backup destination is `<repo>/backups` — the same disk as `postgres_data`
  (`scripts/backup-database.sh:105`).

### Two env files, and which is which

This trips people up, so be explicit. Compose reads **two different files** for two different
purposes:

| File | Read by | Holds |
|---|---|---|
| `.env` (repo root) | Docker Compose, for `${VAR}` interpolation | `TDAY_HOST_BIND`, `TDAY_HOST_PORT`, `TZ`, `POSTGRES_USER/PASSWORD/DB`, `OLLAMA_MODEL`, `VITE_SENTRY_*` |
| `.env.docker` | injected into the backend container via `env_file` (`docker-compose.yaml:78-79`) | every backend setting: `AUTH_SECRET`, `DATABASE_URL`, `TDAY_ENV`, encryption keys, rate limits |

Variables in `.env.docker` are **not** visible to Compose itself, and variables in `.env` are **not**
passed into the container unless compose explicitly forwards them (`docker-compose.yaml:80-87` does
this for `TZ`, `APPLE_TEAM_ID`, `IOS_BUNDLE_ID`, `OLLAMA_MODEL`). Putting `TDAY_HOST_BIND` in
`.env.docker` does nothing at all.

A third consumer complicates the picture: the backup scripts read `TDAY_BACKUP_*` defaults from
the **root `.env`** by default, not from `.env.docker` (`scripts/backup-database.sh:95`).

Both files, and any copy of them, are gitignored (`.gitignore:35-48`); only `.env.example` is
tracked.

### The minimum to boot

Exactly **two** variables hard-fail. `AppConfig.load()` calls `error(...)` for these and nothing
else:

```kotlin
databaseUrl = secret("DATABASE_URL", "DATABASE_URL_FILE") ?: error("DATABASE_URL is required")
authSecret  = secret("AUTH_SECRET",  "AUTH_SECRET_FILE")  ?: error("AUTH_SECRET is required")
```

`tday-backend/src/main/kotlin/com/ohmz/tday/config/AppConfig.kt:90-93`. Every other setting in that
file has a default (`env(key, default)`, `envInt(key, default)`) or is nullable. Notably **not**
required: `DATA_ENCRYPTION_KEY`, `TDAY_ENV`, `CORS_ALLOWED_ORIGINS`, the VAPID keys, `OLLAMA_URL`,
`APPLE_TEAM_ID`, every `AUTH_LIMIT_*` / `AUTH_LOCKOUT_*` knob. `CRONJOB_SECRET` still appears in
`.env.example:35` and in existing `.env.docker` files but **nothing in the backend reads it** — a
grep for `CRONJOB` across `tday-backend/src/` returns nothing, and the only other hits in the repo
are docs plus one web guardrail test. It can be deleted.

Minimal first setup:

```bash
cp .env.example .env.docker
openssl rand -base64 32            # paste into AUTH_SECRET in .env.docker
# DATABASE_URL can stay as shipped if you do not override the POSTGRES_* values
docker compose up -d --build
```

If you change `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` in the root `.env`
(`docker-compose.yaml:16-18`), you must change `DATABASE_URL` in `.env.docker` to match — they are
two separate places describing the same credentials, and the compose comment at
`docker-compose.yaml:13-15` says so. The shipped defaults are the well-known weak
`myuser`/`mypass`/`mydb`; that is tolerable only because the `database` service publishes no host
port at all (it has no `ports:` key), so Postgres is reachable only from inside the compose network.
It stops being tolerable the moment anything else on the host or on the docker network can reach
that container.

`AUTH_SECRET` should be at least 16 characters — see [Troubleshooting](#troubleshooting) for what
happens below that. `DATABASE_URL` accepts either a `postgresql://user:pass@host/db` URL or a raw
`jdbc:` URL (`config/DatabaseConfig.kt:19-38`).

Eight secrets can alternatively be read from a file via `<NAME>_FILE` (`AppConfig.kt:208-222`) for
Docker/Kubernetes secret mounts: `DATABASE_URL`, `AUTH_SECRET`, `AUTH_CREDENTIALS_PRIVATE_KEY`,
`DATA_ENCRYPTION_KEY`, `DATA_ENCRYPTION_KEYS`, `TDAY_PROBE_ENCRYPTION_KEY`, `VAPID_PUBLIC_KEY`,
`VAPID_PRIVATE_KEY`. Two `_FILE` names documented in `.env.example` have **no reader in the code**
— `CRONJOB_SECRET_FILE` (`.env.example:48`) and `DATA_ENCRYPTION_AAD_FILE` (`.env.example:178`);
`DATA_ENCRYPTION_AAD` is read by a plain `env()` call (`AppConfig.kt:107`). Following either
comment yields a silently unset value. Nothing in compose wires up Docker secrets, so by default
every secret reaches the backend as a process environment variable — readable by anyone who can
run `docker inspect tday_backend` or read `/proc/<pid>/environ` on the host.

### First run

```bash
docker compose up -d --build
docker compose logs -f tday-backend        # watch it come up
curl -sf http://127.0.0.1:2525/health      # {"status":"ok"}  (plugins/Routing.kt:28-30)
```

On boot the backend runs Flyway migrations itself against the mounted `/app/migrations`
(`DatabaseConfig.kt:56-69`), then creates any missing tables/columns via Exposed
(`DatabaseConfig.kt:91-96`). There is no separate migration step to run. `validateOnMigrate` is
deliberately off so a cosmetic edit to an already-applied migration cannot crash startup
(`DatabaseConfig.kt:63-67`) — the trade is that a *substantive* edit to an applied migration also
passes silently and simply never runs.

Then open the SPA and register. **The first account to register becomes `ADMIN` + `APPROVED`;
every account after it lands `PENDING`** (`services/UserService.kt:140,151`). A `PENDING` user gets
no session at all — the credentials callback refuses them with `code: "pending_approval"`
(`routes/auth/CredentialsCallbackRoutes.kt:119-124`) — so they can read and write nothing until an
admin approves via `PATCH /api/admin/users/{id}` (`routes/AdminRoutes.kt:26-33`).

Register your own account **immediately** after the first boot. Registration is open to anyone who
can reach the server; the only thing standing between a stranger and the admin account is being
first. Rate limits blunt automated abuse (6 registrations/hour and 3/10min per IP,
`AppConfig.kt:130-133`) but they do not close the window.

Caveat worth knowing before you invite anyone: there is no "suspend" for an approved account. The
admin panel offers approve (`AdminRoutes.kt:27`), reject-a-pending-registration (`:43-44`), delete
(`:35`), reset-password (`:53-54`) and clear-reset-request (`:67-68`). Cutting off an
already-approved user means deleting them, which purges their data.

### Optional settings that change security posture

Everything here is optional and shipped off or permissive. These are the ones worth a decision.

**`TDAY_ENV=production`** — the single highest-impact setting in the file. Default is
`development` (`AppConfig.kt:203-206`, falling back to `NODE_ENV` then `"development"`). Setting it
to exactly `production` (case-insensitive) turns on three things:
- `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload` (`plugins/SecurityHeaders.kt:110-112`)
- the `__Secure-` session cookie prefix and the `Secure` flag (`security/SessionCookies.kt:18-19,94`)
- `REQUIRE_ENCRYPTION_AT_REST` being honoured at all (`Application.kt:161`)

Any other value silently disables all three. Set it in `.env.docker`.

**`CORS_ALLOWED_ORIGINS`** — empty by default, which means same-origin only. Each entry must be a
full `http(s)://host[:port]` origin; anything unparseable is logged and skipped rather than
rejected (`plugins/Cors.kt:40-51`). Credentialed cross-origin requests are allowed
(`allowCredentials = true`, `plugins/Cors.kt:31`), so every origin you add here is an origin that
can drive the API with the user's cookie. Leave it empty unless you host the SPA elsewhere.

**`CSP_MODE`** — unset means `enforce` (`plugins/SecurityHeaders.kt:12-17`). Accepts
`enforce` | `report-only` | `off`; anything unrecognised falls back to `enforce`. The policy itself
is built in `buildCspHeader` (`SecurityHeaders.kt:57-84`) and is `script-src 'self'` with no inline
scripts, `frame-ancestors 'none'`, `object-src 'none'`. Two honest weaknesses, both forced by
dependencies and documented in the source: `style-src` keeps `'unsafe-inline'` because Radix/sonner
/vaul inject runtime `<style>` elements, and blocking next-themes' inline anti-FOUC script costs a
brief theme flash. There is no `report-uri`/`report-to` directive, so violations surface only in
the browser console — nothing is collected server-side, in either mode. `CSP_CONNECT_EXTRA`
**replaces** rather than appends — setting it drops the auto-derived Sentry ingest origin
(`SecurityHeaders.kt:89-91`).

**`DATA_ENCRYPTION_KEY` + `REQUIRE_ENCRYPTION_AT_REST`** — field encryption at rest is **opt-in and
off by default**, and running without it is a supported configuration, not a misconfiguration.
With no key set, production boots normally and logs one INFO line stating that task titles and
descriptions are plaintext in Postgres (`Application.kt:179-183`). Set
`REQUIRE_ENCRYPTION_AT_REST=true` and a missing key becomes a hard startup failure instead
(`Application.kt:163,176`) — that is the only configuration in which boot is refused, and the
decision table is covered by six tests in
`tday-backend/src/test/kotlin/com/ohmz/tday/StartupEncryptionPolicyTest.kt`, including one named
`boot is refused in exactly one configuration and no other`. The flag is ignored outside production
(`Application.kt:161`).

The reasoning, in the source comment at `Application.kt:139-155`: on a single-operator box, anyone
who can read the database already has the host, and the key would sit in the same `.env.docker`
next to the volume — while a fail-closed default turns a missing variable into a crash loop under
`restart: always` on a machine reachable only over SSH. The threat encryption actually addresses is
a **dump that leaves the host**, and [encrypting your backups](#backups) covers that better.

If you do set a key: it must decode to exactly 32 bytes — base64 or 64-char hex — or
`parseKeyMaterial` throws (`security/FieldEncryption.kt:131-148`). Encryption is not retroactive:
existing rows stay plaintext until next written, because `decryptIfEncrypted` passes anything
without the `enc:v1:` prefix straight through (`FieldEncryption.kt:99-103`). Malformed entries in
the optional `DATA_ENCRYPTION_KEYS` keyring (no `:` separator) are silently skipped rather than
rejected (`FieldEncryption.kt:113-114`), so a typo there can leave you booting with fewer keys than
you think.

**`RETENTION_DRY_RUN`** — defaults to `true` (`AppConfig.kt:168`), so the retention scheduler
**deletes nothing** out of the box. It runs every 6 hours (`services/RetentionScheduler.kt:162`)
and would age out `eventlog`/`auththrottle`/`authsignal`/`cronlog` at 90/30/180/90 days, preserving
any throttle row still serving a live lockout (`RetentionScheduler.kt:80-86`). In dry-run it does
not even count rows — it logs the cutoff and records `<table>=dry-run`
(`RetentionScheduler.kt:130-134`). Read one cycle's log output, then set `RETENTION_DRY_RUN=false`.
None of the `RETENTION_*` variables appear in `.env.example` or `docker-compose.yaml`, so nothing
prompts you to do this.

One trap: `RetentionScheduler.purge` treats `0` as "disable this table"
(`RetentionScheduler.kt:127`), but you cannot reach that value through the environment —
`envInt` discards any non-positive parse and returns the default (`AppConfig.kt:193-196`), so
`RETENTION_EVENTLOG_DAYS=0` silently means 90 days, not "off". Set a very large number instead.

**`AUTH_LIMIT_*` / `AUTH_LOCKOUT_*`** — all have working defaults (`AppConfig.kt:122-139`); leaving
them unset is fine. Defaults worth knowing: credentials 12/300s per IP, register 6/hour plus a
3/10min burst ceiling, lockout after 5 failures with exponential backoff from 30s to 1800s, and an
`(IP, account)` dimension at 50/900s (`AUTH_LIMIT_CREDENTIALS_ACCOUNT_*`, `AppConfig.kt:134-135`)
which is **not** listed in `.env.example`. All of these are keyed off the client IP resolved from
`cf-connecting-ip` → `x-forwarded-for` → `x-real-ip` → socket address, with **no trusted-proxy
allowlist** (`security/ClientSignals.kt:27-37`). That is safe only while the sole ingress is a
loopback-bound tunnel that rewrites the header itself. If you front this with any other proxy, or
widen the bind, the per-IP dimension becomes attacker-controlled with no code change and no warning.

### Exposing it

The compose port mapping is `"${TDAY_HOST_BIND:-127.0.0.1}:${TDAY_HOST_PORT:-2525}:8080"`
(`docker-compose.yaml:77`). With no root `.env` override, the backend listens on **host loopback
only** — nothing on the LAN or the internet can reach it directly, and Postgres and Ollama publish
no host ports at all.

That loopback bind is not a nicety; it is what makes the rest of the network posture defensible.
The supported public model is an **outbound-only Cloudflare Tunnel**: `cloudflared` runs on the host,
dials out to Cloudflare's edge, and proxies inbound requests to `127.0.0.1:2525`
(`docs/remote-access/cloudflare-tunnel.md:11-25`). There is no inbound port, no port-forward, no
public origin IP, and Cloudflare rewrites `CF-Connecting-IP` on ingress so a remote client cannot
forge their own rate-limit bucket. Setup steps are in that guide; harden the edge with the rules in
`docs/security/cloudflare-auth-hardening.md`.

Setting `TDAY_HOST_BIND=0.0.0.0` in the root `.env` publishes the backend on every host interface
over plain HTTP. That is a legitimate choice for a trusted LAN or a dev box, but be clear about
what it costs: the app installs no `HttpsRedirect` and no `ForwardedHeaders` plugin (a grep for
either across `tday-backend/src/main/kotlin/` returns nothing), so it will serve the SPA and accept
credentials over cleartext HTTP without complaint, and anything that can reach the port can set
`cf-connecting-ip` per request and get a fresh throttle bucket every time. Verify the live value on
the deploy host rather than assuming — `docker port tday_backend 8080` should print
`127.0.0.1:2525`.

The container itself is reasonably confined: `security_opt: no-new-privileges:true`,
`cap_drop: ALL`, `pids_limit: 512` (`docker-compose.yaml:75,96-99`). Memory limits are deliberately
absent, with the reason in the file (`docker-compose.yaml:71-74`): a too-low `mem_limit` under
`restart: always` produces an OOM crash loop, which is worse than the DoS it defends against. So
there is no memory bound on the backend today.

`docs/REMOTE_ACCESS.md` compares the alternatives (Tailscale, WireGuard, ZeroTier, SSH tunnel,
ngrok, frp) if a tunnel to a third party is not acceptable.

### Backups

**Nothing runs automatically.** No timer, no cron entry, no compose service — and the absence of a
compose service is deliberate, reasoned at `docs/security/backups.md:290-296` (a backup container
would need the Docker socket, which is root on the host). Backups begin only once you schedule them.
Two first-party scripts exist:

```bash
./scripts/backup-database.sh                    # dump to ./backups, unencrypted
./scripts/backup-database.sh --list             # what do I have?
./scripts/restore-database.sh --inspect FILE    # verify a dump, change nothing
```

`backup-database.sh` runs `pg_dump --format=custom --no-owner --no-privileges` **inside** the
`database` container, reading credentials from that container's own environment so nothing is
hardcoded and the password never reaches the host process list
(`scripts/backup-database.sh:242-249`). It verifies before keeping anything: non-zero size,
≥ `--min-bytes` (1024), a clean `gzip -t`, and a parseable `pg_restore --list` with at least one
restorable object (`:258-288`). A failed dump is never left behind looking usable — it is staged in
`TMPDIR` and published by an atomic same-filesystem rename (`:226-233,320-324`). Output is
`chmod 600` with a `.sha256` sidecar in a `chmod 700` directory that ignores itself in git
(`:191-199,319-332`).

Two defaults to know before you schedule it:

- **The script prunes its own old dumps at 30 days by default** (`TDAY_BACKUP_RETENTION_DAYS`,
  `:106`, applied at `:336-348`). The prune is anchored on the script's own `tday-db-` filename
  prefix so it cannot delete anything else in the directory. Pass `--retention-days 0` to disable.
- **It reads `TDAY_BACKUP_*` defaults from the repo's root `.env` unless told otherwise** (`:95`).
  `--env-file` overrides that path; the file is parsed line-by-line and never sourced, so it is
  never executed (`:79-93`).

**Encryption of the dump is opt-in and off by default** (`TDAY_BACKUP_ENCRYPTION=none`,
`scripts/backup-database.sh:107`, applied at `:294-311`). An unencrypted dump contains every
password hash, API key, calendar-feed token and webhook secret in restorable form — and if you are
running without field encryption, every task title and description in the clear. The script says so
on every unencrypted run (`:309`). Two modes:

- `--encrypt age` (preferred) — public-key, so the server cannot decrypt its own backups. Needs
  `TDAY_BACKUP_AGE_RECIPIENT` or `TDAY_BACKUP_AGE_RECIPIENTS_FILE` (`:174-182`).
- `--encrypt openssl` — AES-256-CBC, PBKDF2-SHA512, 600 000 iterations, passphrase from
  `TDAY_BACKUP_PASSPHRASE`, never printed and never on the command line (`:300-306`).

What encryption here does *not* cover: the plaintext dump is assembled in `TMPDIR` before it is
encrypted (`:227-230`). Nothing cleartext is ever written into the backup directory, but the
cleartext does exist on whatever filesystem `TMPDIR` points at for the duration of the run.

Schedule it yourself with cron or a systemd timer; put non-secret settings in a root-owned
`/etc/tday-backup.env` and pass `--env-file`:

```cron
15 3 * * * /path/to/Tday/scripts/backup-database.sh --env-file /etc/tday-backup.env >> /var/log/tday-backup.log 2>&1
```

The user running it must be in the `docker` group or be root (`docs/security/backups.md:239-240`).
Check the log after the first scheduled run. A backup job that has failed silently for three months
is the classic way to find out you have no backups on the day you need them.

**Getting a copy off the box is still unimplemented.** The default destination is `<repo>/backups`,
on the same disk as `postgres_data`. That survives a bad migration and a `down -v`; it does not
survive the disk or the building. Point `TDAY_BACKUP_DIR` at another physical device and add your
own `rsync`/`rclone`/`restic` step — nothing in the repo does this
(`docs/security/backups.md:298-306`).

**Test the restore. An untested backup is not a backup.** `docs/security/backups.md:352-424` gives
three drills; run at least Drill A now:

```bash
DUMP=backups/tday-db-20260805T031500Z.dump.gz
docker run -d --name tday_restore_drill \
  -e POSTGRES_USER=drill -e POSTGRES_PASSWORD=drill -e POSTGRES_DB=drill postgres:15
sleep 5
gzip -dc "$DUMP" | docker exec -i tday_restore_drill \
  sh -c 'PGPASSWORD=drill pg_restore --no-owner --no-privileges --single-transaction -U drill -d drill'
docker exec -i tday_restore_drill psql -U drill -d drill -c 'SELECT count(*) FROM "User";'
docker rm -f -v tday_restore_drill
```

Pass criteria: `pg_restore` exits 0, the tables are there, the row counts look right, and the last
`flyway_schema_history` row has `success = t`. Drill B (a real restore against the live stack)
proves the thing Drill A cannot: that *you* can bring the service back.

`restore-database.sh` is destructive by design — it drops and recreates the database. It takes a
safety dump of the current state first and refuses to proceed if that fails (`:222-246`), stops the
backend so it cannot write into a half-restored schema (`:252-260`), restores inside
`--single-transaction` so a failure commits nothing (`:287-301`), and demands you type
`RESTORE <dbname>` exactly (`:184`). There is no `--yes` flag; unattended recovery uses
`TDAY_RESTORE_CONFIRM` with that same phrase (`:206-216`), and a non-interactive run without it is
refused outright (`:215`). One sharp edge the script itself warns about: if you restore an
encrypted dump without `TDAY_BACKUP_ENCRYPTION` set, the safety dump it takes first lands
**unencrypted** next to the encrypted ones (`:226-233`).

**What the dump does not contain: your secrets.** `AUTH_SECRET`, `DATA_ENCRYPTION_KEY(S)`,
`AUTH_CREDENTIALS_PRIVATE_KEY` and the VAPID keys are not in it. If you use field encryption,
encrypted columns restore as ciphertext and stay unreadable without the key that was current when
the dump was taken. Back up `.env.docker` separately, in a password manager or secret store.

There is no WAL archiving and no point-in-time recovery: a dump is a point in time, so daily
backups mean up to 24 hours of loss (`docs/security/backups.md:462-467`).

### Upgrading and redeploying

```bash
./scripts/backup-database.sh                   # first, always
git pull
docker compose up -d --build tday-backend      # rebuild only the backend
docker compose logs -f tday-backend
curl -sf http://127.0.0.1:2525/health
```

Notes that matter:

- **Take a backup before any deploy that touches migrations or encryption settings.** Migrations run
  automatically at boot and there is no down-migration path.
- **Never `docker compose down -v`.** The `-v` deletes the `postgres_data` named volume
  (`docker-compose.yaml:104-105`), which is the only copy of everything unless you have scheduled
  backups.
- The backend has a real readiness healthcheck against `/health` (`docker-compose.yaml:90-95`), so
  `docker compose ps` distinguishes a booted-but-broken backend from a healthy one. Wait for
  `healthy`, not just `running`.
- Rebuilding the backend rebuilds the SPA too — they ship in the same image
  (`Dockerfile.backend:39-41`). `VITE_SENTRY_DSN` is a **build arg**, not a runtime variable: it is
  inlined by Vite during the build (`Dockerfile.backend:8-13`), so changing it in `.env` requires a
  rebuild, not a restart.
- If you use the AI profile, pull its images on upgrade too:
  `docker compose --profile ai pull ollama ollama-model-setup`.
- After deploying, re-verify the externally observable posture:
  ```bash
  curl -sI https://<host>/ | grep -iE 'content-security-policy|permissions-policy|strict-transport'
  docker port tday_backend 8080          # expect 127.0.0.1:2525
  ```

### Troubleshooting

**The container will not start / crash-loops.** Under `restart: always` a config error becomes a
loop, so read the log rather than the status: `docker compose logs --tail=100 tday-backend`. The
realistic causes, in order:
- `DATABASE_URL is required` / `AUTH_SECRET is required` — the only two fatal missing variables
  (`AppConfig.kt:90-93`). Usually means you put them in the root `.env` instead of `.env.docker`.
- `Invalid field encryption key. Expected 32-byte base64 or 64-char hex.` — a malformed
  `DATA_ENCRYPTION_KEY` is a **hard failure regardless of `REQUIRE_ENCRYPTION_AT_REST`, and in every
  environment**. The keyring is lazy, but `startupEncryptionVerdict(...)`'s arguments are evaluated
  before the `when` branches, so `fieldEncryption.isConfigured()` forces the parse on every boot
  (`Application.kt:173` → `FieldEncryption.kt:44,37,147`). An *absent* key is fine; a *bad* key is
  not.
- `REQUIRE_ENCRYPTION_AT_REST=true but no usable DATA_ENCRYPTION_KEY…` — you opted in and there is
  no key (`Application.kt:167-170,176`). Either set one or unset the flag.
- Database not ready — the backend `depends_on` the database's healthcheck
  (`docker-compose.yaml:100-102`), so this is usually Postgres itself failing; check
  `docker compose logs database`.

**HSTS and `Secure` cookies silently missing.** Symptom: everything works, but
`curl -sI https://<host>/` shows no `Strict-Transport-Security` and the session cookie is
`authjs.session-token` rather than `__Secure-authjs.session-token`. Cause: `TDAY_ENV` is not exactly
`production` — a typo, a trailing space, or the variable sitting in the wrong env file. There is no
warning for this; the comparison is a plain `equals("production", ignoreCase = true)`
(`AppConfig.kt:94`). It also silently disables `REQUIRE_ENCRYPTION_AT_REST`. Verify with
`docker compose exec tday-backend printenv TDAY_ENV`.

**Lockouts stop working after every restart.** Symptom: repeated failed logins never produce a
lasting lockout. Cause: `AUTH_SECRET` shorter than 16 characters. Its length is never validated —
the app prints one line to **stderr** (`auth_secret_missing using fallback hash key`) and then
generates a fresh random HMAC key on every boot (`security/ClientSignals.kt:16-25`), orphaning every
persisted throttle bucket on each restart. Sessions still work, so nothing looks broken. Use the
`openssl rand -base64 32` form.

**The mobile apps cannot reach the server, but the web SPA is fine.** `.env.example` ships
`TDAY_UPDATE_REQUIRED=true` (`:208`) with `TDAY_APP_VERSION=0.7.0` (`:200`) and a compatibility
mode of `exact` from `version.json`. In that combination the backend returns 426/409 for **every**
`/api/*` call except `/api/mobile/probe` from any client that identifies itself as
`X-Tday-Client: android-compose` or `ios` and reports a different `X-Tday-App-Version`
(`plugins/Security.kt:204-236`). The web SPA sends neither header and is unaffected, which is why
this looks like a mobile-only network fault. Set `TDAY_UPDATE_REQUIRED=false` unless your installed
apps are pinned to the same version as the server.

**The SPA white-screens or a feature stops talking to its backend after a deploy.** Suspect the CSP
first — check the browser console for violations (there is no server-side report endpoint, so the
console is the only place they appear). You do not need to rebuild the image to recover: set
`CSP_MODE=report-only` in `.env.docker` and `docker compose up -d tday-backend`. Violations are
then reported but not blocked, and you can read exactly which directive is at fault. `CSP_MODE=off`
removes the header entirely as a last resort. If the breakage is a blocked outbound call, add the
origin to `CSP_CONNECT_EXTRA` — but remember it **replaces** the auto-derived Sentry origin, so
include that too if you use Sentry.

**Rate limits collapse onto a single bucket behind a proxy.** If you front the backend with nginx,
Traefik, Caddy or a NAS reverse proxy, it **must** set one of `cf-connecting-ip`,
`x-forwarded-for` or `x-real-ip`. Without it every request appears to come from the proxy and all
per-IP limits share one bucket (`ClientSignals.kt:27-37`). The inverse risk is worse: because there
is no trusted-proxy allowlist, any client that can reach the port directly can set those headers
itself.

**Retention appears to do nothing.** It is doing nothing — `RETENTION_DRY_RUN` defaults to `true`.
Look for `[retention:dry-run] … would purge` in the log, then set `RETENTION_DRY_RUN=false`.
Setting a `RETENTION_*_DAYS` value to `0` to disable one table does not work; see above.

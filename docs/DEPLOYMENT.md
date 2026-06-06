# Deployment

How T'Day is built, deployed, and operated in production. Product direction and data boundaries are documented in [`PRODUCT_DIRECTION.md`](PRODUCT_DIRECTION.md) and [`DATA_MODEL.md`](DATA_MODEL.md).

## Environments

| Environment | Branch | URL | Deployment |
|-------------|--------|-----|------------|
| Production | `master` | `tday.ohmz.cloud` | Auto via GitHub Actions → Docker image to GHCR |
| Development | `develop` | Local only | `docker compose up` or local dev servers |

Mobile clients can also run in Local Mode without a deployed backend. Deployment work affects Server Mode, remote access, app compatibility checks, and server-backed sync.

## Docker

### Architecture

```
docker-compose.yaml
├── tday_backend    (Ktor + Vite SPA, port 2525 → 8080)
├── tday_db         (PostgreSQL 15, internal port 5432)
└── tday_ollama     (optional Ollama AI profile, internal port 11434)
```

### Build

The Docker image (`Dockerfile.backend`) is a multi-stage build:

1. **Stage 1 — Frontend** (`node:20-alpine`): `npm ci` + `npm run build` in `tday-web/` → static assets at `/web/dist`
2. **Stage 2 — Backend** (`eclipse-temurin:21-jdk-alpine`): Copies Docker-specific Gradle files from `docker/`, `shared/src`, and `tday-backend/src`, then runs `./gradlew :tday-backend:buildFatJar -x test`
3. **Stage 3 — Runtime** (`eclipse-temurin:21-jre-alpine`): Non-root user `tday`, copies fat JAR to `app.jar` and static files to `/app/static`, sets `STATIC_FILES_DIR=/app/static`

The production image is a **single JVM process** serving both the REST API and the static SPA.

Each frontend build also stamps a **unique build id** (`git-sha + UTC timestamp`) into the JS bundle as `__BUILD_ID__` and emits a matching `dist/version.json` (`{ "buildId", "version" }`), served at `/version.json`. This is the cache key that drives client cache invalidation — see [Web Cache Invalidation & Client Updates](#web-cache-invalidation--client-updates). For a readable SHA in the id, pass it as a build arg (the `.git` dir is not copied into the frontend stage):

```bash
docker compose build --build-arg GIT_SHA=$(git rev-parse --short HEAD) tday-backend
```

Without it the id falls back to a timestamp only — still unique per build, just less traceable.

```bash
# Local build
docker compose up -d --build

# Optional local AI summaries
# Set OLLAMA_URL=http://ollama:11434 in .env.docker, then run:
docker compose --profile ai pull ollama ollama-model-setup
docker compose --profile ai up -d --build
```

When the `ai` profile is enabled, Compose starts `tday_ollama` plus a one-shot model setup container. The setup container pulls `qwen3.5:0.8b` and attempts to remove the old `qwen2.5:0.5b` model. Pull the Ollama images during updates too; the qwen3.5 model requires a recent Ollama runtime. If the AI profile is not enabled, Summary still works through the backend logic fallback.

Ollama runs on CPU by default. For NVIDIA GPU acceleration, install the NVIDIA Container Toolkit on the host and add the GPU override file to every Compose command:

```bash
docker compose -f docker-compose.yaml -f docker-compose.gpu.yaml --profile ai up -d --build
```

### Docker Security

The `tday_backend` container runs with:
- `security_opt: no-new-privileges:true`
- `cap_drop: ALL`
- No privileged mode

### Network Security

By default the backend port is bound to **`127.0.0.1`** (localhost only). External clients cannot reach it over HTTP — an ingress method is required to bridge external traffic to `localhost:2525`.

```
Browser / Mobile App
  └─ HTTPS / VPN ─► [ ingress method ] ─► localhost:2525 ─► tday_backend :8080
```

The binding is controlled by two variables in the **project-root `.env`** file (not `.env.docker`):

| Variable | Default | Purpose |
|----------|---------|---------|
| `TDAY_HOST_BIND` | `127.0.0.1` | Interface to bind on the Docker host |
| `TDAY_HOST_PORT` | `2525` | Host port mapped to the container's `8080` |

To allow direct external access (development / trusted LAN only):

```bash
# .env (project root)
TDAY_HOST_BIND=0.0.0.0
```

When exposing the port externally, set `TDAY_ENV=production` in `.env.docker` so the backend enables secure cookies and HSTS headers.

For detailed instructions on all supported remote access methods — including Cloudflare Tunnel, Tailscale, WireGuard, ZeroTier, SSH tunnels, ngrok, and frp — see **[Remote Access](REMOTE_ACCESS.md)**.

### Self-hosting on a NAS (TrueNAS / Unraid / Synology / Proxmox)

These are the recurring "I deployed it but can't reach it / it behaves oddly" issues on NAS and
home-server platforms, with the symptom and the fix.

| Symptom                                                                                                           | Cause                                                                                                        | Fix                                                                                                                                                                                                                                            |
|-------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Container is "running" but the web UI is unreachable from any other device                                        | Default bind is `127.0.0.1` (localhost of the container/VM only)                                             | Either put a reverse proxy / VPN in front (recommended — gives HTTPS), or for a trusted LAN set `TDAY_HOST_BIND=0.0.0.0` in the **root `.env`** and recreate. On Unraid/TrueNAS custom-app UIs, map host port → container `8080` the same way. |
| Behind a reverse proxy, registration/login throttles trip far too early, or all users share one rate-limit bucket | Proxy isn't forwarding the real client IP, so every request looks like the proxy's IP                        | Configure the proxy to send `X-Forwarded-For` (or `X-Real-IP`; Cloudflare sends `cf-connecting-ip`). T'Day reads these in priority order — no extra "trust proxy" flag needed.                                                                 |
| Logged out constantly / "secure cookie" warnings when served over a domain                                        | App not in production mode, so cookies aren't marked Secure for an HTTPS origin, or the origin is cross-site | Set `TDAY_ENV=production` in `.env.docker` once you terminate HTTPS at the proxy. Add the external origin to `CORS_ALLOWED_ORIGINS` only if the web app is served from a *different* origin than the API.                                      |
| Server **log** timestamps are in UTC, not your local time                                                         | Container defaults to UTC                                                                                    | Optional: set `TZ` (IANA name) in the root `.env` — passed to the backend container. This affects **logs only**. Task due times, overdue, and reminders are always shown in each user's own device timezone and are unaffected by `TZ` (see [Server timezone](#server-timezone)). |
| Data lost after recreating the container                                                                          | App data lives only in the `postgres_data` named volume                                                      | Keep the named volume (don't `down -v`); back it up with scheduled `pg_dump`. On Unraid/TrueNAS, map the Postgres data path to persistent array storage, not a temp/ephemeral dir.                                                             |
| New version doesn't appear after pulling                                                                          | NAS UIs use their own update flow                                                                            | Unraid: container → **Force update**. Portainer: **Recreate** → **Re-pull image** (see [Updating in Production](#updating-in-production)). `docker compose pull && up -d` for plain Compose.                                                   |

For HTTPS without exposing a port at all (and to satisfy the secure-context requirements of PWAs and
WebCrypto), prefer a tunnel/VPN from [Remote Access](REMOTE_ACCESS.md) over binding `0.0.0.0`
directly.

### Health Checks

| Service | Check | Interval |
|---------|-------|----------|
| PostgreSQL | `pg_isready` | 1s (10 retries) |
| Ollama, optional `ai` profile | `ollama list` | 20s (5 retries) |
| T'Day backend | Depends on PostgreSQL; uses Ollama opportunistically when configured | — |

## CI/CD Pipeline

### Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `pr-gate.yml` | PR to `master` | Validates source branch (`develop` only), runs web lint + test, backend test |
| `release.yml` | Push to `master` | Runs lint + tests, then builds Docker image, pushes to GHCR, creates release |

### Test-Before-Build Policy

**No Docker image is built or published unless all tests pass.** This is enforced in both CI workflows:

- **PR Gate** (`pr-gate.yml`): On every PR to `master`, the pipeline validates the source branch is `develop`, then runs `npm run lint` and `npm run test` in `tday-web/`, followed by `./gradlew :tday-backend:test`. PRs cannot merge if either step fails.
- **Release** (`release.yml`): On push to `master`, a `lint-and-test` job runs first. The `build-and-release` job (Docker build, push, tag, release) has `needs: lint-and-test` — it will not start unless lint and tests pass.

```
PR to master:
  check-source-branch → lint-and-test → (merge allowed)

Push to master:
  lint-and-test → build-and-release (Docker build + push + tag + GitHub release)
```

This ensures:
- Broken code never produces a Docker image.
- Security guardrails, coding standards, and architecture tests gate every release.
- Test failures block the pipeline before any artifact is published.

### Release Process

1. Features are developed on `feature/*` branches.
2. PRs merge into `develop` after review.
3. When ready for release, PR `develop` → `master`.
4. `pr-gate.yml` validates source branch, then runs lint + tests.
5. On merge to `master`, `release.yml`:
   - Runs lint and the full test suite (including guardrails).
   - **Only if tests pass**: builds and pushes Docker image to `ghcr.io/ohmzi/tday:latest` and `:v<version>`.
   - Creates a Git tag.
   - Generates structured release metadata from the commit range since the previous tag.
   - Writes `tday-web/public/release/current-release.json` into the shipped web build and updates `tday-web/public/release/latest-changes.json` on `master`.
   - Creates a GitHub release.

### Version Bumping

The **single source of truth** for the app/server version is root `version.json`. All other systems derive from it:

- **CI/CD**: Reads `version.json` → Docker image tags (`:v1.6.0`, `:latest`), Git tags, GitHub releases.
- **Web**: `scripts/version.mjs sync` mirrors the version into `tday-web/package.json` and `package-lock.json`; Vite bundles that package value.
- **Backend**: `tday-backend/build.gradle.kts` parses `version.json`, embeds it as `tday-version.json`, and `AppConfig` uses it as the default `TDAY_APP_VERSION`/backend release fallback.
- **Android**: `app/build.gradle.kts` parses root `version.json` at build time → `versionName` and computed `versionCode`.
- **iOS**: `scripts/version.mjs sync` mirrors the version, build number, and update URL into `Info.plist`, Xcode project metadata, and `project.yml`.
- **Backend compatibility templates**: The same sync script mirrors version/update-required values into `.env.example` and `tday-backend/.env.example`. Live deployment env files such as `.env.docker` stay operator-owned and must be updated deliberately when the server should require a new app version.
- **Runtime**: Android sends `BuildConfig.VERSION_NAME` and iOS sends `CFBundleShortVersionString` in the `X-Tday-App-Version` HTTP header.

To bump the version before merging to `master`:

```bash
node scripts/version.mjs bump patch   # 1.6.0 -> 1.6.1
node scripts/version.mjs bump minor   # 1.6.0 -> 1.7.0
node scripts/version.mjs bump major   # 1.6.0 -> 2.0.0
node scripts/version.mjs check
```

The bump command updates `version.json`, increments `ios.buildNumber`, and syncs every checked-in mirror. If you manually edit `version.json`, run `node scripts/version.mjs sync` and then `node scripts/version.mjs check`.

**Never** set release version numbers directly in Gradle files, iOS project files, web package files, lockfiles, or example env templates.

### Version Reference

Every file that contains or controls a version number, grouped by platform.

#### Root Manifest (source of truth)

| File | What | Notes |
|------|------|-------|
| `version.json` | App semver, exact compatibility policy, iOS build number, iOS update URL | **Edit this file or use `scripts/version.mjs bump`**; all other app/server versions derive from it. |

#### Web App

| File | What | Notes |
|------|------|-------|
| `tday-web/package.json` / `package-lock.json` (`"version"`) | App semver mirror | Auto-synced from `version.json`. |
| `tday-web/vite.config.ts` (`__APP_VERSION__`) | Build-time define from `npm_package_version` | Injected into the SPA at build; fallback `"0.0.0"`. |
| `tday-web/src/main.tsx` | Sentry release (`tday-web@<version>`) | Derived at build time. `VITE_SENTRY_TRACES_SAMPLE_RATE` controls trace sampling. |

#### Android

| File | What | Notes |
|------|------|-------|
| `android-compose/app/build.gradle.kts` | `versionName` / `versionCode` | Parsed from root `version.json` at build time. `versionCode` = `major*10000 + minor*100 + patch`. |
| `android-compose/.../TdayApplication.kt` | Sentry release (`tday-android@<version>`) | Uses `BuildConfig.VERSION_NAME`. `SENTRY_TRACES_SAMPLE_RATE` or `local.properties:sentryTracesSampleRate` controls trace sampling. |
| `android-compose/.../NetworkModule.kt` | `X-Tday-App-Version` HTTP header | Uses `BuildConfig.VERSION_NAME`. |

#### iOS

| File | What | Notes |
|------|------|-------|
| `ios-swiftUI/Tday/Info.plist` (`CFBundleShortVersionString`) | Marketing version (e.g. `1.21.0`) | Auto-synced from `version.json`. |
| `ios-swiftUI/Tday/Info.plist` (`TdayUpdateURL`) | App Store/TestFlight update URL | Auto-synced from `version.json` `ios.updateUrl`; leave empty only for builds without direct iOS update action. |
| `ios-swiftUI/project.yml` / `TdayApp.xcodeproj/project.pbxproj` (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`) | Xcode project metadata | Auto-synced from `version.json`; keep both aligned when regenerating the project. |
| `ios-swiftUI/Tday/Info.plist` (`CFBundleVersion`) | Build number | Mirrors `ios.buildNumber`; the bump command increments it. |
| `ios-swiftUI/.../SentryConfiguration.swift` | Sentry release (`tday-ios@<version>`) | Uses `CFBundleShortVersionString`. `SENTRY_DSN` and `SENTRY_TRACES_SAMPLE_RATE` flow through `Info.plist` build settings. |

#### Backend

| File | What | Notes |
|------|------|-------|
| `tday-backend/build.gradle.kts` (`version`) | Gradle artifact version | Parsed from root `version.json` and embedded as `tday-version.json`. |
| `tday-backend/.../Application.kt` | Sentry release (`tday-backend@<version>`) | Reads `TDAY_BACKEND_VERSION`, then `TDAY_APP_VERSION`, then embedded manifest version. `SENTRY_TRACES_SAMPLE_RATE` controls trace sampling. |

For Sentry project setup, release artifact verification, alerting, smoke drills,
and failure triage, see [`SENTRY_RUNBOOK.md`](SENTRY_RUNBOOK.md). Do not store
Sentry account passwords in deployment files; use DSNs for SDK configuration and
least-privilege auth tokens only for release/source artifact upload.

#### Server Compatibility (`TDAY_APP_VERSION`)

The manifest and `TDAY_APP_VERSION` environment variable tell the backend which app version it is compatible with. When exact compatibility is enabled with `TDAY_UPDATE_REQUIRED=true`, mobile clients that connect with a different version are shown an "Update Required" or "Server Update Needed" screen.

Local Mode does not require this probe. Server Mode Android and iOS clients use `/api/mobile/probe` plus the `X-Tday-App-Version` header to decide whether the installed app and server can safely sync. The backend also rejects mismatched mobile API requests: older apps receive `426 Upgrade Required`; apps newer than the server receive `409 Conflict`.

| File | Purpose | Notes |
|------|---------|-------|
| `.env.docker` | **Live value** used by the running Docker container | This is the file that actually controls what the server reports. Update here and recreate the container to take effect. |
| `.env.example` | Template for new deployments (project root) | Auto-synced to the manifest version; copy the value into live env files when that version should be required. |
| `tday-backend/.env.example` | Template for local backend development | Auto-synced to the manifest version; copy the value into live env files when that version should be required. |
| `tday-backend/.../AppConfig.kt` (`probeAppVersion`) | Reads `TDAY_APP_VERSION` at startup | Env-driven with embedded `version.json` fallback. |

**Updating live `TDAY_APP_VERSION`:** After releasing a new app version, update the value in `.env.docker` to match the newly released version, then recreate the backend container:

```bash
# After editing .env.docker
docker compose up -d tday-backend
```

### Android Signing

Distributable Android release builds must use the same release keystore every time, or Android will reject updates with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

- CI supplies the release signing credentials through `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`.
- The release workflow also accepts the repository's legacy `TWA_KEYSTORE_BASE64`, `TWA_STORE_PASSWORD`, and `TWA_KEY_PASSWORD` secrets and auto-detects the alias when `RELEASE_KEY_ALIAS` is not configured.
- Local `assembleRelease` or `bundleRelease` builds now fail fast if those variables are missing, instead of silently producing a debug-signed release APK that cannot update an existing release install.
- For a local-only build that is not meant to update an existing release-signed install, you can opt in explicitly with `-PallowDebugSignedRelease=true`.
- The Android app can download a release APK in-app and hand it directly to the system installer. The first sideloaded update still requires enabling "Install unknown apps" for T'Day in Android settings.
- Historical note: GitHub Android APKs published before the stable signing fix on April 1, 2026 may have been signed with ephemeral debug certificates from CI runners. Devices on one of those installs must uninstall once and reinstall `v1.8.1` or newer before sideloaded updates will work again.

### iOS Signing and Associated Domains

- The iOS app uses `ios-swiftUI/TdayApp.xcodeproj`, automatic signing, and the `Tday` scheme.
- `/.well-known/apple-app-site-association` is served by the backend for webcredentials/deep-link support.
- `CFBundleShortVersionString`, `CFBundleVersion`, iOS `MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`, `TdayUpdateURL`, and example `TDAY_APP_VERSION` values are synced from root `version.json` by `scripts/version.mjs sync`.
- Set `ios.updateUrl` in `version.json` to the App Store or TestFlight URL before distributing an iOS build that should offer direct updates.

## Configuration

### Environment Variables

All configuration is via environment variables. See `.env.example` for the full list with documentation.

The Ktor backend (`AppConfig.kt`) loads all settings from environment variables and supports `_FILE` suffixes for secret file mounts (Docker/Kubernetes).

#### Required

| Variable | Purpose |
|----------|---------|
| `DATABASE_URL` | PostgreSQL connection string (JDBC or `postgresql://` format) |
| `AUTH_SECRET` | JWE encryption secret (generate: `openssl rand -base64 32`) |

#### Recommended

| Variable                            | Purpose                                                                                                                        |
|-------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `AUTH_PBKDF2_ITERATIONS`            | Password hash iterations (default: 310,000)                                                                                    |
| `AUTH_SESSION_MAX_AGE_SEC`          | Rolling web-session inactivity window in seconds (default: 2,592,000)                                                          |
| `AUTH_SESSION_ABSOLUTE_MAX_AGE_SEC` | Absolute session cap from original login time in seconds (default: 7,776,000)                                                  |
| `AUTH_SESSION_RENEW_THRESHOLD_SEC`  | Renewal threshold in seconds before expiry (default: 604,800)                                                                  |
| `AUTH_CREDENTIALS_PRIVATE_KEY`      | RSA key for credential envelope encryption; recommended in production to avoid ephemeral startup keys                          |
| `AUTH_CAPTCHA_SECRET`               | Cloudflare Turnstile secret; recommended in production so adaptive CAPTCHA does not fail closed when triggered                 |
| `APPLE_TEAM_ID`                     | Apple Developer Team ID used in Tday's canonical `apple-app-site-association` webcredentials payload for iOS Password AutoFill |
| `IOS_BUNDLE_ID`                     | iOS app bundle identifier for webcredentials association (default: `com.ohmz.tday.ios`)                                        |
| `ANDROID_PACKAGE_NAME`              | Android app package name for Digital Asset Links credential sharing (default: `com.ohmz.tday.compose`)                         |
| `ANDROID_SHA256_CERT_FINGERPRINTS`  | Comma-separated SHA-256 signing certificate fingerprints for Android Digital Asset Links credential sharing                    |
| `OLLAMA_URL`                        | Optional Ollama service URL. Leave blank for backend logic-only summaries; use `http://ollama:11434` with the Compose `ai` profile |
| `OLLAMA_MODEL`                      | AI model for summaries when Ollama is enabled (default: `qwen3.5:0.8b`)                                                        |

The native iOS app saves and retrieves Tday credentials under the canonical `tday.ohmz.cloud` Apple Passwords scope, regardless of the server URL a user connects to.
The native Android app can save and retrieve app-scoped password credentials immediately. Sharing
credentials with the canonical `tday.ohmz.cloud` web scope requires
`ANDROID_SHA256_CERT_FINGERPRINTS` so the backend can serve `/.well-known/assetlinks.json`.

### Server timezone

**You almost never need to set this, and the server's timezone does not affect anyone's task
times.** T'Day uses the industry-standard "store UTC, render per-user" model:

- Every task due time is stored in **UTC**. Each client converts the user's local input to a UTC
  instant when saving, and converts it back to **that user's own device timezone** when displaying.
- Each user's timezone is detected from their device and synced to the server automatically
  (`X-User-Timezone` header + `GET /api/timezone`, stored on the user record). It is used for
  per-user, server-side groupings like the "today / overdue" summary.
- All server-side time math runs in UTC, so it is **independent of the container clock**. Two users
  in different timezones see the same task at the correct local time for each of them, on web, iOS,
  and Android alike.
- Reminders are scheduled **on each device** and fire at the task's absolute due instant, rendered
  in that device's local time — also independent of the server timezone.

Because of this, the `TZ` environment variable only changes the **server's log timestamps**. Leave
it at the default `UTC` unless you specifically want logs in your local zone:

```bash
# root .env — affects log timestamps only, NOT task due times or reminders
TZ=America/New_York
```

`TZ` is read by Docker Compose and passed into the backend container
(`docker-compose.yaml`: `TZ: ${TZ:-UTC}`). The PostgreSQL service is unaffected — all timestamps
are stored and compared in UTC. If task times ever appear off for a single user, the cause is that
user's **device** timezone being wrong, not the server `TZ`.

#### Docker Compose (project-root `.env`)

These variables are read by Docker Compose for port binding. They belong in the **root `.env`** file, not `.env.docker`.

| Variable | Default | Purpose |
|----------|---------|---------|
| `TDAY_HOST_BIND` | `127.0.0.1` | Network interface for the host port binding (`127.0.0.1` = localhost only, `0.0.0.0` = all interfaces) |
| `TDAY_HOST_PORT` | `2525` | Host port mapped to the backend container's port `8080` |

#### Optional

| Variable | Purpose |
|----------|---------|
| `TDAY_ENV` | Runtime mode (`production` enables production-only behavior such as HSTS and secure session cookies; `NODE_ENV` is still accepted as a fallback) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed cross-origin web origins (same-origin requests work without it) |
| `DATA_ENCRYPTION_KEY` / `DATA_ENCRYPTION_KEY_ID` | Field-level encryption at rest |
| `API_RATE_LIMIT_MAX` / `API_RATE_LIMIT_WINDOW_SEC` | Global `/api/**` request budget |
| `INFRA_RATE_LIMIT_MAX` / `INFRA_RATE_LIMIT_WINDOW_SEC` | `/health` and `/api/mobile/probe` request budget |
| `SUMMARY_RATE_LIMIT_MAX` / `SUMMARY_RATE_LIMIT_WINDOW_SEC` | `POST /api/todo/summary` request budget |
| `CHANGE_PASSWORD_RATE_LIMIT_MAX` / `CHANGE_PASSWORD_RATE_LIMIT_WINDOW_SEC` | `POST /api/user/change-password` request budget |
| `WS_RATE_LIMIT_MAX` / `WS_RATE_LIMIT_WINDOW_SEC` | `/ws` connect-attempt budget |
| `AUTH_LIMIT_SESSION_GET_MAX` / `AUTH_LIMIT_SESSION_GET_WINDOW_SEC` | `GET /api/auth/session` budget |
| `AUTH_LIMIT_CREDENTIALS_KEY_MAX` / `AUTH_LIMIT_CREDENTIALS_KEY_WINDOW_SEC` | `GET /api/auth/credentials-key` budget |

### Secrets via Files

For Docker/Kubernetes secret mounts, append `_FILE` to any sensitive variable:

```bash
AUTH_SECRET_FILE=/run/secrets/auth_secret
DATABASE_URL_FILE=/run/secrets/database_url
AUTH_CAPTCHA_SECRET_FILE=/run/secrets/auth_captcha_secret
```

The Ktor backend's `AppConfig` reads file contents into the corresponding variable at startup when `_FILE` variants are present.

## Database Migrations

### How Migrations Work

T'Day uses **Flyway** for database migrations. Flyway runs automatically on Ktor backend startup (`DatabaseConfig.kt`), applying any pending migrations from `tday-backend/src/main/resources/db/migration/`.

Migration files follow the naming convention: `V<number>__<description>.sql`. The current baseline sequence is:

- `V1__baseline.sql`: legacy placeholder kept for compatibility.
- `V2__full_schema.sql`: full schema snapshot generated from the live PostgreSQL schema for clean installs.
- `V3__add_missing_indexes.sql`: first incremental migration after the schema snapshot.

Existing databases with pre-Flyway schema but no migration history are baselined at version `2`, which skips the placeholder and full-schema migrations and applies only new incremental migrations.

### Creating Migrations

1. Create the next SQL file in `tday-backend/src/main/resources/db/migration/` following the naming convention (`V4__...`, `V5__...`, and so on).
2. Write the DDL/DML statements, or generate/review SQL from a local database when a full snapshot is explicitly needed.
3. Update the corresponding Exposed `Table` objects in `tday-backend/src/main/kotlin/com/ohmz/tday/db/tables/` to match.
4. Restart the backend — Flyway applies the migration automatically.
5. Commit both the migration SQL and the Exposed table changes.

### Migration Safety

- Always review generated SQL before committing.
- Do not regenerate `V2__full_schema.sql` for routine schema changes. Add a new incremental migration instead.
- Backward-compatible changes are preferred (add columns with defaults, don't drop columns immediately).
- For destructive changes, use a multi-step migration:
  1. Add new column / table.
  2. Deploy code that writes to both old and new.
  3. Migrate data.
  4. Remove old column / table.

## Rollback Strategy

### Application Rollback

Pull and run the previous Docker image version:

```bash
docker pull ghcr.io/ohmzi/tday:v1.4.0
docker compose down
# Update docker-compose.yaml image tag or use:
docker run -d --name tday -p 2525:8080 --env-file .env.docker ghcr.io/ohmzi/tday:v1.4.0
```

### Database Rollback

Flyway does not support automatic down-migrations. For rollbacks:

1. Write a manual SQL script to reverse the migration.
2. Apply via `psql` or a migration tool.
3. Keep database backups before every release.

## Observability

### Logging

- Application logs go to stdout/stderr via **Logback** (Docker captures them).
- Security events are written to the `eventLog` database table.
- OkHttp (Android) logs at DEBUG level with cookie redaction.
- Log configuration is in `tday-backend/src/main/resources/logback.xml`.

### Monitoring Recommendations

- Monitor `auth_lockout`, `auth_limit_ip`, and `auth_limit_ip_burst` event codes for abuse (the
  burst code fires when an IP exceeds the short-window account-creation tier).
- Set alerts for container restarts.
- Monitor PostgreSQL connection pool (HikariCP) and disk usage.
- Check the Ollama health endpoint only when the `ai` profile is enabled. Without Ollama, Summary falls back to backend logic.

### Backups

- Database: Schedule automated PostgreSQL dumps with encryption at rest.
- Secrets: Store in a secrets manager with audit logging.

## Updating in Production

### Docker Compose

```bash
docker compose pull && docker compose up -d
```

### Portainer

1. Containers → select **tday_backend**.
2. Click **Recreate** → enable **Re-pull image** → click **Recreate**.

### Post-Update

- Flyway migrations run automatically on container start.
- Existing databases without Flyway history are baselined at version `2`; empty databases replay the full schema snapshot and then incremental migrations.
- Verify the app is healthy by checking `GET /health` returns `{ "status": "ok" }`.
- Review `docker logs tday_backend` for startup errors.
- **Web clients self-update.** Already-open browsers/PWAs detect the new build and reload into it automatically — no manual cache clearing on each release. See below.

## Web Cache Invalidation & Client Updates

### Why this exists

`tday-web` is a Vite PWA served same-origin by the backend from `/app/static`. Every build produces content-hashed chunks and a fresh `index.html`. Without coordination, an already-open client (or a Service Worker / browser / Cloudflare cache) keeps an **old `index.html`** whose chunk hashes no longer exist on the server. When the app then lazy-loads a route chunk it gets a 404 → the SPA fallback returns `index.html` (HTML) → the dynamic import fails and the app crashes (`Failed to fetch dynamically imported module`, or in WebKit `undefined is not an object (evaluating 'o._result.default')`).

This system makes deploys self-healing across every OS/browser. It has four cooperating layers, each guarded against reload loops.

### 1. Per-build cache key

- `tday-web/vite.config.ts` computes a unique `BUILD_ID` once per build (`GIT_SHA` env/arg → else `git rev-parse` → else `dev`, suffixed with a UTC timestamp). It is both:
  - injected into the bundle as the `__BUILD_ID__` define (declared in `src/vite-env.d.ts`), and
  - emitted as `dist/version.json` via an inline `generateBundle` plugin (single source of truth — the same const feeds both, so they can never disagree).
- The backend serves `dist/version.json` at `/version.json` automatically (it rides in the static dir; no backend route needed).

### 2. HTTP cache headers (`tday-backend/.../plugins/Routing.kt`)

The static handler sets `Cache-Control` by path (`cacheControlFor`), so browsers and Cloudflare cache correctly:

| Resource | `Cache-Control` | Rationale |
|----------|-----------------|-----------|
| `index.html`, SPA-fallback HTML, `/version.json` | `no-cache, no-store, must-revalidate` | Always revalidate so a new build is picked up immediately |
| `/assets/**` (content-hashed) | `public, max-age=31536000, immutable` | Filename changes every build, so cache forever safely |
| everything else (icons, manifest, locales) | `public, max-age=3600` | Modest TTL for non-hashed static files |

The HTML rule is keyed so the **SPA fallback** (e.g. a deep link like `/en/app/tday`) is also `no-store`, not just literal `index.html`.

### 3. Service worker (`tday-web/src/sw.ts`)

- **Navigations are NetworkFirst** (`cacheName: tday-navigation`, 4s timeout), falling back to the precached `/index.html` **only when offline**. So online clients always fetch the current `index.html` (and thus current chunk refs); offline still works.
- `/version.json` has an explicit `NetworkOnly` route, and is excluded from precache (the Workbox `globPatterns` deliberately omits `json` — **do not add it**).
- A `message` handler supports `{type:"SKIP_WAITING"}` and `{type:"CLEAR_CACHES"}` for client-driven activation / hard reset.

### 4. Client version poller (`useVersionGate` → `components/app/VersionGate.tsx`, mounted in `App.tsx`)

Fetches `/version.json` (`cache:"no-store"`) on mount, on tab refocus (`visibilitychange`), and every 15 minutes. On a `buildId` mismatch it applies a **hybrid UX**:

- **Silent reload** when the tab had been backgrounded/idle and the user is *not* typing (no editable element focused) — zero interruption.
- Otherwise a non-blocking **"New version available — Reload"** toast (Sonner).

Reload-loop protection lives in `src/lib/chunkError.ts` (`versionReloadAlreadyTried` / `markVersionReloadTried` / `clearVersionReloadFlag`), a separate one-shot guard from the reactive stale-chunk guard. Both clear after 8s of healthy runtime (`main.tsx`), so a *second* deploy within the same session can also self-heal.

### Layer composition (defense in depth)

1. **NetworkFirst nav** (structural) — any reload/navigation while online lands on fresh `index.html`.
2. **`useVersionGate`** (proactive) — detects a deploy before a stale chunk is even requested.
3. **`chunkError.ts` + `vite:preloadError`** (reactive net) — recovers any stale dynamic import that still slips through; thanks to NetworkFirst the recovery reload reliably lands on the current build. See also the Safari/WebKit notes in [Developer notes](#developer-notes--gotchas).

### Verifying after a deploy

```bash
# build key is served and never cached
curl -sI http://127.0.0.1:2525/version.json | grep -i cache-control   # no-store
curl -s  http://127.0.0.1:2525/version.json                            # {"buildId":...}
# HTML shell never cached, hashed assets immutable
curl -sI http://127.0.0.1:2525/            | grep -i cache-control      # no-store
curl -sI http://127.0.0.1:2525/assets/<hashed>.js | grep -i cache-control  # immutable
```

End-to-end (real Safari engine) with Playwright WebKit: load the app fresh and confirm no spurious reload; intercept `/version.json` to return a different `buildId` and confirm the prompt/auto-reload fires. (`webkit` browser is bundled; run `sudo npx playwright install-deps` once for the system libs.)

### Recovering a device stuck on a pre-this-code build

This mechanism only takes effect once a device has loaded **one** build that contains it. A device still pinned to an older cached build needs a one-time manual clear:

- If installed to the home screen (PWA), delete that icon first — it caches separately from the browser.
- iOS Safari: **Settings → Safari → Clear History and Website Data**. Desktop: DevTools → Application → Service Workers → *Unregister*, then *Clear site data*, then hard reload.

### Developer notes / gotchas

- **Don't churn caches needlessly.** Each build re-hashes every chunk (and the Sentry plugin injects a fresh debug id), so rapid back-to-back rebuilds while someone is testing can leave a device's precache half-updated. Batch changes into one build.
- **Keep `version.json` uncacheable.** Don't add `json` to the SW `globPatterns`, and keep the `NetworkOnly` route + the `no-store` header.
- **New static assets:** content-hashed output goes under `/assets/**` (immutable) automatically; anything you drop in `tday-web/public/` is non-hashed and gets the 1h default — bump its handling in `cacheControlFor` if it needs different behavior.
- **Tuning:** poll cadence is `CHECK_INTERVAL_MS` in `useVersionGate.ts`; the toast UX is in `VersionGate.tsx`.
- **Safari/WebKit:** Safari has never shipped `requestIdleCallback` and only added `structuredClone` in 15.4 — guard browser globals with `typeof x === "function"` fallbacks (see `usePrefetchRoutes.ts`, `mergeInstanceAndTodo.ts`). A WebKit-only crash surfaces as the `RouteErrorPage`/`ErrorBoundary` screen; both expose an "Error details" expander, and both route stale-chunk errors through the auto-reload.

### Key files

| Concern | File |
|---------|------|
| Build id + `version.json` emit | `tday-web/vite.config.ts`, `src/vite-env.d.ts` |
| Cache-Control headers | `tday-backend/src/main/kotlin/com/ohmz/tday/plugins/Routing.kt` (`cacheControlFor`) |
| SW strategy + messages | `tday-web/src/sw.ts` |
| Version poller + UX | `tday-web/src/hooks/useVersionGate.ts`, `src/components/app/VersionGate.tsx`, `src/App.tsx` |
| Reload guards + reactive net | `tday-web/src/lib/chunkError.ts`, `src/main.tsx` |
| Build-arg for SHA | `Dockerfile.backend` |

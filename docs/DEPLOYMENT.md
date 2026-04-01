# Deployment

How T'Day is built, deployed, and operated in production.

## Environments

| Environment | Branch | URL | Deployment |
|-------------|--------|-----|------------|
| Production | `master` | `tday.ohmz.cloud` | Auto via GitHub Actions → Docker image to GHCR |
| Development | `develop` | Local only | `docker compose up` or local dev servers |

## Docker

### Architecture

```
docker-compose.yaml
├── tday_backend    (Ktor + Vite SPA, port 2525 → 8080)
├── tday_db         (PostgreSQL 15, internal port 5432)
└── tday_ollama     (Ollama AI, internal port 11434)
```

### Build

The Docker image (`Dockerfile.backend`) is a multi-stage build:

1. **Stage 1 — Frontend** (`node:20-alpine`): `npm ci` + `npm run build` in `tday-web/` → static assets at `/web/dist`
2. **Stage 2 — Backend** (`eclipse-temurin:21-jdk-alpine`): Copies Docker-specific Gradle files from `docker/`, `shared/src`, and `tday-backend/src`, then runs `./gradlew :tday-backend:buildFatJar -x test`
3. **Stage 3 — Runtime** (`eclipse-temurin:21-jre-alpine`): Non-root user `tday`, copies fat JAR to `app.jar` and static files to `/app/static`, sets `STATIC_FILES_DIR=/app/static`

The production image is a **single JVM process** serving both the REST API and the static SPA.

```bash
# Local build
docker compose up -d --build

# Pull AI model after first start
docker exec -it tday_ollama ollama pull qwen2.5:0.5b
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

### Health Checks

| Service | Check | Interval |
|---------|-------|----------|
| PostgreSQL | `pg_isready` | 1s (10 retries) |
| Ollama | `ollama list` | 20s (5 retries) |
| T'Day backend | Depends on both above being healthy | — |

## CI/CD Pipeline

### Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `pr-gate.yml` | PR to `master` | Validates source branch (`develop` only), runs web lint + test, backend test |
| `release.yml` | Push to `master` | Runs lint + tests, then builds Docker image, pushes to GHCR, creates release |
| `cronjob.yml` | Schedule + manual | Calls production cron endpoint for task maintenance |

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
   - Generates release notes from merged PRs.
   - Creates a GitHub release.

### Version Bumping

The **single source of truth** for the app version is `tday-web/package.json`. All other systems derive from it:

- **CI/CD**: Reads `tday-web/package.json` → Docker image tags (`:v1.6.0`, `:latest`), Git tags, GitHub releases.
- **Android**: `app/build.gradle.kts` parses `tday-web/package.json` at build time → `versionName` and computed `versionCode`.
- **iOS**: A `postversion` npm hook runs `scripts/sync-ios-version.sh`, which writes the version into `ios-swiftUI/Tday/Info.plist` and stages the change automatically.
- **Runtime**: Android sends `BuildConfig.VERSION_NAME` and iOS sends `CFBundleShortVersionString` in the `X-Tday-App-Version` HTTP header.

To bump the version before merging to `master`:

```bash
cd tday-web
npm version patch   # 1.6.0 → 1.6.1
npm version minor   # 1.6.0 → 1.7.0
npm version major   # 1.6.0 → 2.0.0
```

The `postversion` hook syncs the iOS `Info.plist` and stages it, so the version-bump commit includes the plist change.

**Never** set version numbers directly in `build.gradle.kts` or any other file. Edit only `tday-web/package.json`.

### Android Signing

Distributable Android release builds must use the same release keystore every time, or Android will reject updates with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

- CI supplies the release signing credentials through `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`.
- The release workflow also accepts the repository's legacy `TWA_KEYSTORE_BASE64`, `TWA_STORE_PASSWORD`, and `TWA_KEY_PASSWORD` secrets and auto-detects the alias when `RELEASE_KEY_ALIAS` is not configured.
- Local `assembleRelease` or `bundleRelease` builds now fail fast if those variables are missing, instead of silently producing a debug-signed release APK that cannot update an existing release install.
- For a local-only build that is not meant to update an existing release-signed install, you can opt in explicitly with `-PallowDebugSignedRelease=true`.
- The Android app can download a release APK in-app and hand it directly to the system installer. The first sideloaded update still requires enabling "Install unknown apps" for T'Day in Android settings.
- Historical note: GitHub Android APKs published before the stable signing fix on April 1, 2026 may have been signed with ephemeral debug certificates from CI runners. Devices on one of those installs must uninstall once and reinstall `v1.8.1` or newer before sideloaded updates will work again.

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

| Variable | Purpose |
|----------|---------|
| `CRONJOB_SECRET` | Auth header for scheduled cron endpoint |
| `AUTH_PBKDF2_ITERATIONS` | Password hash iterations (default: 310,000) |
| `AUTH_SESSION_MAX_AGE_SEC` | Rolling web-session inactivity window in seconds (default: 2,592,000) |
| `AUTH_SESSION_ABSOLUTE_MAX_AGE_SEC` | Absolute session cap from original login time in seconds (default: 7,776,000) |
| `AUTH_SESSION_RENEW_THRESHOLD_SEC` | Renewal threshold in seconds before expiry (default: 604,800) |
| `OLLAMA_URL` | Ollama service URL (default: `http://ollama:11434`) |
| `OLLAMA_MODEL` | AI model for summaries (default: `qwen2.5:0.5b`) |

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
| `AUTH_CREDENTIALS_PRIVATE_KEY` | RSA key for credential envelope encryption |
| `AUTH_CAPTCHA_SECRET` | Cloudflare Turnstile secret for adaptive CAPTCHA |
| `DATA_ENCRYPTION_KEY` / `DATA_ENCRYPTION_KEY_ID` | Field-level encryption at rest |
| `AWS_*` | S3 storage for files |

### Secrets via Files

For Docker/Kubernetes secret mounts, append `_FILE` to any sensitive variable:

```bash
AUTH_SECRET_FILE=/run/secrets/auth_secret
DATABASE_URL_FILE=/run/secrets/database_url
CRONJOB_SECRET_FILE=/run/secrets/cronjob_secret
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

- Monitor `auth_lockout` and `auth_limit_ip` event codes for abuse.
- Set alerts for container restarts.
- Monitor PostgreSQL connection pool (HikariCP) and disk usage.
- Check Ollama health endpoint for AI availability.

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

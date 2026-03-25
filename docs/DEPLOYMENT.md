# Deployment

How T'Day is built, deployed, and operated in production.

## Environments

| Environment | Branch | URL | Deployment |
|-------------|--------|-----|------------|
| Production | `master` | `tday.ohmz.cloud` | Auto via GitHub Actions → Docker image to GHCR |
| Development | `develop` | Local only | `docker compose up` or `npm run dev` |

## Docker

### Architecture

```
docker-compose.yaml
├── tday          (Next.js app, port 2525 → 3000)
├── tday_db       (PostgreSQL 15, internal port 5432)
└── tday_ollama   (Ollama AI, internal port 11434)
```

### Build

The Docker image is a multi-stage Node 20 Alpine build:

1. `npm install` (all dependencies)
2. `prisma generate` (generate Prisma client)
3. `next build` (compile Next.js)
4. Prune dev dependencies
5. Copy build output to final image

```bash
# Local build
docker compose up -d --build

# Pull AI model after first start
docker exec -it tday_ollama ollama pull qwen2.5:0.5b
```

### Docker Security

The `tday` container runs with:
- `security_opt: no-new-privileges:true`
- `cap_drop: ALL`
- No privileged mode

### Health Checks

| Service | Check | Interval |
|---------|-------|----------|
| PostgreSQL | `pg_isready` | 1s (10 retries) |
| Ollama | `ollama list` | 20s (5 retries) |
| T'Day app | Depends on both above being healthy | — |

## CI/CD Pipeline

### Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `pr-gate.yml` | PR to `master` | Validates source branch, runs lint + full test suite |
| `release.yml` | Push to `master` | Runs lint + tests, then builds Docker image, pushes to GHCR, creates release |
| `cronjob.yml` | Schedule + manual | Calls production cron endpoint for task maintenance |

### Test-Before-Build Policy

**No Docker image is built or published unless all tests pass.** This is enforced in both CI workflows:

- **PR Gate** (`pr-gate.yml`): On every PR to `master`, the pipeline validates the source branch is `develop`, then runs `npm run lint` and `npm run test`. PRs cannot merge if either step fails.
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

The **single source of truth** for the app version is `package.json` in the project root. All other systems derive from it:

- **CI/CD**: Reads `package.json` → Docker image tags (`:v1.5.0`, `:latest`), Git tags, GitHub releases.
- **Android**: `app/build.gradle.kts` parses `package.json` at build time → `versionName` and computed `versionCode`.
- **Runtime**: Android sends `BuildConfig.VERSION_NAME` in the `X-Tday-App-Version` HTTP header.

To bump the version before merging to `master`:

```bash
npm version patch   # 1.5.0 → 1.5.1
npm version minor   # 1.5.0 → 1.6.0
npm version major   # 1.5.0 → 2.0.0
```

**Never** set version numbers directly in `build.gradle.kts` or any other file. Edit only `package.json`.

## Configuration

### Environment Variables

All configuration is via environment variables. See `.env.example` for the full list with documentation.

#### Required

| Variable | Purpose |
|----------|---------|
| `DATABASE_URL` | PostgreSQL connection string |
| `AUTH_SECRET` | JWT signing secret (generate: `openssl rand -base64 32`) |

#### Recommended

| Variable | Purpose |
|----------|---------|
| `CRONJOB_SECRET` | Auth header for scheduled cron endpoint |
| `AUTH_PBKDF2_ITERATIONS` | Password hash iterations (default: 310,000) |
| `AUTH_SESSION_MAX_AGE_SEC` | Session lifetime in seconds (default: 86,400) |
| `OLLAMA_URL` | Ollama service URL (default: `http://ollama:11434`) |
| `OLLAMA_MODEL` | AI model for summaries (default: `qwen2.5:0.5b`) |

#### Optional

| Variable | Purpose |
|----------|---------|
| `AUTH_TRUST_HOST` | Trust proxy host headers (for Cloudflare, reverse proxies) |
| `AUTH_ENFORCE_HTTPS_REDIRECT` | Force HTTPS in production |
| `AUTH_CREDENTIALS_PRIVATE_KEY` | RSA key for credential envelope encryption |
| `AUTH_CAPTCHA_SECRET` | Cloudflare Turnstile secret for adaptive CAPTCHA |
| `DATA_ENCRYPTION_KEY` / `DATA_ENCRYPTION_KEY_ID` | Field-level encryption at rest |
| `AWS_*` | S3 storage for notes/files |

### Secrets via Files

For Docker/Kubernetes secret mounts, append `_FILE` to any sensitive variable:

```bash
AUTH_SECRET_FILE=/run/secrets/auth_secret
DATABASE_URL_FILE=/run/secrets/database_url
CRONJOB_SECRET_FILE=/run/secrets/cronjob_secret
```

The Docker entrypoint (`scripts/docker-entrypoint.sh`) reads file contents into the corresponding variable at container start.

## Database Migrations

### Applying Migrations

Migrations run automatically during Docker container startup (`prisma migrate deploy` in entrypoint).

For local development:

```bash
npx prisma migrate deploy     # apply pending migrations
npx prisma migrate dev        # create a new migration during development
npx prisma studio             # visual database browser
```

### Creating Migrations

1. Modify `prisma/schema.prisma`.
2. Run `npx prisma migrate dev --name <descriptive-name>`.
3. Review the generated SQL in `prisma/migrations/`.
4. Commit both the schema change and the migration.

### Migration Safety

- Always review generated SQL before committing.
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
docker run -d --name tday -p 2525:3000 --env-file .env.docker ghcr.io/ohmzi/tday:v1.4.0
```

### Database Rollback

Prisma does not support automatic down migrations. For rollbacks:

1. Write a manual SQL script to reverse the migration.
2. Apply via `psql` or a migration tool.
3. Keep database backups before every release.

## Observability

### Logging

- Application logs go to stdout/stderr (Docker captures them).
- Security events are written to the `eventLog` database table.
- OkHttp (Android) logs at DEBUG level with cookie redaction.
- Prisma logs queries in non-production environments.

### Monitoring Recommendations

- Monitor `auth_lockout` and `auth_limit_ip` event codes for abuse.
- Set alerts for container restarts.
- Monitor PostgreSQL connection pool and disk usage.
- Check Ollama health endpoint for AI availability.

### Backups

- Database: Schedule automated PostgreSQL dumps with encryption at rest.
- Secrets: Store in a secrets manager with audit logging.
- See [`docs/security/operations-hardening.md`](security/operations-hardening.md) for the full operations checklist.

## Updating in Production

### Docker Compose

```bash
docker compose pull && docker compose up -d
```

### Portainer

1. Containers → select **tday**.
2. Click **Recreate** → enable **Re-pull image** → click **Recreate**.

### Post-Update

- Database migrations run automatically on container start.
- Verify the app is healthy by checking `GET /api/mobile/probe` returns `{ "probe": "ok" }`.
- Review `docker logs tday` for startup errors.

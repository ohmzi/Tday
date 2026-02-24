# T'Day

Personal task planner.

- Tasks with priorities, pinning, and drag-and-drop reordering
- Calendar with month, week, and day views
- Notes with a rich text editor
- Tags and projects for organization
- Completion history
- 11 languages

Built with Next.js, Postgres, Prisma, and Ollama. Runs in Docker.

```bash
docker compose up -d --build
docker exec -it tday_ollama ollama pull qwen2.5:0.5b
```

Docker services started by compose:
- `tday` (Next.js app)
- `tday_db` (Postgres)
- `tday_ollama` (local AI runtime for summaries)

GPU acceleration:
- `tday_ollama` is configured with `gpus: all` by default.
- Ensure NVIDIA Container Toolkit is installed on the Docker host.

## Auth Hardening

- Mobile/server probe endpoint: `GET /api/mobile/probe`
- Backend auth throttling + lockout is configurable via `.env.docker` auth limit variables
- Password hashing uses PBKDF2 with configurable iterations (`AUTH_PBKDF2_ITERATIONS`)
- Session revocation is server-enforced using token versioning (sign-out and password change revoke active sessions)
- Session lifetime is configurable via `AUTH_SESSION_MAX_AGE_SEC` (default 24h, persistent across app/browser restarts)
- Adaptive CAPTCHA can be required after repeated failures (`AUTH_CAPTCHA_*`)
- API responses are served with `Cache-Control: no-store` and hardened security headers
- Production middleware enforces HTTPS (except local development hosts)
- Optional server-side field encryption at rest for sensitive text (`DATA_ENCRYPTION_*`)
- Cloudflare edge rule recommendations are documented in:
  `/home/ohmz/StudioProjects/Tday/docs/security/cloudflare-auth-hardening.md`

## Secrets And Rotation

- Production secrets should come from a secrets manager or mounted secret files.
- Docker entrypoint supports `*_FILE` for:
  - `AUTH_SECRET`
  - `CRONJOB_SECRET`
  - `DATABASE_URL`
  - `AUTH_CAPTCHA_SECRET`
  - `DATA_ENCRYPTION_KEY`
  - `DATA_ENCRYPTION_KEYS`
  - `DATA_ENCRYPTION_AAD`
- Rotate secrets on a schedule and keep previous encryption keys in `DATA_ENCRYPTION_KEYS` during rollover windows.

## Native Android (Compose)

A new native Android client now lives in:

`/home/ohmz/StudioProjects/Tday/android-compose`

Open that folder in Android Studio to run the Kotlin + Jetpack Compose app.

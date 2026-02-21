# T'Day

Personal task planner.

- Tasks with priorities, pinning, and drag-and-drop reordering
- Calendar with month, week, and day views
- Notes with a rich text editor
- Tags and projects for organization
- Completion history
- 11 languages

Built with Next.js, Postgres, and Prisma. Runs in Docker.

```bash
docker compose up --build
```

## Auth Hardening

- Mobile/server probe endpoint: `GET /api/mobile/probe`
- Backend auth throttling + lockout is configurable via `.env.docker` auth limit variables
- Cloudflare edge rule recommendations are documented in:
  `/home/ohmz/StudioProjects/Tday/docs/security/cloudflare-auth-hardening.md`

## Native Android (Compose)

A new native Android client now lives in:

`/home/ohmz/StudioProjects/Tday/android-compose`

Open that folder in Android Studio to run the Kotlin + Jetpack Compose app.

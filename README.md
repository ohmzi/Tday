# T'Day

Personal task planner — self-hosted, private, multilingual.

- Tasks with priorities, pinning, drag-and-drop reordering, and RFC 5545 recurrence
- Calendar with month, week, and day views
- Notes with a rich text editor (TipTap)
- Lists (projects) for organization with colors and icons
- Completion history and AI-powered task summaries (Ollama)
- 11 languages via next-intl
- Native Android client (Kotlin + Jetpack Compose)
- Native iOS client (SwiftUI + SwiftData)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Web app | Next.js 15 (App Router), React 18, TypeScript 5, Tailwind CSS 4 |
| Database | PostgreSQL 15, Prisma 6 |
| Auth | NextAuth v5 (JWT sessions, PBKDF2 credentials, optional OAuth) |
| AI | Ollama (local, e.g. qwen2.5:0.5b) |
| Android | Kotlin, Jetpack Compose, Hilt, Retrofit, Material 3 |
| iOS | SwiftUI, SwiftData, URLSession, Observation |
| Infra | Docker Compose, GitHub Actions CI/CD, GHCR |

## Quick Start

### Docker (recommended)

```bash
cp .env.example .env.docker
# Edit .env.docker — at minimum set AUTH_SECRET and DATABASE_URL

docker compose up -d --build
docker exec -it tday_ollama ollama pull qwen2.5:0.5b
```

Docker Compose starts three services:

| Service | Container | Port |
|---------|-----------|------|
| Next.js app | `tday` | `2525 → 3000` |
| PostgreSQL | `tday_db` | 5432 (internal) |
| Ollama | `tday_ollama` | 11434 (internal) |

GPU acceleration: `tday_ollama` uses `gpus: all` by default — install the NVIDIA Container Toolkit on the Docker host.

### Local Development

```bash
npm install
# Requires a running PostgreSQL instance
npx prisma migrate deploy
npm run dev          # starts Next.js with Turbopack
npm run lint         # ESLint
npm run test         # Jest
```

### Android

Open `android-compose/` in Android Studio (SDK 35 required) and run on device or emulator. See [`android-compose/README.md`](android-compose/README.md) for first-launch behavior and persistence details.

### iOS

Open `ios/` in Xcode on macOS and use the `Tday/` source tree for the native SwiftUI app. See [`ios/README.md`](ios/README.md) for the current structure and environment notes.

## Project Structure

```
Tday/
├── app/                    # Next.js App Router (pages, API routes, auth)
├── android-compose/        # Native Android client (Kotlin + Compose)
├── ios/                    # Native iOS client (SwiftUI + SwiftData)
├── components/             # Shared React components (UI, todo, sidebar, admin)
├── features/               # Feature modules (calendar, list, notes, todos, user)
├── hooks/                  # Shared React hooks
├── i18n/                   # next-intl routing and request config
├── lib/                    # Server utilities (Prisma, security, dates, NLP)
├── messages/               # Locale JSON files (11 languages)
├── prisma/                 # Schema and migrations
├── providers/              # React context providers
├── public/                 # Static assets
├── scripts/                # Docker entrypoint
├── tests/                  # Jest test suites
└── docs/                   # Architecture, coding standards, guides
```

## Documentation

| Document | Purpose |
|----------|---------|
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Developer setup, conventions, PR process |
| [`SECURITY.md`](SECURITY.md) | Security practices and responsible disclosure |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | System design, domain boundaries, data flow |
| [`docs/CODING_STANDARDS.md`](docs/CODING_STANDARDS.md) | Code quality rules, naming, patterns |
| [`docs/API_GUIDELINES.md`](docs/API_GUIDELINES.md) | REST API contracts and conventions |
| [`docs/TESTING.md`](docs/TESTING.md) | Testing strategy and expectations |
| [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) | Docker, CI/CD, secrets, releases |
| [`docs/adr/`](docs/adr/) | Architecture Decision Records |

## License

Private repository. All rights reserved.

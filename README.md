# T'Day

Personal task planner — self-hosted, private, multilingual.

- Tasks with priorities, pinning, drag-and-drop reordering, and RFC 5545 recurrence
- Calendar with month, week, and day views
- Lists (projects) for organization with colors and icons
- Completion history and AI-powered task summaries (Ollama)
- 11 languages via i18next
- Native Android client (Kotlin + Jetpack Compose)
- Native iOS client (SwiftUI + SwiftData)

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | Vite, React 18, TypeScript 5, React Router, Tailwind CSS 4, i18next |
| Backend | Ktor (Kotlin), Exposed ORM, Flyway migrations |
| Database | PostgreSQL 15 |
| Auth | JWE sessions, PBKDF2 credentials, credential envelope encryption |
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
| Ktor backend + Vite SPA | `tday_backend` | `2525 → 8080` |
| PostgreSQL | `tday_db` | 5432 (internal) |
| Ollama | `tday_ollama` | 11434 (internal) |

GPU acceleration: `tday_ollama` uses `gpus: all` by default — install the NVIDIA Container Toolkit on the Docker host.

### Local Development

```bash
# Frontend (Vite SPA)
cd tday-web
npm install
npm run dev              # starts Vite dev server

# Backend (Ktor)
./gradlew :tday-backend:run   # starts Ktor on port 8080

# Linting & testing
cd tday-web && npm run lint    # ESLint
cd tday-web && npm run test    # Vitest
./gradlew :tday-backend:test   # Kotlin JUnit 5
```

Requires a running PostgreSQL instance. The Ktor backend applies Flyway migrations automatically on startup.

### Android

Open `android-compose/` in Android Studio (SDK 35 required) and run on device or emulator. See [`android-compose/README.md`](android-compose/README.md) for first-launch behavior and persistence details.

### iOS

Open `ios-swiftUI/` in Xcode on macOS and use the `Tday/` source tree for the native SwiftUI app. See [`ios-swiftUI/README.md`](ios-swiftUI/README.md) for the current structure and environment notes.

## Project Structure

```
Tday/
├── tday-web/                  # Vite SPA (React + TypeScript + Tailwind)
│   ├── src/
│   │   ├── components/        # Shared React components
│   │   ├── features/          # Feature modules (calendar, list, todos)
│   │   ├── hooks/             # Shared React hooks
│   │   ├── lib/               # Client utilities (security, dates, API client)
│   │   ├── pages/             # Route pages
│   │   └── providers/         # React context providers
│   ├── messages/              # Bundled default locale fallback (`en.json`)
│   ├── public/                # Static assets and lazy-loaded locale bundles
│   └── tests/                 # Vitest guardrail and unit suites
├── tday-backend/              # Ktor backend (Kotlin)
│   └── src/main/kotlin/
│       └── com/ohmz/tday/
│           ├── config/        # App configuration
│           ├── db/            # Exposed tables and migrations
│           ├── di/            # Koin dependency injection
│           ├── domain/        # Domain types and validation
│           ├── models/        # Request/response DTOs
│           ├── plugins/       # Ktor plugins (routing, auth, headers)
│           ├── routes/        # API route handlers
│           ├── security/      # Auth, encryption, throttling
│           └── services/      # Business logic
├── shared/                    # Shared Kotlin Multiplatform code
├── android-compose/           # Native Android client (Kotlin + Compose)
├── ios-swiftUI/               # Native iOS client (SwiftUI)
├── scripts/                   # Git hooks
├── docs/                      # Architecture, coding standards, guides
├── Dockerfile.backend         # Multi-stage Docker build (Vite + Ktor)
└── docker-compose.yaml        # Full stack orchestration
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

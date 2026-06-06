# T'Day

Private, self-hosted personal task planning with native mobile apps and local-first behavior.

T'Day is designed to be a quiet daily planner, not a generic productivity platform. The end goal is
a single product expressed across web, Android, and iOS:

- Scheduled tasks with priorities, pinning, drag-and-drop reordering, reminders, and RFC 5545
  recurrence.
- Floater/Anytime tasks for unscheduled work, with their own lists and completed history.
- Calendar with month, week, and day views, anchored headers, bounded navigation, overdue
  visibility, and cross-platform paging rules.
- Local Mode on Android and iOS for offline-only use without server setup or login.
- Server Mode for self-hosted sync, realtime updates, encrypted sessions, and private PostgreSQL
  storage.
- Local-first mobile data backed by Room on Android and SwiftData on iOS.
- Completion history, list metadata preservation, task search, widgets, car task surfaces, in-app
  update/version compatibility, and backend summaries with optional local AI via Ollama.
- 11 web locales via i18next, with mobile strings handled through platform-local patterns.

## Tech Stack

| Layer            | Technology                                                                                                               |
|------------------|--------------------------------------------------------------------------------------------------------------------------|
| Frontend         | Vite, React 18, TypeScript 5, React Router, Tailwind CSS 4, i18next                                                      |
| Backend          | Ktor (Kotlin), Exposed ORM, Flyway migrations                                                                            |
| Database         | PostgreSQL 15                                                                                                            |
| Auth             | Rolling JWE cookie sessions, PBKDF2 credentials, credential envelope encryption                                          |
| Shared Contracts | Kotlin Multiplatform DTOs, enums, validators, and route constants                                                        |
| AI               | Optional Ollama (local, default `qwen3.5:0.8b`) with backend logic fallback                                              |
| NLP (scheduling) | On-device natural-language date parsing — web chrono-node, iOS `NSDataDetector`, Android Natty (offline, no AI/network)   |
| Android          | Kotlin, Jetpack Compose, Hilt, Retrofit, Room, WorkManager, Glance widgets, internal car surface, Material 3             |
| iOS              | SwiftUI, SwiftData, URLSession, Observation, Keychain/cookie handling, WidgetKit widgets, CarPlay templates, App Intents |
| Infra            | Docker Compose, GitHub Actions CI/CD, GHCR                                                                               |

## Documentation Currency

Markdown files were audited on **2026-05-29** using git history. The docs were mostly last touched
between March and May 2026, while recent commits added Local Mode, Floater/Anytime tasks,
RootFeedDock, Room/SwiftData cache parity, mobile sheet/swipe/calendar polish, and offline sync
refinements. This refresh updates the project docs around that current direction.

See [`docs/REPO_HOUSEKEEPING.md`](docs/REPO_HOUSEKEEPING.md) for the markdown audit summary and
maintenance expectations.

## Quick Start

### Docker (recommended)

```bash
cp .env.example .env.docker
# Edit .env.docker — at minimum set AUTH_SECRET and DATABASE_URL

docker compose up -d --build
```

Docker Compose starts the backend and database by default. Summary still works without Ollama by
using the backend logic fallback.

To enable local AI summaries:

```bash
# In .env.docker, set:
OLLAMA_URL=http://ollama:11434
OLLAMA_MODEL=qwen3.5:0.8b

docker compose --profile ai pull ollama ollama-model-setup
docker compose --profile ai up -d --build
```

The AI profile starts Ollama and runs a one-shot model setup container that pulls `qwen3.5:0.8b` and
attempts to remove the old `qwen2.5:0.5b` model if it exists. Pull the Ollama images during updates
too; the qwen3.5 model requires a recent Ollama runtime.

Docker Compose services:

| Service                     | Container      | Port                                                |
|-----------------------------|----------------|-----------------------------------------------------|
| Ktor backend + Vite SPA     | `tday_backend` | `localhost:2525 → 8080` (localhost only by default) |
| PostgreSQL                  | `tday_db`      | 5432 (internal)                                     |
| Ollama, optional AI profile | `tday_ollama`  | 11434 (internal)                                    |

Port 2525 is bound to `127.0.0.1` by default so the backend is not exposed over HTTP to the network.
For remote access, see [`docs/REMOTE_ACCESS.md`](docs/REMOTE_ACCESS.md) — it covers Cloudflare
Tunnel, Tailscale, WireGuard, ZeroTier, SSH tunnels, ngrok, and frp. Set `TDAY_HOST_BIND=0.0.0.0` in
the root `.env` to open the port externally (see [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)).

GPU acceleration: `tday_ollama` runs on CPU by default so the stack comes up on any host. For
NVIDIA GPU acceleration, install the NVIDIA Container Toolkit on the Docker host and layer the GPU
override on top of every Compose command:

```bash
docker compose -f docker-compose.yaml -f docker-compose.gpu.yaml --profile ai up -d --build
```

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

Requires a running PostgreSQL instance. The Ktor backend applies Flyway migrations automatically on
startup.

### Android

Open `android-compose/` in Android Studio (SDK 35 required) and run on a device or emulator. The app
can start in Local Mode or connect to a self-hosted server. See [
`android-compose/README.md`](android-compose/README.md) for structure, first-launch behavior,
persistence, sync, car-surface constraints, and release notes.

### iOS

Open `ios-swiftUI/TdayApp.xcodeproj` in Xcode on macOS and run the `Tday` scheme. The app supports
Local Mode, server workspaces, SwiftData cache, reminders, Today and Floater WidgetKit widgets,
CarPlay templates, and the shared mobile feature surface. See [
`ios-swiftUI/README.md`](ios-swiftUI/README.md) for structure and environment notes.

## Project Structure

```
Tday/
├── tday-web/                  # Vite SPA (React + TypeScript + Tailwind)
│   ├── src/
│   │   ├── components/        # Shared React components
│   │   ├── features/          # Feature modules (calendar, completed, list, release, today)
│   │   ├── hooks/             # Shared React hooks
│   │   ├── lib/               # Client utilities (security, dates, API, todo helpers)
│   │   ├── pages/             # Route pages
│   │   ├── providers/         # React context providers
│   │   └── types/             # Web-only TypeScript domain/UI types
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
├── shared/                    # KMP DTOs, enums, validators, and route constants
├── android-compose/           # Native Android client (Compose, Room, Hilt, Glance widget)
├── ios-swiftUI/               # Native iOS client (SwiftUI, SwiftData, Observation, WidgetKit target)
│   ├── Tday/                  # Main iOS app
│   ├── TdayWidget/            # WidgetKit extension and snapshots
│   └── Tests/                 # iOS test target
├── scripts/                   # Git hooks, version sync, operational helpers
├── version.json               # Global app/server version and compatibility manifest
├── docker/                    # Container runtime helper config
├── docs/                      # Product, architecture, data, coding, testing, deployment
├── Dockerfile.backend         # Multi-stage Docker build (Vite + Ktor)
└── docker-compose.yaml        # Full stack orchestration
```

## Product Model

| Concept           | Purpose                                                                                           |
|-------------------|---------------------------------------------------------------------------------------------------|
| Scheduled task    | Due-date task used by Today, Scheduled, Calendar, reminders, recurrence, and scheduled-task lists |
| Floater           | Unscheduled Anytime task with priority, pinning, ordering, list membership, and completion        |
| List              | Project/group for scheduled tasks                                                                 |
| Floater list      | Project/group for floaters                                                                        |
| Completed history | Completed todo and completed floater records, preserving list metadata where possible             |
| Local Mode        | Mobile-only workspace that never requires server setup or login                                   |
| Server Mode       | Authenticated self-hosted workspace with local optimistic writes and sync replay                  |

The detailed data contract lives in [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md).

## Natural-language scheduling

Typing a date/time phrase in a task title auto-fills the Due. "Buy milk tomorrow at 5pm" sets the
due to 5:00 PM tomorrow, highlights the recognized phrase as you type, and saves the task as just
"Buy milk".

This runs **entirely on-device — no AI model and no network** — on every client, each using its
platform's native date parser:

| Platform | Parser                                                                            |
|----------|-----------------------------------------------------------------------------------|
| Web      | [chrono-node](https://github.com/wanasit/chrono) (client-side JavaScript)         |
| iOS      | Foundation `NSDataDetector` (Apple's built-in date detector)                      |
| Android  | [Natty](https://github.com/joestelmach/natty) (bundled JVM library)               |

The recognized phrase stays visible with a warm highlight while you type and is stripped from the
saved title. Parsing uses the device timezone and the result is stored as a UTC instant, so the
same task shows the correct local time on every device. (This is unrelated to the optional Ollama
AI, which only powers task *summaries* — not scheduling.)

## Widgets

Both mobile platforms ship two home-screen widgets, standardized across Android and iOS:

| Widget  | Shows                     |
|---------|---------------------------|
| Today   | Scheduled tasks due today |
| Floater | Unscheduled Anytime tasks |

Widgets use an **app-driven, immediate-refresh** model rather than the platform's slow
system-scheduled intervals. After every task mutation (add, edit, complete, delete) and on app
background, the app pushes an explicit refresh — Android via `GlanceAppWidgetManager` and a
WorkManager fallback, iOS via `WidgetCenter` reloads backed by an App Group snapshot. A background
worker on each platform refreshes every 15 minutes as a freshness fallback, and widget state carries
a "last updated" indicator.

See [`docs/WIDGET_SYNC.md`](docs/WIDGET_SYNC.md) for the full architecture, file layout, and
per-platform integration checklist.

## In-car surfaces

Both mobile apps expose the Today/Floater task surface for driving contexts, kept intentionally
minimal — list templates plus icon controls, with voice capture for adding a task.

- **iOS — CarPlay.** A CarPlay template scene (`ios-swiftUI/Tday/Feature/CarPlay/`) built on
  `CPListTemplate` with bar-button controls for the Today/Floater switch. Adding a task goes through
  App Intents / Siri voice (the plus action offers a template-compliant iPhone handoff). Real
  CarPlay deployment is gated on Apple granting the CarPlay entitlement for the app's category; the
  code stays buildable and entitlement-gated until then.
- **Android — internal car surface (not Android Auto).** An app-internal adaptive surface at
  `tday://car` (`android-compose/.../feature/car/`). It deliberately does **not** declare Android
  Auto / Android Automotive metadata, because Google Play's current car-app categories don't include
  generic task/productivity apps. It defaults to Today, switches to Floater with icon-only controls,
  and uses on-device speech recognition for the add action, falling back to the normal create-task
  flow when speech is unavailable.

Per-platform detail lives in [`ios-swiftUI/README.md`](ios-swiftUI/README.md) (CarPlay) and
[`android-compose/README.md`](android-compose/README.md) (Car Surface).

## Dashboard widget (Homarr)

T'Day can surface your tasks on a [Homarr](https://homarr.dev) dashboard via a dedicated **Tday
Tasks** widget. The widget shows a chosen view — **Today, Scheduled, Overdue, or Floater** — and
lets you complete (with an Undo toast), quick-add (one task per line, with priority/list/due), edit,
and delete tasks directly from the dashboard.

It is **not** an embedded iframe of the web app (T'Day sends `X-Frame-Options: DENY`). Instead the
widget talks to T'Day's **token-authenticated REST API** from Homarr's own backend, so no extra
service is needed. Connecting it up:

1. In T'Day, go to **Settings → API Key / Dashboard Access → Generate Key** and copy the key (shown
   once).
2. In Homarr, add the **Tday** integration: paste your T'Day **server URL** and the **API key**.
3. Add the **Tday Tasks** widget and pick a view.

The widget and integration live in the [`ohmzi/homarr`](https://github.com/ohmzi/homarr) fork, with
end-user docs in [`ohmzi/documentation`](https://github.com/ohmzi/documentation/tree/develop). To
build your own integration against the same API, see
[`docs/API_INTEGRATION.md`](docs/API_INTEGRATION.md).

## Product Direction

Future coding should preserve these expectations:

- Mobile UX parity matters. Android and iOS should expose the same feature surface while using
  native implementation patterns.
- Local cache is the mobile screen source of truth. Network sync updates the cache; screens observe
  cache changes.
- Scheduled tasks and floaters are separate domain concepts. Do not represent unscheduled work by
  making `Todo.due` nullable.
- Server APIs must remain stable for mobile clients, with explicit compatibility behavior before
  breaking changes.
- Code should stay easy to reason about: narrow responsibilities, clear dependency direction, named
  concepts over cleverness, and no shared abstraction until it has a real second use.
- Documentation is updated with the behavior it describes.

The fuller north star is in [`docs/PRODUCT_DIRECTION.md`](docs/PRODUCT_DIRECTION.md).

## Documentation

| Document                                                 | Purpose                                                                                   |
|----------------------------------------------------------|-------------------------------------------------------------------------------------------|
| [`AGENTS.md`](AGENTS.md)                                 | AI agent workflow, git expectations, and cross-platform UX parity rules                   |
| [`CONTRIBUTING.md`](CONTRIBUTING.md)                     | Developer setup, conventions, PR process                                                  |
| [`SECURITY.md`](SECURITY.md)                             | Security practices and responsible disclosure                                             |
| [`docs/PRODUCT_DIRECTION.md`](docs/PRODUCT_DIRECTION.md) | Product goal, mobile rules, Local Mode, Floater/Anytime direction                         |
| [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md)               | Backend tables, shared DTOs, mobile cache records, mutation queue, data-change checklist  |
| [`docs/WIDGET_SYNC.md`](docs/WIDGET_SYNC.md)             | Home-screen widget sync architecture, file layout, and Android/iOS integration checklists |
| [`docs/REPO_HOUSEKEEPING.md`](docs/REPO_HOUSEKEEPING.md) | Markdown audit, generated-file rules, docs maintenance, repo hygiene                      |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)           | System design, domain boundaries, data flow                                               |
| [`docs/CODING_STANDARDS.md`](docs/CODING_STANDARDS.md)   | Code quality rules, naming, patterns                                                      |
| [`docs/API_GUIDELINES.md`](docs/API_GUIDELINES.md)       | REST API contracts and conventions                                                        |
| [`docs/API_INTEGRATION.md`](docs/API_INTEGRATION.md)     | API-key auth + endpoints for external integrations (e.g. the Homarr Tday Tasks widget)    |
| [`docs/TESTING.md`](docs/TESTING.md)                     | Testing strategy and expectations                                                         |
| [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md)               | Docker, CI/CD, secrets, releases                                                          |
| [`docs/REMOTE_ACCESS.md`](docs/REMOTE_ACCESS.md)         | Remote access methods (Cloudflare Tunnel, Tailscale, WireGuard, etc.)                     |
| [`docs/TELEMETRY.md`](docs/TELEMETRY.md)                 | What crash reporting collects (and doesn't) — no PII, no analytics                        |
| [`docs/SENTRY_RUNBOOK.md`](docs/SENTRY_RUNBOOK.md)       | Sentry setup, alerting, release artifacts, smoke drills, and failure triage               |
| [`docs/adr/`](docs/adr/)                                 | Architecture Decision Records                                                             |

## Verification

Run the smallest meaningful checks for your change, then broaden when a change crosses boundaries.

```bash
# Web
cd tday-web && npm run lint
cd tday-web && npm run test

# Backend
./gradlew :tday-backend:test

# Android
cd android-compose && ./gradlew :app:compileDebugKotlin
cd android-compose && ./gradlew :app:testDebugUnitTest

# iOS
xcodebuild test -project ios-swiftUI/TdayApp.xcodeproj -scheme Tday -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.6'
```

For user-facing mobile changes, do a parity pass on both platforms even when only one platform was
edited.

## License

Private repository. All rights reserved.

# Architecture

This document describes the high-level system design, domain boundaries, and key technical decisions for T'Day. Product intent lives in [`PRODUCT_DIRECTION.md`](PRODUCT_DIRECTION.md); durable data shape lives in [`DATA_MODEL.md`](DATA_MODEL.md).

## System Overview

T'Day is a **monorepo application** with a Kotlin/Ktor backend, a React SPA frontend, shared Kotlin Multiplatform code, and native local-first mobile clients:

```
┌─────────────────────────────────────────────────────────────┐
│                        Clients                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │  Web (React)  │  │ Android App  │  │    iOS App      │  │
│  │  Vite SPA     │  │ Compose/Room │  │ SwiftUI/Data   │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────────┘  │
│         │                 │                  │               │
└─────────┼─────────────────┼──────────────────┼───────────────┘
          │ HTTPS/WS        │ HTTPS            │ HTTPS
          ▼                 ▼                  ▼
┌─────────────────────────────────────────────────────────────┐
│              Ktor Backend (Kotlin/JVM, Netty)               │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────────┐ │
│  │ Security │→ │  Ktor Plugins │→ │  Route Handlers       │ │
│  │ Intercept│  │  (Headers,    │  │  (REST + WebSocket)   │ │
│  │          │  │   Serializer) │  │                       │ │
│  └──────────┘  └──────────────┘  └───────────┬───────────┘ │
│                                               │             │
│  ┌──────────────────┐  ┌─────────────────────┐│            │
│  │  JWE Sessions     │  │  Exposed ORM        ││            │
│  │  (Nimbus JOSE)    │  │  (DSL + Tables)     ││            │
│  └──────────────────┘  └─────────┬───────────┘│            │
│                                   │            │            │
│  ┌──────────────────┐             │            │            │
│  │  Koin DI          │             │            │            │
│  └──────────────────┘             │            │            │
└───────────────────────────────────┼────────────┘            │
                                    ▼                         │
                          ┌─────────────────┐  ┌─────────────┐
                          │  PostgreSQL 15   │  │  Ollama     │
                          │  (data store)    │  │  (local AI) │
                          └─────────────────┘  └─────────────┘
```

### Architectural Direction

- Web is the desktop/admin/broad-access client.
- Backend owns auth, persistence, tenant isolation, server compatibility, AI summaries, and realtime events.
- Android and iOS own the primary mobile experience and render from local cache first.
- Local Mode is a mobile-only workspace with no server dependency.
- Server Mode uses optimistic local writes plus pending mutation replay.
- Scheduled tasks and floaters are separate concepts; do not make `Todo.due` nullable to represent Anytime work.
- Boundaries should stay readable and directional: clients render state, presentation layers coordinate work, repositories/services own domain/data operations, and transport/storage details sit at the edges.
- Shared abstractions are introduced only when they reduce real duplication or clarify a cross-platform contract.

### Shared Kotlin Multiplatform Module

The `shared/` module is a Kotlin Multiplatform (KMP) library targeting JVM, Android, and iOS frameworks. It provides the source of truth for:

- Serializable DTOs (request/response models)
- Domain enums (`Priority`, `UserRole`, `ApprovalStatus`, `SortBy`, `GroupBy`, `ProjectColor`, etc.)
- Contract validators

| Consumer | Target | How it's consumed |
|----------|--------|-------------------|
| Backend (`tday-backend`) | JVM | Gradle `project(":shared")` |
| Android (`android-compose`) | Android | Gradle `project(":shared")` |
| iOS (`ios-swiftUI`) | Swift models | Mirrored manually in `Core/Model/ApiModels.swift` and checked with contract tests |

## Domain Model

The application is organized around these core domains:

| Domain | Description | Models |
|--------|------------|--------|
| **Auth** | Registration, login, sessions, approval, admin | `User`, `Account`, `AuthThrottle`, `AuthSignal` |
| **Todos** | Scheduled task CRUD, RFC 5545 recurrence, priorities, ordering | `Todo`, `TodoInstance`, `CompletedTodo` |
| **Floaters** | Unscheduled Anytime task CRUD, priorities, ordering | `Floater`, `CompletedFloater` |
| **Lists** | Scheduled-task project grouping with colors and icons | `List` |
| **Floater Lists** | Floater project grouping with colors and icons | `FloaterList` |
| **Files** | Reserved/legacy file metadata retained for cleanup paths; no active upload/download API | `File` |
| **Preferences** | Sort/group/direction settings per user | `UserPreferences` |
| **Admin** | App configuration, user management | `AppConfig` |
| **Operations** | Cron jobs, event logging | `CronLog`, `eventLog` |
| **Mobile Sync** | Local cache metadata and pending replay state | Android Room entities, iOS SwiftData entities, `PendingMutationRecord` |

## Backend Architecture (Ktor)

### Request Flow

```
Client Request
    │
    ▼
Ktor Pipeline Intercept (Security.kt)
    ├── Read Bearer token or session cookie
    ├── Decode JWE → validate claims (expiry, tokenVersion)
    ├── Check AuthUserCache (30s TTL) → DB lookup on miss
    └── Attach AuthUser to call attributes
    │
    ▼
Ktor Plugins
    ├── SecurityHeaders (CSP, HSTS, X-Frame-Options, COOP, CORP)
    ├── ContentNegotiation (kotlinx.serialization JSON)
    ├── StatusPages (AppError → ApiError JSON mapping)
    └── WebSockets (ping/timeout/frame config)
    │
    ▼
Route Handlers (routes/*.kt)
    ├── call.withAuth { } → require authenticated user
    ├── Validate input via Konform validateOrFail() → Either<AppError, T>
    ├── Delegate to service layer
    └── Respond with JSON or WebSocket frames
    │
    ▼
Services (services/*.kt)
    ├── Return Either<AppError, T> (Arrow)
    └── newSuspendedTransaction(Dispatchers.IO) { } for DB access
    │
    ▼
Exposed ORM → PostgreSQL (via HikariCP)
```

### Package Structure (Backend)

```
tday-backend/src/main/kotlin/com/ohmz/tday/
├── Application.kt          # main(), module(): wires all plugins and DI
├── config/
│   ├── AppConfig.kt        # Loads all env vars (and optional _FILE secret paths)
│   └── DatabaseConfig.kt   # HikariCP pool, Flyway migrate, Exposed connect
├── db/
│   ├── enums/PgEnums.kt    # PostgreSQL enum ↔ shared Kotlin enum mapping
│   ├── tables/             # Exposed Table definitions (Users, Todos, etc.)
│   └── util/               # Database utility helpers
├── di/AppModule.kt         # Koin modules: config, security, services
├── domain/
│   ├── AppError.kt         # Sealed error hierarchy → HTTP status codes
│   ├── AuthContext.kt      # withAuth { } helper for authenticated routes
│   ├── DomainEvent.kt      # Sealed WebSocket event types
│   └── Validations.kt      # Konform validators for shared DTOs
├── models/
│   ├── request/            # Mostly typealiases to shared models
│   └── response/           # Response-specific DTOs
├── plugins/
│   ├── Routing.kt          # /health, /api/*, /ws, static SPA serving
│   ├── CallLogging.kt      # Structured request logging
│   ├── Cors.kt             # CORS policy
│   ├── RateLimiting.kt     # App-layer request throttling
│   ├── Security.kt         # JWE bearer + cookie auth, pipeline intercept
│   ├── SecurityHeaders.kt  # CSP, HSTS, X-Frame-Options, etc.
│   ├── SentryPlugin.kt     # Sentry JVM configuration
│   ├── Serialization.kt    # kotlinx.serialization JSON config
│   └── StatusPages.kt      # AppError → JSON ApiError mapping
├── routes/                 # HTTP route handlers by domain
│   └── auth/               # Auth-specific routes
├── security/               # JWT, passwords, throttling, captcha, encryption
└── services/               # Business logic (todo, list, user, admin, etc.)
```

### Installed Ktor Plugins

| Plugin | Purpose |
|--------|---------|
| **Koin** | Dependency injection (config, security, service modules) |
| **WebSockets** | Real-time domain event streaming per authenticated user |
| **ContentNegotiation** | JSON request/response via kotlinx.serialization |
| **DefaultHeaders / SecurityHeaders** | Security headers (CSP, HSTS in production, X-Frame-Options, etc.) |
| **StatusPages** | Maps `AppError` / generic errors to JSON `ApiError` responses |
| **Authentication** | Bearer token provider + pipeline intercept for JWE/cookie auth |
| **Routing** | API routes, health check, WebSocket, optional static SPA |
| **CallLogging** | Structured request logging without sensitive payloads |
| **RateLimiting** | App-layer request throttling before handlers |

### Error Handling (Backend)

```
Route Handler
    │
    ├── withAuth { } → business logic
    │       │
    │       ├── Returns Either<AppError, T>
    │       └── AppError → respondAppError with HTTP status
    │
    └── StatusPages plugin catches exceptions
            │
            ├── ApiException (deprecated) → JSON { message, code } + HTTP status
            └── Unknown error → 500 + generic message
```

The primary error path uses the `AppError` sealed interface with `Either<AppError, T>` from Arrow. The legacy `ApiException` hierarchy is `@Deprecated` and will be removed in a future release. `StatusPages.kt` handles both paths. Internal details are never exposed to clients.

## Web Architecture (Vite + React)

### Stack

- **React 18** with **TypeScript 5** bundled by **Vite 6**
- **React Router 7** with locale-prefixed URLs (`/:locale/app/*`)
- **TanStack React Query 5** for server state
- **Tailwind CSS 4** with Radix UI primitives (shadcn-style)
- Shared `fetch` API client wrapper with cookie-based auth

### Directory Structure

```
tday-web/src/
├── main.tsx              # React DOM entry point
├── App.tsx               # Provider tree: Theme → Query → Auth → Tooltip → ErrorBoundary → Router + Toaster
├── router.tsx            # Route definitions (public, protected, calendar layouts)
├── globals.css           # Tailwind @theme tokens, CSS variables
├── i18n.ts               # i18next configuration (11 locales, path-based detection)
├── components/           # Shared UI (ui/* primitives, Sidebar, auth, todo pieces)
├── features/             # Feature modules (calendar, completed, list, release, todayTodos, user)
├── hooks/                # Shared React hooks
├── lib/                  # Utilities (api-client, navigation, cache, performance, security, dates, todo)
├── pages/                # Route-level screens and layouts
├── providers/            # React context providers (Auth, Theme, Query, Menu, etc.)
└── types/                # TypeScript type definitions
```

### State Management (Web)

- **Server state**: TanStack React Query (`useQuery`, `useMutation`) for all API-fetched data. Default `staleTime: 60_000`. Global `QueryCache.onError` shows destructive toasts for all query failures.
- **Auth state**: Custom `AuthProvider` context managing session fetch/refresh via the shared `api-client` (`api.GET`/`api.POST`).
- **UI state**: React `useState`/`useReducer` within components. React Context for cross-cutting state (menu, preferences, todo form/mutations).
- **Theme**: `next-themes` provider with system/light/dark modes via CSS `class` strategy.
- **Toasts**: Sonner `<Toaster />` mounted at the app root. Components use `useToast()` hook; the `QueryCache` uses the imperative `toast` API directly.

### API Communication (Web)

The web SPA communicates with the Ktor backend via a thin `fetch` wrapper:

- `api.GET/POST/PATCH/DELETE` functions in `lib/api-client.ts` with `ApiError` typing
- All HTTP calls (including auth and preferences) route through the shared API client
- `credentials: "same-origin"` for cookie-based sessions
- Browser `fetch` cache mode set to `no-store` on all private API requests
- Dev: Vite proxy forwards `/api` to `http://localhost:8080`
- Production: Ktor serves the SPA as static files from `STATIC_FILES_DIR`

## Android Architecture

### Pattern: MVVM with Domain Repositories

```
┌───────────────────────────────────────────────────┐
│  Composable Screens (UI Layer)                    │
│  Observe StateFlow → render → invoke ViewModel    │
└──────────────────────┬────────────────────────────┘
                       │
┌──────────────────────┴────────────────────────────┐
│  @HiltViewModel classes (Presentation Layer)      │
│  MutableStateFlow + viewModelScope.launch         │
│  Coordinate repositories + app services directly  │
└──────────────────────┬────────────────────────────┘
                       │
┌──────────────────────┴────────────────────────────┐
│  Data / App Services                              │
│  TodoRepository, ListRepository, FloaterListRepo, │
│  CompletedRepo, AuthRepository, SettingsRepo,     │
│  SyncManager, OfflineCacheManager, Reminder APIs  │
└──────┬───────────────────────────────┬────────────┘
       │                               │
┌──────┴──────┐              ┌─────────┴─────────┐
│ Retrofit    │              │ Room + Encrypted    │
│ (Network)   │              │ prefs for secrets   │
└─────────────┘              └───────────────────┘
```

### Key Design Decisions (Android)

- **MVVM without an Android use-case layer**: ViewModels coordinate repositories and app services directly instead of routing through Android-specific `UseCase` wrappers.
- **Domain-specific repositories**: Data access is split by concern (`TodoRepository`, `ListRepository`, `CompletedRepository`, `AuthRepository`, `SettingsRepository`) instead of a single catch-all repository.
- **Local-first sync**: `OfflineCacheManager` stores the local source of truth in Room, while `SyncManager` replays pending mutations and refreshes remote snapshots in Server Mode.
- **Local Mode**: Mobile can run without server setup. Server-only operations are hidden or disabled and pending mutations are not retained for replay.
- **Cache invalidation**: `OfflineCacheManager.cacheDataVersion` is observed by ViewModels so screens can hydrate from cache when local data changes.
- **Auth compatibility**: The Android client implements the JWE credential flow (CSRF token fetch → credential callback → session cookie) using Retrofit + an encrypted cookie store.
- **Navigation**: Programmatic Compose Navigation (`NavHost`) with `sealed class AppRoute` — no XML navigation graphs.
- **Server discovery**: Runtime server URL configuration with optional certificate fingerprint pinning for self-hosted instances.
- **Root feeds**: `RootFeedDock` switches between Home and Floater/Anytime, with a shared root create action.

### Package Structure (Android)

```
com.ohmz.tday.compose/
├── core/
│   ├── data/          # Repositories, OfflineCacheManager, SyncManager, stores
│   │   └── db/        # Room entities, DAOs, and database
│   ├── model/         # ApiModels (DTOs), DomainModels (UI types)
│   ├── navigation/    # AppRoute sealed class
│   ├── network/       # Hilt NetworkModule, TdayApiService, EncryptedCookieStore
│   ├── notification/  # Alarms, WorkManager, receivers
│   ├── security/      # Probe/decryption helpers
│   └── ui/            # Shared non-feature app UI helpers
├── feature/
│   ├── app/           # AppViewModel (bootstrap, sync, session)
│   ├── auth/          # AuthViewModel
│   ├── home/          # HomeScreen + HomeViewModel
│   ├── todos/         # Todo/Floater list screens + ViewModel
│   ├── completed/     # CompletedScreen + CompletedViewModel
│   ├── calendar/      # CalendarScreen + CalendarViewModel
│   ├── settings/      # SettingsScreen
│   ├── release/       # In-app update and latest release
│   ├── widget/        # Today Tasks Glance widget and refresh coordinator
│   └── onboarding/    # OnboardingWizardOverlay
└── ui/
    ├── component/     # Shared composables (PullRefresh, CreateTaskBottomSheet, RootFeedDock)
    └── theme/         # Material 3 theme, colors, typography, dimensions
```

## iOS Architecture

### Stack

- **SwiftUI** targeting iOS 17+
- **SwiftData** for local persistence
- **Observation** for ViewModels
- `AppRootView` using `NavigationStack`, root-feed state, onboarding overlay, update gating, deep links, and Local/Server Mode checks

### Structure

```
ios-swiftUI/Tday/
├── Feature/
│   ├── App/          # AppRootView + AppViewModel
│   ├── Home/         # Home root feed
│   ├── Todos/        # Todo/Floater management
│   ├── Calendar/     # Calendar views
│   ├── Completed/    # Completion history
│   ├── Settings/     # User settings
│   ├── Auth/         # Login/register
│   └── Onboarding/   # First-launch flow
├── Core/
│   ├── Data/         # AppContainer, repositories, SwiftData cache, sync
│   ├── Domain/       # Focused use cases
│   ├── Model/        # API/domain/offline models
│   ├── Navigation/   # AppRoute
│   ├── Network/      # TdayAPIService, RealtimeClient (URLSession + cookies)
│   ├── Notification/ # Deep links and reminders
│   ├── Security/     # Probe/decryption helpers
│   ├── UI/           # Shared app UI helpers
│   └── Widget/       # TodayTasks snapshot store
├── UI/
│   ├── Component/    # Shared SwiftUI controls and sheets
│   └── Theme/        # Colors and rounded typography
└── AppRootView.swift # NavigationStack, root feed state, overlays, deep links
```

The WidgetKit extension lives beside the app target at `ios-swiftUI/TdayWidget/` and is wired as the `TdayWidget` app-extension target in both `project.yml` and the Xcode project. It shares snapshots with the app through the App Group suite `group.com.ohmz.tday`. iOS tests live in `ios-swiftUI/Tests/`. Sentry Cocoa is the only notable third-party runtime dependency; core app behavior uses native frameworks.

## Database Design

### Entity Relationships

```
User ──┬── Todo ──── TodoInstance
       │      └──── List (scheduled-task project)
       ├── Floater ─── FloaterList
       ├── CompletedTodo
       ├── CompletedFloater
       ├── File
       ├── UserPreferences
       ├── Account (OAuth)
       └── (approved by) User
```

### ORM and Migrations

- **ORM**: JetBrains Exposed 0.57.0 (DSL/Table API with `newSuspendedTransaction(Dispatchers.IO) {}` for coroutine-safe DB access)
- **Connection pool**: HikariCP 6.2.1 (max 10 connections, auto-commit off)
- **Migrations**: Flyway 10.22.0 with `baselineOnMigrate=true` and a baseline version of `2` for legacy databases. Migration files live in `tday-backend/src/main/resources/db/migration/`, with `V2__full_schema.sql` serving as the clean-install schema snapshot.
- **Driver**: PostgreSQL JDBC 42.7.4

### PostgreSQL Enums

`UserRole`, `ApprovalStatus`, `SortBy`, `GroupBy`, `Direction`, `Priority`, `ProjectColor` — defined as Kotlin enums in `shared/` and mapped to PostgreSQL enums via `PgEnums.kt`.

### Key Patterns

- **Soft completion**: Completed todos are moved to `CompletedTodo` with metadata (completion time, on-time status, days to complete).
- **Floater completion**: Completed floaters are moved to `CompletedFloater` with completion time, days-to-complete metadata, and floater-list metadata.
- **RFC 5545 recurrence**: Todos support `rrule`, `due`, `exdates`, and `durationMinutes`. Instances are materialized in `TodoInstance` for per-occurrence overrides.
- **Tenant isolation**: All data queries filter by `userID`. There are no shared/public data models.
- **Audit fields**: All major models include `createdAt` and `updatedAt`.

## Authentication Flow

### Web and Mobile (JWE Sessions)

```
Client → GET /api/auth/csrf (fetch CSRF token)
       → POST /api/auth/callback/credentials
              │
              ├── Rate limit check (AuthThrottle)
              ├── Optional CAPTCHA verification
              ├── Optional credential envelope decryption (RSA)
              ├── PBKDF2 password verification (or rehash if iterations changed)
              ├── Approval status check
              └── JWE token issued → set as HTTP-only cookie
```

Tokens are encrypted JWTs (JWE) via Nimbus JOSE JWT + BouncyCastle. The Ktor pipeline intercept reads tokens from `Authorization: Bearer` headers or session cookies, decodes the JWE, and validates claims (`tokenVersion`, expiry, role, approval).

### Session Revocation

`SessionControl` bumps `tokenVersion` on the `User` row. Existing tokens fail on the next request when the intercept detects the version mismatch.

### Mobile Auth and Workspace Flow

```
App launch → Probe server (GET /api/mobile/probe)
           → Optional cert fingerprint check
           → GET /api/auth/csrf
           → POST /api/auth/callback/credentials (with CSRF + encrypted envelope)
           → Session cookie stored in EncryptedCookieStore
           → Subsequent requests include cookie automatically
```

Local Mode skips server probe/auth and enters a local-only workspace immediately.

## Real-Time Communication

The backend exposes a `WS /ws` WebSocket endpoint for authenticated users. Domain events (`DomainEvent` sealed class) are streamed as JSON frames — covering todo and list changes. Each user has their own WebSocket channel.

## Internationalization

- 11 locales: `en`, `zh`, `de`, `ja`, `ar`, `ru`, `es`, `fr`, `ms`, `it`, `pt`.
- Managed via **i18next** + **react-i18next** with path-based locale detection.
- English is bundled from `tday-web/messages/en.json` for first paint; other locale bundles are lazy-loaded from `tday-web/public/locales/<lng>/translation.json`.
- Routes are prefixed with `/:locale/` via React Router.

## AI Integration

- **Summary** is served by the backend and remains available without local AI by using deterministic task logic.
- **Ollama** is an optional local AI enhancement for task summaries.
- Model: `qwen3.5:0.8b` by default when Ollama is enabled.
- The feature is toggled globally via `AppConfig.aiSummaryEnabled` (admin setting).
- AI calls are timeout-protected with `OLLAMA_TIMEOUT_MS`; failures fall back to backend logic.

## Caching Strategy

- **Web**: No application-level persistence cache. The shared API client uses browser `fetch` with `cache: "no-store"` for private API requests. TanStack React Query provides in-memory client-side cache with 60-second stale time.
- **Android**: Room-backed local cache for todos, floaters, lists, completed history, pending mutations, and sync metadata. Encrypted preferences still protect credentials, cookies, server config, trust data, theme/reminder preferences, and legacy cache migration input. Cache is the source of truth for screens; network sync updates it periodically, on foreground reconnect, realtime events, and user refresh in Server Mode.
- **iOS**: SwiftData-backed local cache with mirrored `OfflineSyncState` records. Keychain-backed stores protect server config, cookies, credentials, mode state, theme, and reminders. Cache changes notify ViewModels and widget snapshot storage, which writes the Today widget payload into the App Group defaults suite.

## Background Jobs

- **Android reminders**: `AlarmManager` for exact-time reminders, `WorkManager` for periodic reminder rescheduling, `BootRescheduleReceiver` for device restart recovery.
- **iOS reminders**: `UserNotifications` scheduling and notification deep-link routing.
- **Widgets**: Android Glance and iOS WidgetKit expose the same Today Tasks contract: pending scheduled tasks due today only, widget body/header/message/rows open the main app, and the add control opens `tday://todos/create?target=today`.

## Mobile Widgets

The v1 mobile widget surface is intentionally narrow and action-oriented. Widgets do not complete tasks inline; they take users into the app through deep links so Local Mode, optimistic writes, validation, and create/edit sheets remain owned by the main clients.

- **Android**: `TodayTasksWidget` uses Glance with responsive compact, wide, and tall layouts. `TodayTasksWidgetModel` builds setup, empty, and tasks states from cached scheduled todos, and `TodayTasksWidgetRefresher` lets cache writes refresh the widget after Local Mode and optimistic changes. Wide, medium, and tall task layouts use a Glance `LazyColumn`, so overflow rows scroll inside the widget. The widget add deep link opens a dedicated widget-create activity in the main app task so the Today create sheet appears without the main app Home/Today navigation stack; submitting hands task creation to a background submitter and returns to the launcher while the widget refreshes from cache.
- **iOS**: `TdayWidget` is a WidgetKit extension supporting `.systemSmall`, `.systemMedium`, and `.systemLarge`. The app writes schema-versioned snapshots to App Group defaults under `tday.widget.todayTasksSnapshot`; payloads include status, task count, generation time, and capped task rows. System-family WidgetKit widgets stay snapshot/glanceable, so overflow is represented by the total count rather than an in-widget scroll surface.
- **Visual system fit**: each platform owns widget chrome, margins, background removal, tinting/accent rendering, and launch transitions. T'Day identity comes from the Today accent, rounded typography, priority dots, compact task rows, and calm empty/setup states.

## Production Deployment

In production, `Dockerfile.backend` produces a single container:

1. **Stage 1** (Node 20): Builds `tday-web` static assets (`npm ci && npm run build`)
2. **Stage 2** (JDK 21): Builds Ktor fat JAR (`./gradlew :tday-backend:buildFatJar`)
3. **Stage 3** (JRE 21 Alpine): Runs the JAR with static files at `STATIC_FILES_DIR=/app/static`

One JVM process serves both the REST API and the SPA. Docker Compose orchestrates PostgreSQL and the backend container by default, with Ollama available through the optional `ai` profile.

## Future Considerations

- Keep ViewModel orchestration focused on presentation concerns; if shared mobile workflows become complex, prefer extracting them into repositories, platform services, or focused use cases before adding broad abstractions.
- Define an explicit import/export or migration experience before moving Local Mode data into a server workspace.
- Consider Redis or in-memory caching if web API latency becomes a concern under load.
- Evaluate SSE as an alternative to WebSocket for clients that don't need bidirectional streaming.

# Architecture

This document describes the high-level system design, domain boundaries, and key technical decisions for T'Day.

## System Overview

T'Day is a **monorepo application** with a Kotlin/Ktor backend, a React SPA frontend, shared Kotlin Multiplatform code, and native mobile clients:

```
┌─────────────────────────────────────────────────────────────┐
│                        Clients                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │  Web (React)  │  │ Android App  │  │    iOS App      │  │
│  │  Vite SPA     │  │ Compose/Hilt │  │    SwiftUI      │  │
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

### Shared Kotlin Multiplatform Module

The `shared/` module is a Kotlin Multiplatform (KMP) library targeting JVM, Android, and iOS. It provides a single source of truth for:

- Serializable DTOs (request/response models)
- Domain enums (`Priority`, `UserRole`, `ApprovalStatus`, `SortBy`, `GroupBy`, `ProjectColor`, etc.)
- Contract validators

| Consumer | Target | How it's consumed |
|----------|--------|-------------------|
| Backend (`tday-backend`) | JVM | Gradle `project(":shared")` |
| Android (`android-compose`) | Android | Gradle `project(":shared")` |
| iOS (`ios-swiftUI`) | iOS framework (`TdayShared`) | Swift Package import |

## Domain Model

The application is organized around these core domains:

| Domain | Description | Models |
|--------|------------|--------|
| **Auth** | Registration, login, sessions, approval, admin | `User`, `Account`, `AuthThrottle`, `AuthSignal` |
| **Todos** | Task CRUD, RFC 5545 recurrence, priorities, ordering | `Todo`, `TodoInstance`, `CompletedTodo` |
| **Lists** | Project grouping with colors and icons | `List` |
| **Notes** | Rich text notes (TipTap) | `Note` |
| **Files** | S3-backed file storage | `File` |
| **Preferences** | Sort/group/direction settings per user | `UserPreferences` |
| **Admin** | App configuration, user management | `AppConfig` |
| **Operations** | Cron jobs, event logging | `CronLog`, `eventLog` |

## Backend Architecture (Ktor)

### Request Flow

```
Client Request
    │
    ▼
Ktor Pipeline Intercept (Security.kt)
    ├── Read Bearer token or session cookie
    ├── Decode JWE → validate claims (expiry, tokenVersion)
    ├── Refresh role/approval from database
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
    ├── Validate input (Konform / shared validators)
    ├── Delegate to service layer
    └── Respond with JSON or WebSocket frames
    │
    ▼
Services (services/*.kt)
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
│   └── tables/             # Exposed Table definitions (Users, Todos, etc.)
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
│   ├── Security.kt         # JWE bearer + cookie auth, pipeline intercept
│   ├── SecurityHeaders.kt  # CSP, HSTS, X-Frame-Options, etc.
│   ├── Serialization.kt    # kotlinx.serialization JSON config
│   └── StatusPages.kt      # AppError → JSON ApiError mapping
├── routes/                 # HTTP route handlers by domain
│   └── auth/               # Auth-specific routes
├── security/               # JWT, passwords, throttling, captcha, encryption
└── services/               # Business logic (todo, list, note, user, etc.)
```

### Installed Ktor Plugins

| Plugin | Purpose |
|--------|---------|
| **Koin** | Dependency injection (config, security, service modules) |
| **WebSockets** | Real-time domain event streaming per authenticated user |
| **ContentNegotiation** | JSON request/response via kotlinx.serialization |
| **DefaultHeaders** | Security headers (CSP, HSTS in production, X-Frame-Options, etc.) |
| **StatusPages** | Maps `AppError` / generic errors to JSON `ApiError` responses |
| **Authentication** | Bearer token provider + pipeline intercept for JWE/cookie auth |
| **Routing** | API routes, health check, WebSocket, optional static SPA |

### Error Handling (Backend)

```
Route Handler
    │
    ├── withAuth { } → business logic
    │       │
    │       ├── Returns Either<AppError, T>
    │       └── AppError → ApiException with HTTP status
    │
    └── StatusPages plugin catches exceptions
            │
            ├── ApiException → JSON { message, code } + HTTP status
            └── Unknown error → 500 + generic message
```

Typed error hierarchy in `domain/AppError.kt` maps to HTTP status codes. Internal details are never exposed to clients.

## Web Architecture (Vite + React)

### Stack

- **React 18** with **TypeScript 5** bundled by **Vite 6**
- **React Router 7** with locale-prefixed URLs (`/:locale/app/*`)
- **TanStack React Query 5** for server state
- **Tailwind CSS 4** with Radix UI primitives (shadcn-style)
- Native `fetch` API client with cookie-based auth

### Directory Structure

```
tday-web/src/
├── main.tsx              # React DOM entry point
├── App.tsx               # Provider tree: Theme → Query → Auth → Tooltip → Router
├── router.tsx            # Route definitions (public, protected, calendar layouts)
├── globals.css           # Tailwind @theme tokens, CSS variables
├── i18n.ts               # i18next configuration (11 locales, path-based detection)
├── components/           # Shared UI (ui/* primitives, Sidebar, auth, todo pieces)
├── features/             # Feature modules (calendar, list, todayTodos, completed, notes)
├── hooks/                # Shared React hooks
├── lib/                  # Utilities (api-client, navigation, security, dates)
├── pages/                # Route-level screens and layouts
├── providers/            # React context providers (Auth, Theme, Query, Menu, etc.)
└── types/                # TypeScript type definitions
```

### State Management (Web)

- **Server state**: TanStack React Query (`useQuery`, `useMutation`) for all API-fetched data. Default `staleTime: 60_000`.
- **Auth state**: Custom `AuthProvider` context managing session fetch/refresh from `/api/auth/session`.
- **UI state**: React `useState`/`useReducer` within components. React Context for cross-cutting state (menu, preferences, todo form/mutations).
- **Theme**: `next-themes` provider with system/light/dark modes via CSS `class` strategy.

### API Communication (Web)

The web SPA communicates with the Ktor backend via a thin `fetch` wrapper:

- `api.GET/POST/PATCH/DELETE` functions in `lib/api-client.ts`
- `credentials: "same-origin"` for cookie-based sessions
- `Cache-Control: no-store` on all requests
- Dev: Vite proxy forwards `/api` and `/ws` to `http://localhost:8080`
- Production: Ktor serves the SPA as static files from `STATIC_FILES_DIR`

## Android Architecture

### Pattern: MVVM with Single Repository

```
┌───────────────────────────────────────────────────┐
│  Composable Screens (UI Layer)                    │
│  Observe StateFlow → render → invoke ViewModel    │
└──────────────────────┬────────────────────────────┘
                       │
┌──────────────────────┴────────────────────────────┐
│  @HiltViewModel classes (Presentation Layer)      │
│  MutableStateFlow + viewModelScope.launch         │
└──────────────────────┬────────────────────────────┘
                       │
┌──────────────────────┴────────────────────────────┐
│  TdayRepository (Data Layer)                      │
│  API calls, offline cache, sync, mapping          │
└──────┬───────────────────────────────┬────────────┘
       │                               │
┌──────┴──────┐              ┌─────────┴─────────┐
│ Retrofit    │              │ EncryptedPrefs     │
│ (Network)   │              │ (Local Cache)      │
└─────────────┘              └───────────────────┘
```

### Key Design Decisions (Android)

- **Single fat repository**: `TdayRepository` is the sole data layer facade. All network calls, local cache operations, and domain mapping pass through it.
- **Offline-first sync**: Local cache (`OfflineSyncState` serialized to encrypted prefs) with a pending mutation queue. Background sync loop in `AppViewModel` periodically pushes mutations and pulls fresh data.
- **Cache invalidation**: `cacheDataVersion` `StateFlow<Long>` in the repository — ViewModels observe it and reload when incremented.
- **Auth compatibility**: The Android client implements the JWE credential flow (CSRF token fetch → credential callback → session cookie) using Retrofit + an encrypted cookie store.
- **Navigation**: Programmatic Compose Navigation (`NavHost`) with `sealed class AppRoute` — no XML navigation graphs.
- **Server discovery**: Runtime server URL configuration with optional certificate fingerprint pinning for self-hosted instances.

### Package Structure (Android)

```
com.ohmz.tday.compose/
├── core/
│   ├── data/          # TdayRepository, SecureConfigStore, ThemePreferenceStore
│   ├── model/         # ApiModels (DTOs), DomainModels (UI types)
│   ├── navigation/    # AppRoute sealed class
│   ├── network/       # Hilt NetworkModule, TdayApiService, EncryptedCookieStore
│   └── notification/  # Alarms, WorkManager, receivers
├── feature/
│   ├── app/           # AppViewModel (bootstrap, sync, session)
│   ├── auth/          # AuthViewModel
│   ├── home/          # HomeScreen + HomeViewModel
│   ├── todos/         # TodoListScreen + TodoListViewModel
│   ├── completed/     # CompletedScreen + CompletedViewModel
│   ├── calendar/      # CalendarScreen + CalendarViewModel
│   ├── notes/         # NotesScreen + NotesViewModel
│   ├── settings/      # SettingsScreen
│   └── onboarding/    # OnboardingWizardOverlay
└── ui/
    ├── component/     # Shared composables (PullRefresh, CreateTaskBottomSheet)
    └── theme/         # Material 3 theme, colors, typography, dimensions
```

## iOS Architecture

### Stack

- **SwiftUI** targeting iOS 17+, managed via Swift Package Manager
- **SwiftData** for local persistence
- Feature-based folder structure with `AppRootView` using TabView + NavigationStack

### Structure

```
ios-swiftUI/Tday/
├── Feature/
│   ├── Home/         # Home screen
│   ├── Todos/        # Todo management
│   ├── Calendar/     # Calendar views
│   ├── Completed/    # Completion history
│   ├── Settings/     # User settings
│   ├── Auth/         # Login/register
│   └── Onboarding/   # First-launch flow
├── Core/
│   ├── Network/      # TdayAPIService, RealtimeClient (URLSession + cookies)
│   └── Data/         # Repositories, SwiftData models
└── AppRootView.swift # TabView + NavigationStack with AppRoute destinations
```

No third-party Swift dependencies — all native frameworks.

## Database Design

### Entity Relationships

```
User ──┬── Todo ──── TodoInstance
       │      └──── List (Project)
       ├── CompletedTodo
       ├── Note
       ├── File
       ├── UserPreferences
       ├── Account (OAuth)
       └── (approved by) User
```

### ORM and Migrations

- **ORM**: JetBrains Exposed 0.57.0 (DSL/Table API with `transaction {}` blocks)
- **Connection pool**: HikariCP 6.2.1 (max 10 connections, auto-commit off)
- **Migrations**: Flyway 10.22.0 with `baselineOnMigrate=true`. Migration files live in `tday-backend/src/main/resources/db/migration/`.
- **Driver**: PostgreSQL JDBC 42.7.4

### PostgreSQL Enums

`UserRole`, `ApprovalStatus`, `SortBy`, `GroupBy`, `Direction`, `Priority`, `ProjectColor` — defined as Kotlin enums in `shared/` and mapped to PostgreSQL enums via `PgEnums.kt`.

### Key Patterns

- **Soft completion**: Completed todos are moved to `CompletedTodo` with metadata (completion time, on-time status, days to complete).
- **RFC 5545 recurrence**: Todos support `rrule`, `dtstart`, `due`, `exdates`, and `durationMinutes`. Instances are materialized in `TodoInstance` for per-occurrence overrides.
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

### Android Auth Flow

```
App launch → Probe server (GET /api/mobile/probe)
           → Optional cert fingerprint check
           → GET /api/auth/csrf
           → POST /api/auth/callback/credentials (with CSRF + encrypted envelope)
           → Session cookie stored in EncryptedCookieStore
           → Subsequent requests include cookie automatically
```

## Real-Time Communication

The backend exposes a `WS /ws` WebSocket endpoint for authenticated users. Domain events (`DomainEvent` sealed class) are streamed as JSON frames — covering todo, list, and note changes. Each user has their own WebSocket channel.

## Internationalization

- 11 locales: `en`, `zh`, `de`, `ja`, `ar`, `ru`, `es`, `fr`, `ms`, `it`, `pt`.
- Managed via **i18next** + **react-i18next** with path-based locale detection.
- Locale strings stored in `tday-web/messages/*.json`.
- Routes are prefixed with `/:locale/` via React Router.

## AI Integration

- **Ollama** provides local LLM inference for task summaries.
- Model: `qwen2.5:0.5b` (small, fast).
- Feature is toggled globally via `AppConfig.aiSummaryEnabled` (admin setting).
- Timeout-protected with `OLLAMA_TIMEOUT_MS`.

## Caching Strategy

- **Web**: No application-level cache layer. API requests use `Cache-Control: no-store`. TanStack React Query provides client-side cache with 60-second stale time.
- **Android**: Offline JSON cache in encrypted shared preferences. Cache is the source of truth for list/todo screens; network sync updates the cache periodically and on pull-to-refresh.

## Background Jobs

- **Cron job**: Scheduled GitHub Actions workflow calls `GET /api/cron/rescheduleTodo` with a secret header for periodic task maintenance.
- **Android reminders**: `AlarmManager` for exact-time reminders, `WorkManager` for periodic reminder rescheduling, `BootRescheduleReceiver` for device restart recovery.

## Production Deployment

In production, `Dockerfile.backend` produces a single container:

1. **Stage 1** (Node 20): Builds `tday-web` static assets (`npm ci && npm run build`)
2. **Stage 2** (JDK 21): Builds Ktor fat JAR (`./gradlew :tday-backend:buildFatJar`)
3. **Stage 3** (JRE 21 Alpine): Runs the JAR with static files at `STATIC_FILES_DIR=/app/static`

One JVM process serves both the REST API and the SPA. Docker Compose orchestrates PostgreSQL, Ollama, and the backend container.

## Future Considerations

- Extract `TdayRepository` into smaller domain-specific repositories as the Android app grows.
- Add a domain/use-case layer in Android if business logic exceeds simple CRUD.
- Consider Redis or in-memory caching if web API latency becomes a concern under load.
- Evaluate SSE as an alternative to WebSocket for clients that don't need bidirectional streaming.

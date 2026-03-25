# Architecture

This document describes the high-level system design, domain boundaries, and key technical decisions for T'Day.

## System Overview

T'Day is a **monolithic full-stack application** with a separate native mobile client:

```
┌─────────────────────────────────────────────────────────────┐
│                        Clients                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │  Web (React)  │  │ Android App  │  │ Future: iOS/PWA  │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────────────┘  │
│         │                 │                                  │
└─────────┼─────────────────┼──────────────────────────────────┘
          │ HTTPS           │ HTTPS (cookie-based)
          ▼                 ▼
┌─────────────────────────────────────────────────────────────┐
│                   Next.js App (Node.js)                     │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────────┐ │
│  │ Middleware│→ │  App Router   │→ │  API Route Handlers   │ │
│  │ (Edge)   │  │  (SSR/CSR)    │  │  (Server)             │ │
│  └──────────┘  └──────────────┘  └───────────┬───────────┘ │
│                                               │             │
│  ┌──────────────────┐  ┌─────────────────────┐│            │
│  │  NextAuth v5      │  │  Prisma ORM         ││            │
│  │  (JWT sessions)   │  │  (query builder)    ││            │
│  └──────────────────┘  └─────────┬───────────┘│            │
└──────────────────────────────────┼─────────────┘            │
                                   ▼                          │
                          ┌─────────────────┐  ┌─────────────┐
                          │  PostgreSQL 15   │  │  Ollama     │
                          │  (data store)    │  │  (local AI) │
                          └─────────────────┘  └─────────────┘
```

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

## Web Architecture

### Request Flow

```
Client Request
    │
    ▼
Edge Middleware (middleware.ts)
    ├── HTTPS enforcement (production)
    ├── API auth (JWT validation, approval gate)
    ├── App route protection (redirect to login)
    ├── Internationalization (next-intl locale routing)
    └── Security headers (CSP, HSTS, CORP, etc.)
    │
    ▼
App Router
    ├── app/[locale]/*          → SSR/CSR pages
    ├── app/api/*               → REST route handlers
    └── app/api/auth/[...nextauth] → NextAuth handlers
    │
    ▼
Business Logic (lib/, features/)
    │
    ▼
Prisma ORM → PostgreSQL
```

### Directory Responsibilities

| Directory | Responsibility | Layer |
|-----------|---------------|-------|
| `app/api/` | HTTP route handlers — parse requests, call business logic, return responses | Presentation |
| `lib/` | Shared server utilities — Prisma client, security, dates, NLP, error handling | Infrastructure |
| `features/` | Feature-scoped client data fetching (React Query hooks and mutations) | Client data |
| `components/` | React UI components — shared primitives (`ui/`), domain components (`todo/`, `Sidebar/`) | Presentation |
| `providers/` | React context providers — theme, query client, menus, preferences | Client infrastructure |
| `hooks/` | Shared React hooks | Client infrastructure |
| `i18n/`, `messages/` | Internationalization routing, request config, and locale strings | Cross-cutting |
| `prisma/` | Database schema and migrations | Data |

### State Management (Web)

- **Server state**: TanStack React Query (`useQuery`, `useMutation`) for all API-fetched data.
- **Auth state**: NextAuth `SessionProvider` with JWT tokens.
- **UI state**: React `useState`/`useReducer` within components; no global UI state store.
- **Theme**: `next-themes` provider with system/light/dark modes.

### Error Handling (Web)

```
API Route Handler
    │
    ├── try { business logic }
    │
    └── catch (error) → errorHandler(error)
                            │
                            ├── BaseServerError instance → JSON { message } + HTTP status
                            └── Unknown error → 500 + generic message
```

Custom error classes (`lib/customError.ts`): `BadRequestError`, `UnauthorizedError`, `ForbiddenError`, `NotFoundError`, etc. Each carries an HTTP status code.

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
- **NextAuth compatibility**: The Android client implements the full NextAuth credential flow (CSRF token fetch → credential callback → session cookie) using Retrofit + an encrypted cookie store.
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
    └── theme/         # Material 3 theme, colors, typography
```

## Database Design

### Entity Relationships

```
User ──┬── Todo ──── TodoInstance
       │      └──── List
       ├── CompletedTodo
       ├── Note
       ├── File
       ├── UserPreferences
       ├── Account (OAuth)
       └── (approved by) User
```

### Key Patterns

- **Soft completion**: Completed todos are moved to `CompletedTodo` with metadata (completion time, on-time status, days to complete).
- **RFC 5545 recurrence**: Todos support `rrule`, `dtstart`, `due`, `exdates`, and `durationMinutes`. Instances are materialized in `TodoInstance` for per-occurrence overrides.
- **Tenant isolation**: All data queries filter by `userID`. There are no shared/public data models.
- **Audit fields**: All major models include `createdAt` and `updatedAt`.

## Authentication Flow

### Web (NextAuth Credentials)

```
Client → GET /api/auth/csrf (fetch CSRF token)
       → POST /api/auth/callback/credentials
              │
              ├── Rate limit check (AuthThrottle)
              ├── Optional CAPTCHA verification
              ├── Optional credential envelope decryption
              ├── PBKDF2 password verification (or rehash if iterations changed)
              ├── Approval status check
              └── JWT issued → set as HTTP-only cookie
```

### Android (NextAuth-compatible)

```
App launch → Probe server (/api/mobile/probe)
           → Optional cert fingerprint check
           → GET /api/auth/csrf
           → POST /api/auth/callback/credentials (with CSRF + encrypted envelope)
           → Session cookie stored in EncryptedCookieStore
           → Subsequent requests include cookie automatically
```

## Internationalization

- 11 locales: `en`, `zh`, `de`, `ja`, `ar`, `ru`, `es`, `fr`, `ms`, `it`, `pt`.
- Managed via `next-intl` with locale-prefixed routing (`/[locale]/...`).
- Locale strings stored in `messages/*.json`.
- Middleware detects and routes to the user's preferred locale.

## AI Integration

- **Ollama** provides local LLM inference for task summaries.
- Model: `qwen2.5:0.5b` (small, fast).
- Feature is toggled globally via `AppConfig.aiSummaryEnabled` (admin setting).
- Timeout-protected with `OLLAMA_TIMEOUT_MS`.

## Caching Strategy

- **Web**: No application-level cache layer. API responses include `Cache-Control: no-store` for private data. Static assets use Next.js default caching.
- **Android**: Offline JSON cache in encrypted shared preferences. Cache is the source of truth for list/todo screens; network sync updates the cache periodically and on pull-to-refresh.

## Background Jobs

- **Cron job**: Scheduled GitHub Actions workflow calls `GET /api/cron/rescheduleTodo` with a secret header for periodic task maintenance.
- **Android reminders**: `AlarmManager` for exact-time reminders, `WorkManager` for periodic reminder rescheduling, `BootRescheduleReceiver` for device restart recovery.

## Future Considerations

- Extract `TdayRepository` into smaller domain-specific repositories as the Android app grows.
- Add a domain/use-case layer in Android if business logic exceeds simple CRUD.
- Consider Redis or in-memory caching if web API latency becomes a concern under load.
- Evaluate WebSocket or SSE for real-time sync between web and mobile clients.

# API Guidelines

Conventions for the T'Day REST API served by the Ktor backend.

## Base URL

All API routes live under `/api/`. The web SPA consumes them via same-origin requests (Vite proxy in development, same container in production). The Android and iOS clients target them at the user-configured server URL.

## Authentication

- All routes require a valid JWE session unless listed as public.
- Public routes: `/api/auth/*` (CSRF, register, login-challenge, credentials-key, callback), `/api/mobile/probe`, `/health`.
- Authentication is enforced by a **Ktor pipeline intercept** in `Security.kt`:
  1. Reads a JWE token from `Authorization: Bearer` header or session cookies.
  2. Decodes and validates claims (expiry, `tokenVersion`, role, approval status).
  3. Attaches `AuthUser` to the call attributes.
- Route handlers use `call.withAuth { }` to require an authenticated user.
- Admin routes additionally require `role == ADMIN` and `approvalStatus == APPROVED` via `requireAdminEither`.

## Request Format

### Content Type

- Request bodies use `application/json`.
- Serialization is handled by Ktor's `ContentNegotiation` plugin with `kotlinx.serialization`.

### Validation

- Validate incoming request bodies using **Konform** validators and **shared model validators** from the `shared` KMP module.
- Throw typed `AppError` variants for invalid input (maps to `400 Bad Request`).
- Never trust client input without validation.

```kotlin
val validated = validateTodoRequest(request)
if (validated is Invalid) {
    return AppError.BadRequest(validated.errors.joinToString()).left()
}
```

### Query Parameters

- Use for filtering, sorting, and pagination.
- Access via `call.request.queryParameters["key"]`.

## Response Format

### Success Responses

Return JSON with appropriate HTTP status:

```json
// Single resource
{
  "id": "clx...",
  "title": "Buy groceries",
  "priority": "Medium"
}

// Collection
[
  { "id": "clx...", "title": "Buy groceries" },
  { "id": "cly...", "title": "Walk the dog" }
]

// Action confirmation
{
  "message": "Todo completed successfully"
}
```

### Error Responses

Always return a JSON object with a `message` field:

```json
{
  "message": "Todo not found"
}
```

Error responses are produced by the `StatusPages` plugin from thrown `ApiException` instances, which are derived from typed `AppError` sealed variants.

## HTTP Status Codes

### Success

| Code | Usage |
|------|-------|
| `200 OK` | Successful GET, PATCH, PUT, DELETE |
| `201 Created` | Successful POST that creates a resource |

### Client Errors

| Code | Usage |
|------|-------|
| `400 Bad Request` | Invalid input, malformed JSON, failed validation |
| `401 Unauthorized` | Missing or invalid session |
| `403 Forbidden` | Authenticated but lacking permission (pending approval, non-admin) |
| `404 Not Found` | Resource does not exist or does not belong to the user |
| `409 Conflict` | Duplicate resource or version conflict |
| `429 Too Many Requests` | Rate limit exceeded |

### Server Errors

| Code | Usage |
|------|-------|
| `500 Internal Server Error` | Unhandled exceptions (generic message returned to client) |

## HTTP Methods

| Method | Purpose | Idempotent |
|--------|---------|-----------|
| `GET` | Retrieve resources | Yes |
| `POST` | Create a new resource | No |
| `PATCH` | Partial update of an existing resource | Yes |
| `PUT` | Full replacement of an existing resource | Yes |
| `DELETE` | Remove a resource | Yes |

- Prefer `PATCH` over `PUT` for updates.
- `DELETE` should be idempotent — deleting a non-existent resource returns 200 or 404, never 500.

## Route Handler Pattern

Route handlers delegate to services and use the `withAuth` helper for authentication:

```kotlin
route("/api/todo") {
    post {
        call.withAuth { user ->
            val request = call.receive<CreateTodoRequest>()
            todoService.create(user.id, request)
                .fold(
                    { error -> call.respondError(error) },
                    { todo -> call.respond(HttpStatusCode.Created, todo) }
                )
        }
    }

    get {
        call.withAuth { user ->
            val todos = todoService.listForUser(user.id, call.request.queryParameters)
            call.respond(HttpStatusCode.OK, todos)
        }
    }
}
```

Services return `Either<AppError, T>` (Arrow) for typed error handling. Routes fold the result into HTTP responses.

## Tenant Isolation

- **Every** data query must filter by `userID` from the authenticated session.
- Never return data belonging to other users.
- Admin endpoints that access other users' data must be behind `requireAdminEither`.

## Existing API Surface

### Health and Infrastructure

| Method | Path | Purpose | Auth |
|--------|------|---------|------|
| GET | `/health` | Health check (`{ status: "ok" }`) | Public |
| WS | `/ws` | WebSocket for real-time domain events | Required |

### Auth

| Method | Path | Purpose | Auth |
|--------|------|---------|------|
| GET | `/api/auth/csrf` | Fetch CSRF token | Public |
| POST | `/api/auth/register` | User registration | Public |
| POST | `/api/auth/login-challenge` | Password-proof challenge | Public |
| GET | `/api/auth/credentials-key` | Public key for credential envelope encryption | Public |
| POST | `/api/auth/callback/credentials` | Login (plain or encrypted envelope) | Public |
| GET | `/api/auth/session` | Get current session | Required |
| POST | `/api/auth/logout` | Logout (invalidate session) | Required |

### Todos

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/todo` | List todos (query: `start`/`end`, `timeline`, `recurringFutureDays`) |
| POST | `/api/todo` | Create a new todo |
| PATCH | `/api/todo` | Update a todo |
| DELETE | `/api/todo` | Delete a todo |
| PATCH | `/api/todo/complete` | Mark todo complete |
| PATCH | `/api/todo/uncomplete` | Mark todo incomplete |
| PATCH | `/api/todo/prioritize` | Change priority |
| PATCH | `/api/todo/reorder` | Reorder todos |
| PATCH | `/api/todo/instance` | Update a recurring instance |
| DELETE | `/api/todo/instance` | Delete a recurring instance |
| GET | `/api/todo/overdue` | List overdue todos |
| POST | `/api/todo/nlp` | Natural language date/title parsing |
| POST | `/api/todo/summary` | AI-powered task summary |

### Lists

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/list` | List all lists |
| POST | `/api/list` | Create a list |
| PATCH | `/api/list` | Update a list |
| DELETE | `/api/list` | Delete a list |
| GET | `/api/list/{id}` | Get list with its todos |

### Notes

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/note` | List notes |
| POST | `/api/note` | Create a note |
| PATCH | `/api/note/{id}` | Update a note |
| DELETE | `/api/note/{id}` | Delete a note |

### User

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/user` | Get current user profile |
| PATCH | `/api/user` | Update user (encryption settings) |
| PATCH | `/api/user/profile` | Update profile |
| POST | `/api/user/change-password` | Change password |

### Admin

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/admin/settings` | Get app configuration + Ollama health |
| PATCH | `/api/admin/settings` | Update app configuration |
| GET | `/api/admin/users` | List all users |
| PATCH | `/api/admin/users/{id}` | Update user (approve, change role) |
| DELETE | `/api/admin/users/{id}` | Delete user and related data |

### Preferences

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/preferences` | Get user preferences |
| PATCH | `/api/preferences` | Update preferences |

### Completed Todos

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/completedTodo` | List completed todos |
| DELETE | `/api/completedTodo` | Delete all completed todos |
| PATCH | `/api/completedTodo` | Remove a single completed todo |

### Timezone

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/timezone` | Get/detect timezone (query or header) |

### App Settings

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/app-settings` | Get public app settings (AI summary enabled) |

### Mobile

| Method | Path | Purpose | Auth |
|--------|------|---------|------|
| GET | `/api/mobile/probe` | Server discovery | Public |

### Cron

| Method | Path | Purpose | Auth |
|--------|------|---------|------|
| GET | `/api/cron/rescheduleTodo` | Reschedule overdue recurring tasks | `X-CronSecret` header |

## Cache Headers

- Private API responses include `Cache-Control: no-store` (set by the web API client).
- Security headers (CSP, HSTS, etc.) are applied by the Ktor `SecurityHeaders` plugin.
- Static SPA assets are served from the filesystem when `STATIC_FILES_DIR` is set.

## Adding a New Endpoint

1. Add a route function in `routes/<domain>.kt` (or create a new file for a new domain).
2. Use `call.withAuth { }` for authenticated routes.
3. Validate input using Konform validators or shared model validation.
4. Delegate to a service in `services/`.
5. Filter data by `userID` for tenant isolation.
6. Use appropriate HTTP status codes.
7. Add tests in `tday-backend/src/test/kotlin/` if the endpoint involves security or complex logic.
8. Update this document with the new route.

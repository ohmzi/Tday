# API Guidelines

Conventions for the T'Day REST API served by the Ktor backend. Keep this file aligned with `shared/`, backend routes, mobile Retrofit/URLSession clients, and [`DATA_MODEL.md`](DATA_MODEL.md).

## Base URL

All API routes live under `/api/`. The web SPA consumes them via same-origin requests (Vite proxy in development, same container in production). Android and iOS clients target them at the user-configured server URL in Server Mode. Local Mode does not call the API.

## Authentication

- All routes require a valid JWE session unless listed as public.
- Public routes: `/api/auth/*` (CSRF, register, login-challenge, credentials-key, callback), `/api/mobile/probe`, `/.well-known/apple-app-site-association`, `/apple-app-site-association`, `/.well-known/assetlinks.json`, `/health`.
- Authentication is enforced by a **Ktor pipeline intercept** in `Security.kt`:
  1. Reads a JWE token from `Authorization: Bearer` header or session cookies.
  2. Decodes and validates claims (expiry, `tokenVersion`, role, approval status).
  3. Attaches `AuthUser` to the call attributes.
- Route handlers use `call.withAuth { }` to require an authenticated, approved user.
- Admin routes additionally require `role == ADMIN` and `approvalStatus == APPROVED` via `requireAdminAccess()`.
- App-layer request throttling runs before handlers and may return `429` on `/api/**`, `/health`, `/api/mobile/probe`, `POST /api/todo/summary`, `POST /api/user/change-password`, and `/ws`.

## Request Format

### Content Type

- Request bodies use `application/json`.
- Serialization is handled by Ktor's `ContentNegotiation` plugin with `kotlinx.serialization`.

### Validation

- Validate incoming request bodies using **Konform** validators from `domain/Validations.kt` with the `validateOrFail()` helper.
- Return typed `AppError` variants via `Either.Left` for invalid input (maps to `400 Bad Request`).
- Validate string-backed enum fields before service calls. Invalid enum values such as priority, list color, and preference sort/group/direction must return `400`, not a generic `500`.
- Never trust client input without validation.

```kotlin
either {
    val body = call.receive<TodoCreateRequest>()
    validateCreateTodo.validateOrFail(body).bind()
    todoService.create(user.id, body.title, ...).bind()
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

Always return a JSON object with a `message` field. Validation errors may include `field`; throttled responses include `reason` and `retryAfterSeconds`:

```json
{
  "code": 400,
  "message": "priority is invalid",
  "field": "priority"
}
```

Malformed JSON/request bodies return `400` with `message: "Invalid request body"`. Error responses are produced by `respondAppError()` from the `withAuth` helper, or by `StatusPages` for malformed requests and unhandled exceptions. The legacy `ApiException` hierarchy is deprecated — new code should use `Either<AppError, T>` exclusively.

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

Shared route constants live in `shared/src/commonMain/kotlin/com/ohmz/tday/shared/routes/ApiRoutes.kt`. Add or update those constants with backend route changes so backend, Android, and iOS have one contract reference point.

## Tenant Isolation

- **Every** data query must filter by `userID` from the authenticated session.
- Never return data belonging to other users.
- Admin endpoints that access other users' data must be behind `requireAdminAccess()`.

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
| POST | `/api/todo/summary` | Task summary with optional AI and logic fallback |

### Floaters

Floaters are unscheduled Anytime tasks. They are not scheduled todos with a nullable due date.

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/floater` | List all active floaters |
| POST | `/api/floater` | Create a floater |
| PATCH | `/api/floater` | Update a floater |
| DELETE | `/api/floater` | Delete a floater |
| PATCH | `/api/floater/complete` | Complete a floater |
| PATCH | `/api/floater/uncomplete` | Restore a completed floater to active |
| PATCH | `/api/floater/prioritize` | Change floater priority |
| PATCH | `/api/floater/reorder` | Reorder floaters |

### Lists

Lists group scheduled tasks.

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/list` | List all lists |
| POST | `/api/list` | Create a list |
| PATCH | `/api/list` | Update a list |
| DELETE | `/api/list` | Delete a list |
| GET | `/api/list/{id}` | Get list with its todos |

### Floater Lists

Floater lists group floaters.

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/floaterList` | List all floater lists |
| POST | `/api/floaterList` | Create a floater list |
| PATCH | `/api/floaterList` | Update a floater list |
| DELETE | `/api/floaterList` | Delete one or many floater lists |
| GET | `/api/floaterList/{id}` | Get floater list with its floaters |

### User

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/user` | Get current user profile |
| PATCH | `/api/user` | Update user (encryption settings) |
| PATCH | `/api/user/profile` | Update profile |
| POST | `/api/user/change-password` | Change password |
| GET | `/api/user/security-questions` | Get the user's chosen question ids + whether they still need to be set (`requireSecurityQuestions`) |
| POST | `/api/user/security-questions` | Set/replace the user's security questions (exactly 3 distinct). When the account already has questions configured (`requireSecurityQuestions = false`), the body must include a valid `currentPassword` — otherwise `400 "current password is required"` / `"current password is incorrect"`. The first-time setup gate (`requireSecurityQuestions = true`) omits the password. |

### Admin

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/admin/settings` | Get app configuration and Summary availability |
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
| DELETE | `/api/completedTodo` | Delete all completed todos, or delete one when an `id` body is supplied |
| PATCH | `/api/completedTodo` | Update a completed todo, or remove it when no update fields are supplied |

### Completed Floaters

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/completedFloater` | List completed floaters |
| DELETE | `/api/completedFloater` | Delete all or one completed floater |
| PATCH | `/api/completedFloater` | Update or remove a completed floater |

### Timezone

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/timezone` | Get/detect timezone using `timezone`, `X-Timezone`, or `X-User-Timezone` |

### App Settings

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/app-settings` | Get public app settings (Summary enabled) |

### Mobile

| Method | Path | Purpose | Auth |
|--------|------|---------|------|
| GET | `/api/mobile/probe` | Server discovery, compatibility/version metadata, optional encrypted probe payload | Public |

`GET /api/mobile/probe` returns the public probe contract:

- `service: "tday"`, `probe: "ok"`, `version: "1"`, and `serverTime`.
- `appVersion` with the backend's T'Day release version; mobile App Version screens use this to display the server version even when encrypted compatibility metadata is unavailable.
- `encryptedCompatibility` when mobile compatibility enforcement is configured; Android and iOS decrypt it to enforce app/server version compatibility.

When exact compatibility is enabled, Android and iOS send `X-Tday-Client` and
`X-Tday-App-Version` on API requests. The backend leaves `/api/mobile/probe`,
web requests, and requests without mobile client headers unaffected, but rejects
mismatched mobile API requests with:

- `426 Upgrade Required` + `reason: "app_update_required"` when the app is older than the server-compatible version.
- `409 Conflict` + `reason: "server_update_required"` when the app is newer than the server-compatible version.

### App Association Files

| Method | Path | Purpose | Auth |
|--------|------|---------|------|
| GET | `/.well-known/apple-app-site-association` | iOS webcredentials/deep-link association | Public |
| GET | `/apple-app-site-association` | iOS association fallback path | Public |
| GET | `/.well-known/assetlinks.json` | Android app links association | Public |

## Cache Behavior

- The web API client sends private API requests with `cache: "no-store"` so browser fetch caching does not serve stale authenticated data.
- Routes that return compatibility or discovery metadata, such as `/api/mobile/probe`, should set explicit `Cache-Control: no-store` response headers.
- Security headers (CSP, HSTS, etc.) are applied by the Ktor `SecurityHeaders` plugin.
- Static SPA assets are served from the filesystem when `STATIC_FILES_DIR` is set.

## Adding a New Endpoint

1. Add or update shared request/response models in `shared/` when the endpoint is consumed outside the backend.
2. Add a route function in `routes/<domain>.kt` (or create a new file for a new domain).
3. Use `call.withAuth { }` for authenticated routes.
4. Validate input using Konform validators or shared model validation.
5. Delegate to a service in `services/`.
6. Filter data by `userID` for tenant isolation.
7. Use appropriate HTTP status codes.
8. Update Android Retrofit and iOS URLSession clients when mobile consumes it.
9. Update local cache/sync models if the route changes mobile persisted data.
10. Add tests in `tday-backend/src/test/kotlin/` if the endpoint involves security or complex logic.
11. Update this document with the new route.

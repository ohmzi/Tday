# API Guidelines

Conventions for the T'Day REST API served by Next.js App Router route handlers.

## Base URL

All API routes live under `/api/`. The web app consumes them via same-origin requests. The Android client targets them at the user-configured server URL.

## Authentication

- All routes require a valid JWT session unless listed as public.
- Public routes: `/api/auth/*`, `/api/mobile/probe`.
- Authentication is enforced at two layers:
  1. **Edge middleware** validates the JWT and checks approval status before the request reaches the handler.
  2. **Route handlers** call `auth()` or `requireAdmin()` for additional checks.
- Admin routes additionally require `role === "ADMIN"`.

## Request Format

### Content Type

- Request bodies use `application/json`.
- File uploads (if any) use `multipart/form-data`.

### Validation

- Validate all incoming request bodies with **Zod schemas**.
- Use `safeParse` and throw `BadRequestError` on invalid input.
- Never trust client input without validation.

```typescript
const schema = z.object({
  title: z.string().min(1).max(500),
  priority: z.enum(["Low", "Medium", "High"]).optional(),
});

const parsed = schema.safeParse(await req.json());
if (!parsed.success) {
  throw new BadRequestError("Invalid request body");
}
```

### Path Parameters

- Use dynamic route segments: `app/api/todo/[id]/route.ts`.
- Validate IDs before database queries.

### Query Parameters

- Use for filtering, sorting, and pagination.
- Validate with Zod or manual checks.

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

Error responses are produced by `errorHandler` from thrown `BaseServerError` subclasses.

## HTTP Status Codes

### Success

| Code | Usage |
|------|-------|
| `200 OK` | Successful GET, PATCH, PUT, DELETE |
| `201 Created` | Successful POST that creates a resource |

### Client Errors

| Code | Usage |
|------|-------|
| `400 Bad Request` | Invalid input, malformed JSON, failed Zod validation |
| `401 Unauthorized` | Missing or invalid session (also set by middleware) |
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

Every route handler follows this structure:

```typescript
import { NextResponse } from "next/server";
import { auth } from "@/app/auth";
import { errorHandler } from "@/lib/errorHandler";

export async function GET(req: Request) {
  try {
    const session = await auth();
    if (!session?.user?.id) {
      throw new UnauthorizedError("Not authenticated");
    }

    const data = await prisma.todo.findMany({
      where: { userID: session.user.id },
    });

    return NextResponse.json(data, { status: 200 });
  } catch (error) {
    return errorHandler(error);
  }
}
```

## Tenant Isolation

- **Every** data query must filter by `userID` from the authenticated session.
- Never return data belonging to other users.
- Admin endpoints that access other users' data must be behind `requireAdmin()`.

## Existing API Surface

### Auth

| Method | Path | Purpose |
|--------|------|---------|
| GET/POST | `/api/auth/[...nextauth]` | NextAuth handlers (CSRF, session, callbacks) |
| POST | `/api/auth/register` | User registration |
| GET | `/api/auth/credentials-key` | Public key for credential envelope encryption |
| POST | `/api/auth/login-challenge` | Password-proof challenge (non-WebCrypto fallback) |

### Todos

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/todo` | List todos for current user |
| POST | `/api/todo` | Create a new todo |
| PATCH | `/api/todo` | Update a todo |
| DELETE | `/api/todo` | Delete a todo |
| GET | `/api/completedTodo` | List completed todos |

### Lists

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/list` | List all lists |
| POST | `/api/list` | Create a list |
| PATCH | `/api/list` | Update a list |
| DELETE | `/api/list` | Delete a list |

### Notes

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/note` | List notes |
| POST | `/api/note` | Create a note |
| PATCH | `/api/note` | Update a note |
| DELETE | `/api/note` | Delete a note |

### User

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/user` | Get current user profile |
| PATCH | `/api/user/profile` | Update profile |
| POST | `/api/user/change-password` | Change password |

### Admin

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/admin/settings` | Get app configuration |
| PATCH | `/api/admin/settings` | Update app configuration |
| GET | `/api/admin/users` | List all users |
| PATCH | `/api/admin/users/[id]` | Update user (approve, change role) |

### Mobile

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/mobile/probe` | Server discovery (public, no auth) |

### Preferences

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/preferences` | Get user preferences |
| PATCH | `/api/preferences` | Update preferences |

## Cache Headers

- Private API responses include `Cache-Control: no-store, no-cache, must-revalidate` (set by middleware).
- Public endpoints (probe, auth CSRF) use default caching.
- Static assets follow Next.js default cache behavior.

## Adding a New Endpoint

1. Create `app/api/<domain>/<resource>/route.ts`.
2. Import `auth` and `errorHandler`.
3. Validate input with Zod.
4. Filter data by `userID`.
5. Use appropriate HTTP status codes.
6. Add tests in `tests/` if the endpoint involves security or complex logic.
7. Update this document with the new route.

# API Integration (API keys)

T'Day exposes a **token-authenticated REST API** so external tools — dashboards, scripts, home-lab
automations, or the [Homarr "Tday Tasks" widget](#reference-the-homarr-tday-tasks-widget) — can read
and modify your tasks **as you**, without a browser session. The token acts on the same data model
the apps use; there is no separate "integration backend" to run.

This guide is for developers building their own integration. For internal REST conventions see
[`API_GUIDELINES.md`](API_GUIDELINES.md); for the data shapes see [`DATA_MODEL.md`](DATA_MODEL.md).

## The API key

- **Multiple named keys per user.** You can hold several keys at once; generating a new one no
  longer revokes the others. Each key is revoked independently by id.
- **Scopes.** Every key is either `READ` (may only issue safe `GET`/`HEAD` requests — any mutating
  method returns `403 { reason: "api_key_read_only" }`) or `FULL` (unrestricted, same as your web
  session). Keys created before this feature, and keys created without an explicit scope, are `FULL`.
  Prefer a `READ` key for dashboards and other read-only integrations.
- **Optional expiry.** A key may be created with a lifetime (`expiresInDays`); once expired it stops
  authenticating. Absent that, a key never expires.
- **Format:** `tday_<keyId>_<secret>` — the literal prefix `tday_`, an opaque key id, and a
  256-bit URL-safe random secret.
- **Storage:** the plaintext key is shown **only once**, at creation. The server keeps only a
  fast SHA-256 hash of the secret (legacy keys use PBKDF2), a 4-character preview (for "ending in …"
  display), the label/scope/expiry, and a `lastUsedAt` timestamp — it can never show you the key again.

### Generating & managing a key

From the web app: **Settings → Dashboard access**, optionally name the key and pick **Read-only**
or **Full access**, then **Generate key** and copy it immediately. Each existing key can be revoked
on its own from the list below the form.

Equivalently, the management endpoints (these require a logged-in **session cookie**, not the API
key itself):

| Method   | Path                     | Body / Returns                                                                 |
|----------|--------------------------|--------------------------------------------------------------------------------|
| `GET`    | `/api/user/api-key`      | `{ keys: [{ id, label?, scope, keyPreview, createdAt, lastUsedAt?, expiresAt?, expired }] }` |
| `POST`   | `/api/user/api-key`      | Body `{ label?, scope?: "READ"\|"FULL", expiresInDays? }` → `{ message, apiKey: { id, key, keyPreview, label?, scope, createdAt, expiresAt? } }` — `key` is the full plaintext, returned once |
| `DELETE` | `/api/user/api-key/{id}` | `{ message }` (revokes that one key; `404` if the id is unknown)                |

Changing your password still revokes **all** of your API keys.

## Authenticating requests

Send the key as a **Bearer token** on every request to `/api/*`:

```
Authorization: Bearer tday_<keyId>_<secret>
```

- The key authenticates as its **owning user**. A `FULL` key carries the **same authorization as that
  user's web session**; a `READ` key is limited to safe methods (see [Scopes](#the-api-key)). Account
  approval status still applies. Any `Authorization` value that does **not** start with `tday_` is
  treated as a normal session token instead.
- **Base URL** is your T'Day server origin — the same host that serves the web app. Use **HTTPS**.
- The standard rate limits and security headers apply to API-key requests too.

**Validate a key** (and discover the current user) with:

```bash
curl -H "Authorization: Bearer tday_<keyId>_<secret>" https://tday.example.com/api/auth/session
# 200 → { "user": { "id", "name", "username", "role", "approvalStatus", "timeZone", ... } }
# 401 → key missing, invalid, disabled, or revoked
```

## Working with tasks

The endpoints below are the ones a task integration needs (this is exactly the surface the Homarr
widget uses). Scheduled tasks and **floaters** (undated "Anytime" tasks) are separate concepts.
All due times are **UTC ISO-8601 instants** — send/receive absolute moments and render them in the
user's timezone (see the timezone model in [`DEPLOYMENT.md`](DEPLOYMENT.md#server-timezone)).

| Method   | Path                                              | Purpose                                              |
|----------|---------------------------------------------------|------------------------------------------------------|
| `GET`    | `/api/todo?timeline=true&recurringFutureDays=60`  | Scheduled tasks (today / upcoming, with recurrence)  |
| `GET`    | `/api/todo/overdue`                               | Overdue scheduled tasks                              |
| `GET`    | `/api/floater`                                    | Floaters (undated tasks)                             |
| `GET`    | `/api/list`  /  `/api/floaterList`                | Scheduled-task lists / floater lists                 |
| `POST`   | `/api/todo`     `{ title, priority, due, listID? }` | Create a scheduled task                            |
| `POST`   | `/api/floater`  `{ title, priority, listID? }`    | Create a floater                                     |
| `PATCH`  | `/api/todo`  /  `/api/floater`   `{ id, ...fields }` | Update a task                                      |
| `PATCH`  | `/api/todo/complete` / `/api/todo/uncomplete`     | Toggle completion (floater equivalents exist)        |
| `DELETE` | `/api/todo`  `{ id }`                             | Delete a task                                        |
| `DELETE` | `/api/todo/instance`  `{ todoId, instanceDate }`  | Delete one occurrence of a recurring task            |
| `DELETE` | `/api/floater`  `{ id }`                          | Delete a floater                                     |

Request/response bodies follow the shared DTOs in [`DATA_MODEL.md`](DATA_MODEL.md); `priority` is one
of `Low | Medium | High`.

### Example: create a task

```bash
curl -X POST https://tday.example.com/api/todo \
  -H "Authorization: Bearer tday_<keyId>_<secret>" \
  -H "Content-Type: application/json" \
  -d '{ "title": "Submit report", "priority": "High", "due": "2030-07-29T20:00:00.000Z" }'
```

## Export & import (portable backup)

Two endpoints move a user's whole dataset as one versioned JSON bundle — the basis for
"Download my data", server backups, and Local→Server migration.

| Method | Path           | Purpose                                                             |
|--------|----------------|--------------------------------------------------------------------|
| `GET`  | `/api/export`  | The caller's full [`TdayExport`](../shared/src/commonMain/kotlin/com/ohmz/tday/shared/model/ExportModels.kt) bundle |
| `POST` | `/api/import`  | Restore/merge a bundle: `{ export, dryRun?, includeCompleted?, includePreferences? }` |

- The bundle carries lists, floater lists, todos (with their `exdates` and per-occurrence
  instance overrides), floaters, completed history, and preferences. Descriptions are
  **decrypted to plaintext** on export, so a bundle is portable across servers that use
  different field-encryption keys — the importing server re-encrypts on its own boundary.
- `schemaVersion` gates compatibility: import rejects a bundle newer than the server understands
  and fills missing fields from defaults for older ones.
- Import **never overwrites**. Any primary-key id that already exists for the user (or repeats
  within the bundle) is minted fresh and all references rewritten, so a bundle always adds rows.
  The whole import runs in one transaction — it fully applies or fully rolls back.
- `dryRun: true` validates and returns the counts that *would* be written (`ImportCounts`,
  including `remappedIds`) without touching the database — the preview a trust screen shows before
  the user confirms.

## Security notes

- A `FULL` API key is a **full-account credential** — treat it like a password. Anyone holding it can
  read and change all of that user's tasks. A `READ` key can only read, which limits the blast radius
  if it leaks — prefer it for dashboards and other read-only integrations, and set an expiry when the
  key only needs to live for a while.
- Always call over **HTTPS**. If a key leaks, **revoke that key** from Settings (other keys keep
  working).
- Build integrations to read the key from a secret store, not source. Give each integration its own
  named key so you can revoke one without disturbing the others.

## Reference: the Homarr "Tday Tasks" widget

The [`ohmzi/homarr`](https://github.com/ohmzi/homarr) fork ships a **Tday integration** + **Tday
Tasks widget** that consumes exactly this API (Bearer key + the endpoints above) — a complete,
working reference implementation. End-user setup is documented in the
[`ohmzi/documentation`](https://github.com/ohmzi/documentation/tree/develop) site. See the project
[`README.md`](../README.md#dashboard-widget-homarr) for the connect-it-up summary.

# MCP (AI assistants)

T'Day speaks the [Model Context Protocol](https://modelcontextprotocol.io), so an AI assistant —
Claude Code, Claude Desktop, Cursor, or anything else that speaks MCP over HTTP — can read and
change your tasks by talking to your own T'Day server. Nothing to install and no extra service to
run: the endpoint is part of the backend, at `https://<your-tday>/mcp`, and it authenticates with
the same [API keys](API_INTEGRATION.md#the-api-key) the REST API uses.

Once it is connected you can say things like *"what's on my plate today?"*, *"add 'submit the
report' for Friday 9am to my Work list"*, or *"remind me to buy batteries sometime"* and have them
land in the right place.

## Connecting it up

1. In T'Day, go to **Settings → Dashboard access → Generate key**. Pick **Full access** if you want
   the assistant to be able to create and change tasks, or **Read-only** if it should only look.
   Copy the key — it is shown once.
2. Point your AI client at `https://<your-tday>/mcp` with that key as a Bearer token.

T'Day accepts the key from whichever header your client can actually set, so one server works
everywhere:

| Header | Use it when |
|--------|-------------|
| `Authorization: Bearer tday_…` | The client lets you set `Authorization` yourself (Claude Code, Cursor, `mcp-remote`) |
| `X-API-Key: tday_…` | The client reserves `Authorization` for OAuth and offers a dropdown of extra headers (claude.ai and Claude Desktop connectors) |

`Api-Key`, `X-Auth-Token` and `X-API-Token` are accepted as aliases of `X-API-Key`, and
`Authorization` works with or without the `Bearer ` prefix. Only values starting with `tday_` are
read from the alternate headers.

### Claude Code

```bash
claude mcp add --transport http tday https://tday.example.com/mcp --header "Authorization: Bearer tday_<keyId>_<secret>"
```

### claude.ai and Claude Desktop (custom connector)

Settings → **Connectors** → **Add custom connector**, name it and enter
`https://<your-tday>/mcp`. The dialog probes the server, finds no OAuth, and offers to configure it
manually — click **Next**, then:

1. **Authentication → None.** The description says it: *"Pick this for servers with open access, or
   for servers that use an API key instead of OAuth."* Leaving it on **Always required** makes Claude
   attempt an OAuth handshake T'Day does not implement, which fails with *"Couldn't register with
   Tday's sign-in service"*.
2. Under **Additional headers**, add `api-key` (or `x-api-key`) with your `tday_…` key as the value.
3. Save, then **Connect**.

### Claude Desktop (config file)

`claude_desktop_config.json` itself only launches **stdio** servers — entries are
`command`/`args`/`env`, and an entry with `"type": "http"` and a `"url"` is rejected as *"not a valid
MCP server configuration"*. If you would rather not use the connector UI, bridge it with
[`mcp-remote`](https://github.com/geelen/mcp-remote) (needs Node):

```json
{
  "mcpServers": {
    "tday": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "https://tday.example.com/mcp",
        "--header",
        "X-API-Key:${TDAY_KEY}"
      ],
      "env": { "TDAY_KEY": "tday_<keyId>_<secret>" }
    }
  }
}
```

The key goes through `env` rather than inline: Claude Desktop splits `args` on spaces, so a value
containing one gets mangled. Note there is **no space** after the colon. Restart Claude Desktop
completely — the config is read only at launch.

Config file locations:

- **macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows:** `%APPDATA%\Claude\claude_desktop_config.json`

### Cursor and other HTTP-MCP clients

```json
{
  "mcpServers": {
    "tday": {
      "type": "http",
      "url": "https://tday.example.com/mcp",
      "headers": { "Authorization": "Bearer tday_<keyId>_<secret>" }
    }
  }
}
```

Your server has to be reachable from wherever the AI client runs. On a home server that usually
means a reverse proxy or a tunnel — see [`REMOTE_ACCESS.md`](REMOTE_ACCESS.md). Always use HTTPS: the
key is a bearer credential.

> **No OAuth.** T'Day has no authorization server, so any client flow that insists on OAuth 2.1 with
> dynamic client registration will fail. Every client above is covered by a static key instead —
> in the connector UI that means choosing **Authentication → None** and supplying the key as a header.

## What each key scope can do

| Scope | Reading | Writing |
|-------|---------|---------|
| `FULL` | yes | yes — same authority as your own web session |
| `READ` | yes | no |

A `READ` key still connects and can use every read tool. Write tools stay visible so the assistant
can explain itself, but calling one returns:

```
This T'Day API key is read-only (scope: READ). Creating, editing, completing and deleting tasks
require a Full-access key. Generate one in T'Day → Settings → Dashboard access → Full access, then
update your MCP connection.
```

A `FULL` key is a full-account credential — treat it like a password, give the assistant its own
named key, and revoke that one key if you change your mind.

## The two things the assistant is taught

Both of these are T'Day semantics an assistant cannot infer, so the server states them in its
`initialize` instructions and repeats them in the tool descriptions.

**A date decides what a task is.** A task with a due date is a *scheduled task* and shows up in
Today, the calendar, and reminders. A task without one is an *Anytime task* (a floater) and lives in
its own feed. These are separate records, not one record with an optional date — see
[`DATA_MODEL.md`](DATA_MODEL.md#scheduling-rules). So:

- `tday_create_task` with a `due` writes a scheduled task; without one it writes an Anytime task.
- Adding a date to an Anytime task **promotes** it into a scheduled one; clearing the date
  **demotes** it back. A repeating task can't be demoted — its series would be lost.

**Lists come in two namespaces.** Scheduled lists hold dated tasks; Anytime lists hold undated ones.
The same name can exist in one and not the other. When you name a list that doesn't exist, nothing
is written and the assistant is handed the names that do exist, plus close matches:

```
No scheduled list named "Werk" exists. Closest match: "Work". Available: Work, Home, Errands.
Don't create a list unless the user asks — say it doesn't exist first, then use createListIfMissing.
```

Naming an Anytime list for a dated task gets the same treatment, explaining that the two kinds of
list hold different things.

## Tools

Task ids are handles — `todo:<id>` for a scheduled task, `floater:<id>` for an Anytime task — so a
task read by one tool can be passed straight to another without ambiguity.

| Tool | Needs `FULL` | What it does |
|------|:---:|--------------|
| `tday_get_context` | | Your account, timezone, current server time, whether this key can write, and every list in both namespaces. The first call of a session. |
| `tday_find_list` | | Whether a list exists by name, in which namespace, and close matches if not. |
| `tday_list_tasks` | | Tasks by view (`today`, `overdue`, `upcoming`, `anytime`, `all`), optionally filtered to one list. Repeating tasks are expanded into their real occurrences. |
| `tday_search_tasks` | | Tasks whose title or notes match a term, across both kinds, optionally including completed history. |
| `tday_create_task` | ✓ | Creates a scheduled task when given a `due`, an Anytime task when not. |
| `tday_update_task` | ✓ | Retitle, renote, reprioritise, repin, move between lists, change or clear the date, change or stop the repeat, or edit one occurrence. |
| `tday_complete_task` | ✓ | Complete or reopen a task, or a single occurrence of a repeating one. |
| `tday_delete_task` | ✓ | Delete a task; with an `occurrenceDate`, cancel just that occurrence. |
| `tday_create_list` | ✓ | Create a scheduled or Anytime list. No-ops if the name is already taken in that namespace. |

### Dates and times

`due` accepts `2026-08-21T09:00` (read in your timezone), `2026-08-21` (a whole day), or a full
ISO-8601 instant like `2026-08-21T09:00:00Z`. A date with no time is due at **23:59 local**, so a
task you dated but didn't time doesn't read as overdue for most of that day.

Times come back rendered in your timezone. T'Day stores due dates as a UTC wall clock with no
offset, which is easy to misread as local time — the MCP layer converts in both directions so the
assistant never quotes a time that is silently off by your UTC offset.

### Repeating tasks

`recurrence` is an RFC-5545 rule, e.g. `RRULE:FREQ=WEEKLY;INTERVAL=1;BYDAY=MO`, and needs a `due` —
the rule says when the repeats fall, the due date says where they start.

`tday_list_tasks` expands a series into its actual occurrences, drops cancelled ones, and applies
per-occurrence edits. This has to happen on the server: `TodoDto` carries `rrule` but not `exdates`,
so no external client can work out which occurrences were cancelled. Every occurrence is reported
with an `occurrenceDate`; pass that to `tday_update_task`, `tday_complete_task` or `tday_delete_task`
to act on one occurrence instead of the whole series.

## Supporting endpoints

The MCP endpoint is built on the same REST API any integration can use. Two pieces exist for it and
are equally usable from your own code:

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/integration/context` | One call for scope, user, timezone, server time, and both list namespaces |
| `GET` | `/api/auth/session` | Now also returns `apiKey: { scope, label, keyPreview }` when an API key authenticated the request |

```bash
curl -s -H "Authorization: Bearer tday_<keyId>_<secret>" https://tday.example.com/api/integration/context
```

```json
{
  "apiKey": { "scope": "FULL", "label": "Claude", "keyPreview": "a1b2" },
  "user": { "id": "usr_…", "username": "ohmz", "name": "Omar", "timeZone": "Europe/London" },
  "serverTime": "2026-08-21T14:03:00",
  "capabilities": { "canWrite": true },
  "lists": [{ "id": "clx…", "name": "Work", "todoCount": 4, "myRole": "OWNER" }],
  "anytimeLists": [{ "id": "cly…", "name": "Groceries", "todoCount": 11, "reusable": false }]
}
```

`apiKey` is absent for session-authenticated callers, and `capabilities.canWrite` is false only for
a `READ` key.

## Protocol details

For anyone writing an MCP client against it, or debugging one:

- **Auth:** a `tday_` API key on `Authorization` (with or without `Bearer `), or on `X-API-Key` /
  `Api-Key` / `X-Auth-Token` / `X-API-Token`. Non-`tday_` values on the alternate headers are
  ignored, not tried as session tokens. There is no query-parameter form — URLs end up in proxy and
  browser logs.
- **Transport:** Streamable HTTP, stateless. Every JSON-RPC message is a `POST /mcp` returning
  `application/json`. No `Mcp-Session-Id` is issued and no SSE stream is opened, so `GET` and
  `DELETE` return `405`.
- **Methods:** `initialize`, `notifications/initialized` (answered `202`), `ping`, `tools/list`,
  `tools/call`. Anything else returns JSON-RPC `-32601`.
- **Protocol versions:** `2025-06-18`, `2025-03-26`, `2024-11-05`. An unknown request gets the
  newest back. JSON-RPC batches are rejected — they were removed from the spec in `2025-06-18`.
- **Errors:** a failing *tool* returns a normal result with `isError: true` and text explaining what
  went wrong, so the assistant can relay it. JSON-RPC errors are reserved for malformed requests.
- **Rate limiting:** the same per-user budget as `/api/**` (see
  [`API_GUIDELINES.md`](API_GUIDELINES.md#authentication)); a burst returns `429` with
  `retryAfterSeconds`.

Try it without a client:

```bash
curl -s -X POST https://tday.example.com/mcp -H "Authorization: Bearer tday_<keyId>_<secret>" -H "Content-Type: application/json" -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

## Troubleshooting

| Symptom | Cause |
|---------|-------|
| `401` with "A T'Day API key is required" | No `Authorization` header, or the key is revoked, expired, or from another server. Keys are revoked wholesale when you change your password. |
| Every write says the key is read-only | The key was generated as **Read-only**. Generate a **Full access** key and update the connection. |
| `403 api_key_read_only` on a REST call | That is the REST API's own guard, not MCP — a `READ` key may only issue `GET`/`HEAD`. |
| `429` | Rate limited. The response carries `retryAfterSeconds`. |
| `405` on `GET /mcp` | Expected. The endpoint is POST-only. |
| Claude Desktop: *"not valid MCP server configurations and were skipped"* | `claude_desktop_config.json` only launches stdio servers — `"type": "http"` with a `"url"` is not a shape it understands. Use the connector UI, or the `mcp-remote` bridge above. |
| Connector: *"Couldn't register with Tday's sign-in service"* | Authentication is set to **Always required**, so Claude tried an OAuth handshake. Switch it to **None** and supply the key under Additional headers. |
| Connector: *"Couldn't determine the server settings"* with a **404** | Expected before the endpoint exists on that server — but a 404 after deploying means `/mcp` isn't there. Check `GET /api/mobile/probe`. |
| `mcp-remote` starts but every call is 401 | The header was split on its space. Put the value in `env` and write `X-API-Key:${TDAY_KEY}` with no space after the colon. |
| `404` on `/mcp` | The server predates the MCP endpoint. Check `GET /api/mobile/probe` for its `appVersion` and redeploy. |
| The client connects but lists no tools | It is probably speaking stdio, not HTTP. This is an HTTP MCP server. |
| Assistant says a list doesn't exist | It doesn't — in that namespace. Ask it to check the other one, or to create it. |

## Security notes

- A `FULL` key lets the assistant read and change everything in your account. Give it its own named
  key so you can revoke that one alone, and set an expiry if it is only needed for a while.
- Prefer a `READ` key when you only want the assistant to answer questions about your tasks.
- Everything an assistant does through MCP is an ordinary authenticated write: it shows up in your
  apps immediately over `/ws`, and fires any [webhooks](API_INTEGRATION.md#outbound-webhooks) you
  have registered.
- Always connect over HTTPS. If a key leaks, revoke it in **Settings → Dashboard access**.

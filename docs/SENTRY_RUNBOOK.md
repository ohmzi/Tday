# Sentry Runbook

This runbook is for debugging failures in a private, self-hosted T'Day install.
It is not a product analytics plan. T'Day uses Sentry to understand crashes,
exceptions, failed requests, slow surfaces, release regressions, and privacy-safe
diagnostic breadcrumbs.

## Credential Handling

Do not paste passwords, session cookies, DSNs, or Sentry auth tokens into issues,
docs, commits, chat, logs, screenshots, or support threads.

If credentials are exposed:

1. Rotate the password or token immediately.
2. Revoke any old token in Sentry.
3. Check Sentry organization/project audit logs if available.
4. Replace local `.env`, CI secrets, or build settings with the new value.

For automation, prefer a short-lived or least-privilege Sentry auth token over an
account password. Store it only in local environment variables or CI secrets.

## Project Setup

Use separate Sentry projects so noisy platform failures do not hide each other:

| Platform | Suggested Project | Release Name |
|----------|-------------------|--------------|
| Backend | `tday-backend` | `tday-backend@<version>` |
| Web | `tday-web` | `tday-web@<version>` |
| Android | `tday-android` | `tday-android@<version>` |
| iOS | `tday-ios` | `tday-ios@<version>` |

Configure DSNs through environment/build inputs only:

| Platform | DSN Input | Trace Sample Input |
|----------|-----------|--------------------|
| Backend | `SENTRY_DSN` | `SENTRY_TRACES_SAMPLE_RATE` |
| Web | `VITE_SENTRY_DSN` | `VITE_SENTRY_TRACES_SAMPLE_RATE` |
| Android | `SENTRY_DSN` or `local.properties:sentryDsn` | `SENTRY_TRACES_SAMPLE_RATE` or `local.properties:sentryTracesSampleRate` |
| iOS | Xcode build setting `SENTRY_DSN` | Xcode build setting `SENTRY_TRACES_SAMPLE_RATE` |

Recommended starting trace sample rates:

| Environment | Sample Rate |
|-------------|-------------|
| Local development | `1.0` |
| Staging / private test server | `0.5` |
| Production self-hosted | `0.1` to `0.2` |

Raise production sampling temporarily only while investigating a specific issue.

## Release Artifacts

Before considering Sentry fully operational, verify release artifacts:

- Web source maps upload only when `SENTRY_AUTH_TOKEN` is configured.
- Android mapping/native metadata upload only when Sentry token/env is configured.
- iOS dSYMs upload during the configured release/archive flow.
- Backend release uses `TDAY_BACKEND_VERSION`, then `TDAY_APP_VERSION`, then
  `0.0.0`; it should not stay stuck on an old hardcoded app version.
- The release shown in Sentry matches the version shipped to users.

## Suggested Alerts

Create alerts per project:

| Alert | Why |
|-------|-----|
| New issue in production | Catches brand-new failures after deploy |
| Regression after resolved issue | Catches reintroduced bugs |
| Event spike above baseline | Catches loops, outages, or bad releases |
| Crash-free sessions/users below target, where supported | Highlights mobile release quality |
| High p95/p99 transaction duration on API routes | Catches backend or network slowness |
| Frequent `security.event` breadcrumbs | Surfaces lockouts, rate limits, and auth abuse signals |
| Events without a release value | Finds broken release wiring |

Use issue ownership rules to route backend, web, Android, and iOS failures to the
right code area.

## Failure Triage

When a Sentry issue appears:

1. Confirm platform, environment, release, and first-seen time.
2. Check `tday.operation` or breadcrumb messages such as `sync.replay`,
   `server.probe`, `realtime.connect`, `reminder.reschedule`, `update.check`,
   `local_mode.enter`, or `api.request`.
3. Check the sanitized route template, HTTP status, and failure class.
4. Compare affected release with the last known good release.
5. Reproduce locally using the same platform and mode: Local Mode or Server Mode.
6. Add or update a focused test around the failure.
7. Fix the code, verify the platform suite, then resolve the Sentry issue.
8. If the issue recurs, compare breadcrumbs and release values before reopening a
   broad investigation.

Do not ask users for screenshots of Sentry events if they may contain secrets.
Use event IDs and sanitized context instead.

## Diagnostic Event Taxonomy

Keep names stable, low-cardinality, and structural:

| Area | Examples |
|------|----------|
| API | `api.request`, `api.transport`, route template like `PATCH /api/todo/:id` |
| Sync | `sync.replay`, `sync.manual`, `local_mode.sync_noop` |
| Server setup | `server.probe`, `update.check` |
| Realtime | `realtime.connect`, `realtime.disconnect` |
| Reminders | `reminder.reschedule`, `reminder.cancel_all` |
| Calendar/tasks | `calendar.page`, `calendar.task.reschedule`, `task.reschedule` |
| Lists | `list.create`, `list.update`, `list.delete` |
| Credentials | `credential.request`, `credential.result`, `credential.save` |
| Security | `security.event` with reason code only |

Allowed data: route templates, status codes, durations, counts, booleans,
release/build values, enum-like mode/scope/result names.

Disallowed data: task/list/floater titles, descriptions, user text, email,
raw URL, query string, raw ID, token, cookie, session ID, auth header, request
body, response body, and local cache records.

## Local Smoke Checks

Run the no-dependency smoke check after observability changes:

```bash
node scripts/observability-smoke.mjs
```

It checks the repo for:

- No GA/Dynatrace/Mixpanel/Amplitude dependency additions.
- Sentry privacy options remain disabled for PII and replay.
- Web console/DOM breadcrumbs stay disabled.
- Trace propagation stays restricted to T'Day API routes.
- Platform helpers remain the single place for manual breadcrumbs and captures.
- Route-like data keys are sanitized by helper code.
- Required docs and checklists exist.

Then run the platform tests from `docs/TESTING.md`.

## Production Smoke Drill

Use a staging or private test deployment. Do not add a public production endpoint
that intentionally crashes.

1. Configure DSNs and sample rates for all platforms you want to test.
2. Deploy backend/web and install mobile builds from the same release version.
3. Trigger a safe, expected failure such as an invalid authenticated request,
   disconnected server probe, or blocked realtime reconnect.
4. Verify Sentry receives:
   - Correct project and environment.
   - Correct release/dist.
   - Sanitized route template.
   - Structural breadcrumbs.
   - No email, raw URL, auth/session/cookie value, task title, list name, or
     local-only content.
5. Resolve the test issue or mark it as ignored with a clear note.

## When More Context Is Needed

Prefer improving diagnostic structure before raising sample rates:

- Add a new structural breadcrumb at the start and failure edge of the operation.
- Add counts or enum-like result classes.
- Add a route template or status code if relevant.
- Add a focused sanitizer test when introducing a new data field.

Do not add engagement analytics, ad tracking, session replay, or user behavior
funnels for failure debugging.

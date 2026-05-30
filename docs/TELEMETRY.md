# Telemetry & Crash Reporting

T'Day uses Sentry for crash reporting, release health, and privacy-safe
performance diagnostics across backend, web, Android, and iOS. Sentry was first
added in commit `4b79a1b` on 2026-04-04 (`feat: add Sentry telemetry across all
platforms (v1.14.0)`), followed by no-DSN safety, CI upload guardrails, privacy
tests, and iOS dSYM/package fixes.

Operational Sentry setup, alerting, release artifact checks, smoke drills, and
failure triage live in [`SENTRY_RUNBOOK.md`](SENTRY_RUNBOOK.md).

Telemetry is not product analytics. T'Day does not add Google Analytics,
Dynatrace, Mixpanel, Amplitude, advertising IDs, or user-behavior tracking by
default. Industry analytics guidance is used only as a privacy and taxonomy
reference: keep event names stable, low-cardinality, consent-aware, and free of
PII.

## Industry Reference Baseline

T'Day stays Sentry-first. The comparison set is used to shape the telemetry
contract, not to add more SDKs:

- [Sentry data collected](https://docs.sentry.io/platforms/javascript/guides/react/data-management/data-collected/):
  Sentry can collect stack traces, runtime/device context, request URLs/query
  strings, headers, breadcrumbs, and console logs depending on SDK settings, so
  T'Day keeps `sendDefaultPii = false`, strips IP fields in `beforeSend`, and
  sanitizes routes before adding breadcrumbs or transaction names.
- [Sentry tracing and sampling](https://docs.sentry.io/platforms/javascript/guides/express/tracing/):
  tracing is useful for throughput/latency and distributed debugging, but
  production sampling should be configurable and lower than local debugging.
- [Sentry distributed tracing](https://docs.sentry.io/platforms/javascript/guides/capacitor/tracing/distributed-tracing/):
  trace headers should propagate only to known app/API origins, with
  `sentry-trace` and `baggage` explicitly allowed by backend CORS.
- [Google Analytics PII guidance](https://support.google.com/analytics/answer/6366371?hl=en)
  and [GA4 custom events](https://support.google.com/analytics/answer/12229021?hl=en-EN):
  event names and dimensions must avoid PII, URLs/titles can leak user data, and
  custom event taxonomies should be deliberate. T'Day does not install GA4.
- [Dynatrace personal data guidance](https://docs.dynatrace.com/docs/manage/data-privacy-and-security/data-privacy/personal-data-captured-by-dynatrace):
  observability tools can capture URLs, IDs, IPs, logs, and session details, so
  T'Day treats route names, labels, logs, and breadcrumbs as sensitive unless
  explicitly sanitized. T'Day does not install Dynatrace.
- [OpenTelemetry observability primer](https://opentelemetry.io/docs/concepts/observability-primer/):
  logs, metrics, and traces should explain system behavior together. T'Day maps
  that model to Sentry breadcrumbs, exceptions, transactions, and releases
  without adding product analytics.

## What Is Collected

Every Sentry event may contain non-identifying diagnostics:

| Data | Example | Why It Helps |
|------|---------|--------------|
| Stack trace | `NullPointerException at TodoService.kt:42` | Finds the failing code path |
| Release / build | `tday-ios@1.44.0`, dist `6` | Connects regressions to releases |
| Runtime context | OS, browser, device model class | Reproduces platform-specific failures |
| Sanitized route | `PATCH /api/todo/:id` | Shows failing surface without IDs or query strings |
| HTTP status | `503` | Separates server, auth, and network failures |
| Structural breadcrumbs | `sync.replay`, `server.probe`, `realtime.connect` | Reconstructs failure sequence |
| Performance timings | API/server transactions and navigation spans | Finds slow or failing surfaces |
| Security event code | `auth_lockout`, `request_rate_limit_triggered` | Connects backend abuse signals to failures |

## What Is NOT Collected

| Excluded Data | Enforcement |
|---------------|-------------|
| Task, floater, list titles, descriptions, notes, or user text | Telemetry helpers sanitize breadcrumbs, paths, labels, and log messages |
| Local Mode task/list/floater content | Local Mode breadcrumbs are structural only, such as `local_mode.enter` |
| Email, username, display name, IP address | `sendDefaultPii = false`; `beforeSend` strips residual user/IP fields |
| Cookies, auth headers, CSRF values, session IDs | Request bodies are not attached; web request headers/cookies are scrubbed |
| Query strings and raw URLs | Route helpers remove queries and replace IDs with `:id` |
| Console output and DOM click/key breadcrumbs | Web Sentry config disables console/DOM automatic breadcrumbs and filters remaining breadcrumbs through `beforeBreadcrumb` |
| Screen replay or recordings | Web replay sample rates remain `0`; mobile replay is not enabled |
| Product analytics, engagement funnels, ad identifiers | No analytics SDKs are installed or allowed by guardrail tests |

## Coverage Matrix

| Area | Current Coverage | Required for New Work |
|------|------------------|-----------------------|
| Backend HTTP | Sentry init, logback error appender, sanitized request transactions, security event breadcrumbs | New routes use structural errors and avoid raw path/query/body telemetry |
| Web | Sentry init, router instrumentation, ErrorBoundary/route error capture, sanitized API breadcrumbs, console/DOM breadcrumb filters | New API clients use `src/lib/observability/sentry.ts` helpers |
| Android | Sentry init, OkHttp tracing, navigation listener, sync/realtime/local-mode breadcrumbs, sanitized developer logs | New repositories/ViewModels use `TdayTelemetry` for diagnostics |
| iOS | Sentry init, release/dist, DSN via Info.plist build settings, API/sync/realtime/local-mode breadcrumbs | New async flows use `TdayTelemetry` structural breadcrumbs |
| Offline sync | Pending replay and Local Mode no-op breadcrumbs | New mutation types emit counts/kinds only, never payload text |
| Mobile server setup | Probe/version breadcrumbs | New connection/version flows report phase and result class only |
| Reminders/widgets | Reminder reschedule breadcrumbs and nonfatal capture around scheduler/boot/worker failure paths | New failure paths should capture exception type and operation |
| Security events | Backend `eventLog` plus Sentry breadcrumbs for event code | New security events must use documented reason codes |

## Post-Sentry Feature Coverage

Sentry was introduced on 2026-04-04. Newer work must keep diagnostic coverage
structural and content-free:

| Feature Area | Current Diagnostic Events | Privacy Boundary |
|--------------|---------------------------|------------------|
| Local Mode | `local_mode.enter`, `local_mode.sync_noop` | No local task/list/floater content, no server upload side effect |
| Floater / Anytime tasks | `task.create`, `task.update`, `task.complete`, `task.delete`, `todo_list.load` with `mode=floater` | No floater title, description, list ID, or local cache record |
| Floater and scheduled lists | `list.create`, `list.update`, `list.delete` with `kind=floater` or `kind=scheduled` | Only `has_color`, `has_icon`, and scoped-list booleans |
| Offline sync replay | `sync.replay`, `sync.manual`, `server.probe` | Counts, phase, and result class only; never pending mutation payloads |
| Credential manager / password autofill | `credential.request`, `credential.result`, `credential.save`, `credential.clear` | No email, password, server URL, credential ID, or host entered by the user |
| Mobile probe and version gate | `server.probe`, `update.check` | Phase/status/scope only; no raw server URL |
| Update install flow | `update.check` | Release/version comparison only; no user identifiers |
| Realtime reconnect | `realtime.connect`, `realtime.disconnect` | Connection phase/status only; message payload types are normalized |
| Reminder scheduling | `reminder.reschedule`, `reminder.cancel_all` | Counts/source only; no task titles or reminder text |
| Calendar paging and mode changes | `calendar.page`, `calendar.mode`, `calendar.today`, `calendar.load`, `calendar.refresh` | Mode/direction/counts only; no selected date, task ID, or title |
| Task/list drag-reschedule | `calendar.drag_reschedule`, `calendar.task.reschedule`, `task.reschedule` | Recurring/scope/source only; no task ID, title, or target date |
| Security event monitoring | `security.event` | Reason code and route template only; no IP, raw URL, email, or session value |

## Privacy Safeguards In Code

The relevant code paths:

- Backend: `Application.kt`, `plugins/SentryPlugin.kt`, `observability/TdayObservability.kt`
- Web: `src/main.tsx`, `src/lib/observability/sentry.ts`
- Android: `TdayApplication.kt`, `core/observability/TdayTelemetry.kt`
- iOS: `Core/SentryConfiguration.swift`

Rules for every breadcrumb, tag, transaction, log, or extra:

- Allowed: route templates, screen/operation names, status codes, durations,
  release/build versions, counts, booleans, and enum-like failure classes.
- Route-like telemetry fields such as `route`, `path`, `url`, `from`, and `to`
  must be passed through the platform helper so they become templates like
  `/api/list/:id` instead of raw URLs or IDs.
- Not allowed: user-created text, emails, server URLs, raw IDs, raw query
  strings, auth/session/cookie values, request/response bodies, or local cache
  records.
- Prefer stable names such as `sync.replay`, `server.probe`,
  `realtime.connect`, `reminder.reschedule`, `update.check`, and
  `local_mode.enter`.

## New Feature Observability Checklist

When adding or changing UI, API, sync, auth, reminder, widget, realtime, or
storage behavior:

- Add a structural breadcrumb or error capture for the feature's main failure
  path if a silent failure would be hard to debug.
- Sanitize route names through the platform helper before adding breadcrumbs or
  Sentry extras.
- Record counts and enum states, not names, titles, descriptions, or IDs.
- Check Local Mode: diagnostics may describe the operation but must not imply or
  trigger upload of local-only data.
- Add or update tests when adding a telemetry helper, route sanitizer, or new
  class of diagnostic event.
- Update this document when behavior, collected fields, SDK config, or privacy
  boundaries change.

## Configuration

### Self-Hosted Defaults

Sentry is optional. With an empty DSN, SDKs stay disabled or initialize in
no-op mode and send nothing.

| Platform | DSN | Sample Rate |
|----------|-----|-------------|
| Backend | `SENTRY_DSN` | `SENTRY_TRACES_SAMPLE_RATE` |
| Web | `VITE_SENTRY_DSN` | `VITE_SENTRY_TRACES_SAMPLE_RATE` |
| Android | `SENTRY_DSN` or `local.properties:sentryDsn` | `SENTRY_TRACES_SAMPLE_RATE` or `local.properties:sentryTracesSampleRate` |
| iOS | Xcode build setting `SENTRY_DSN` into `Info.plist` | `SENTRY_TRACES_SAMPLE_RATE` into `Info.plist` |

Production defaults to `0.2` trace sampling unless overridden. Development
defaults to `1.0` so local issues are easier to reproduce.

# Telemetry & Crash Reporting

T'Day uses Sentry for crash reporting on the backend, web, Android, and iOS.
Since 0.8.0 the three clients are **opt-in and failures-only**: nothing is sent
until a person turns "Crash & problem reports" on, and then only at the moment
something fails (a crash, a freeze, the system closing the app for using too
much memory, an unexpected error, or an unusually slow operation). The backend
keeps its own operator-controlled switch, described in
[Backend Server Reports](#backend-server-reports). The decision and its
rationale are in
[ADR 009](adr/009-opt-in-failure-only-crash-reporting.md).

Sentry was first added in commit `4b79a1b` on 2026-04-04 (`feat: add Sentry
telemetry across all platforms (v1.14.0)`), followed by no-DSN safety, CI upload
guardrails, privacy tests, and iOS dSYM/package fixes. Release 0.8.0 made the
clients consent-based, removed their session pings and sampled traces, and
added the on-device scrubbing described below.

Operational Sentry setup, alerting, release artifact checks, smoke drills, and
failure triage live in [`SENTRY_RUNBOOK.md`](SENTRY_RUNBOOK.md).

Telemetry is not product analytics. T'Day does not add Google Analytics,
Dynatrace, Mixpanel, Amplitude, advertising IDs, or user-behavior tracking by
default. Industry analytics guidance is used only as a privacy and taxonomy
reference: keep event names stable, low-cardinality, consent-aware, and free of
PII.

## At A Glance

| Platform | Who decides | Default | What is sent |
|----------|-------------|---------|--------------|
| Web | Each browser: the one-time consent card, then Settings → Privacy | Off | Failure reports only |
| Android | Each device: the one-time consent card, then Settings → Privacy | Off | Failure reports only |
| iOS | Each device: the one-time consent card, then Settings → Privacy | Off | Failure reports only |
| Backend | The operator sets `SENTRY_DSN`, then an admin turns on "Server error reports" (web Settings → Privacy) | Off | Error reports plus sampled request traces |

Clients send no session pings, no release-health data, and no sampled
performance traces. A device that never opts in sends nothing at all, and a
build with no DSN never asks.

## Industry Reference Baseline

T'Day stays Sentry-first. The comparison set is used to shape the telemetry
contract, not to add more SDKs:

- [Sentry data collected](https://docs.sentry.io/platforms/javascript/guides/react/data-management/data-collected/):
  Sentry can collect stack traces, runtime/device context, request URLs/query
  strings, headers, breadcrumbs, and console logs depending on SDK settings, so
  T'Day keeps `sendDefaultPii = false` (Kotlin: `isSendDefaultPii`; web: the
  equivalent `dataCollection` block, which Android and the backend also set
  field by field), removes the user and IP from every event before it leaves,
  and sanitizes routes before adding breadcrumbs or transaction names.
- [Sentry tracing and sampling](https://docs.sentry.io/platforms/javascript/guides/express/tracing/):
  tracing is useful for throughput/latency and distributed debugging, but
  production sampling should be configurable and lower than local debugging.
  Only the backend traces (default `0.1` in production); the clients send no
  traces.
- [Sentry distributed tracing](https://docs.sentry.io/platforms/javascript/guides/capacitor/tracing/distributed-tracing/):
  trace headers should propagate only to known app/API origins. T'Day's clients
  propagate to none (`tracePropagationTargets` is empty), because an unset list
  means every host, and every user's own server would receive trace headers
  carrying the maintainer's DSN public key.
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
  that model to Sentry breadcrumbs, exceptions, and releases (plus backend
  transactions) without adding product analytics.

## Consent And Delivery

This section applies to web, Android, and iOS. The backend is covered in
[Backend Server Reports](#backend-server-reports).

### Consent State

Consent is tri-state: `unanswered`, `granted`, or `denied`. `unanswered` behaves
exactly like `denied`; the only difference is that the one-time card still has
to ask. The answer is per device (per browser on web). It is not synced, and it
is not tied to an account or workspace mode: sign-out, an expired session,
leaving a workspace, and clearing local data all leave it alone.

| Platform | Where the answer lives | Keys |
|----------|------------------------|------|
| Web | `localStorage`; both keys are in `PRESERVED_STORAGE_KEYS` (`AuthProvider.tsx`) | `tday.telemetry.consent` (`granted` or `denied`, absent means unanswered), `tday.telemetry.consentAt` (epoch ms, present only while granted) |
| Android | Plain `SharedPreferences` file `telemetry_consent_prefs`, not the encrypted `SecureConfigStore`, and not cleared by `OfflineCacheManager.clearAllLocalData` | `state` (`granted` or `denied`), `granted_at_ms` (a `granted` entry without a timestamp reads as unanswered) |
| iOS | `UserDefaults.standard`, not the Keychain, so a reinstall resets it | `telemetry.consent` (Bool, absent means unanswered), `telemetry.consentAt` (epoch seconds) |

The answer has to be readable before the app's own services exist, because that
is when the SDK either starts or never does: in `main.tsx` before React renders,
in `Application.onCreate` on Android, and in `TdayApp.init` on iOS.

### Where The Question Is Asked

- **No DSN, no surface.** When a build carries no DSN (web `VITE_SENTRY_DSN`,
  Android `BuildConfig.SENTRY_DSN`, iOS `SENTRY_DSN` in `Info.plist`; forks,
  debug runs, self-built apps), there is no card, no Settings row, and the SDK
  is never initialised.
- **Consent card.** A wizard-styled card ("Help fix crashes?") appears once,
  after the connect/sign-in wizard, when a workspace (Server or Local Mode) is
  open. Existing installs see it once too. "Share reports" and "Not now" carry
  equal weight. It is skipped when the DSN is missing, the question is already
  answered, it was set aside this session, or a higher gate is up (update
  required, security questions, app lock, and on web a forced password change).
  Escape or Back, and "Read the full FAQ", set the card aside without answering:
  the question returns on the next launch (web: the next browser session,
  because the deferral lives in `sessionStorage`). "Read the full FAQ" also opens
  the `crash-reports` guide topic.
- **Settings → Privacy.** A "Crash & problem reports" switch with a "?" that
  opens the same guide topic. Answering here first counts as answering, so the
  card never asks.
- **Where the card is mounted.** Web: `CrashReportsConsentGate` in
  `AppLayout.tsx`, inside the signed-in shell, so Server Mode after login and
  Local Mode after passphrase setup are both covered. Android:
  `TelemetryConsentGate` in `ScheduledTaskHomeRoute`, composed before
  `AuthenticatedGates` so the update-required and security-questions gates draw
  over it, and never over the app lock. iOS: `TelemetryConsentCard` overlay in
  `AppRootView`, placed before the update-required and security-questions gates
  so they draw over it; the app lock is a separate overlay above everything.
- **Nothing before a yes.** The SDK is never initialised, nothing is buffered,
  and no handler is installed or patched (on web, `window.onerror` and `fetch`
  stay untouched). A failure during first run, including onboarding, is lost by
  design. No telemetry about consent changes is ever sent.

### Turning It Off

Switching off, or saying "Not now" after a yes, runs the same sequence on every
client:

1. Persist `denied` (and drop the timestamp), so a kill half way still ends as a
   no.
2. Close the in-memory gate, so nothing already queued can leave.
3. Close the SDK and clear its scopes and breadcrumbs. There is no flush.
4. Purge on-disk state:

| Platform | Purged |
|----------|--------|
| Web | Nothing on disk: the client is closed and unbound and the global, isolation, and current scopes are cleared, with no reload. A withdrawal in another tab arrives through the `storage` event |
| Android | `cacheDir/sentry` and the `INSTALLATION` file the SDK writes to `filesDir` |
| iOS | `<Caches>/io.sentry` and `<Caches>/SentryCrash` (purged before and after `SentrySDK.close()`, because the SDK keeps cached crash reports and sends them on a later launch) |

A launch with any state other than `granted` purges again before doing anything
else (Android `TelemetryBootstrap.start`, iOS `SentryConfiguration.start`), so
an older build's leftovers and an interrupted shutdown are cleaned up too.

### Turning It On

Granting purges first, then persists `granted` with a timestamp, then starts the
SDK fresh. `beforeSend` drops any event stamped earlier than that timestamp. This
is what keeps history from riding in on a later yes: Android replays an ANR from
`ApplicationExitInfo` on the next launch for up to 91 days, and iOS replays
crashes, hangs, and watchdog kills on the next launch. Whatever happened while
the switch was off is never sent.

### DSN Delivery

A DSN is a public, build-time identifier, not a secret. The consent gate, not the
DSN's secrecy, decides whether it is ever used. Release builds carry the
maintainer's DSNs, injected by CI:

| Platform | How the build gets its DSN | Source |
|----------|----------------------------|--------|
| Backend | Runtime env `SENTRY_DSN` | The operator's own environment; never injected by CI |
| Web | `VITE_SENTRY_DSN` build arg, baked into the bundle in the frontend stage of `Dockerfile.backend` | `release.yml` passes `secrets.SENTRY_DSN_WEB`; a self-built image reads it from the project-root `.env` |
| Android | `BuildConfig.SENTRY_DSN` from env `SENTRY_DSN` or `local.properties:sentryDsn` | `release.yml` passes `secrets.SENTRY_DSN_ANDROID` to the APK build |
| iOS | `SENTRY_DSN` build setting flowing into `Info.plist` | `ios-testflight.yml` passes `secrets.SENTRY_DSN_IOS`; the Fastfile writes it into the build's temporary xcconfig after a shape check |

The web Content-Security-Policy has to allow the browser's ingest host. The final
image stage sets `TDAY_CLIENT_SENTRY_DSN` from the same build arg, and the
backend always adds that DSN's ingest origin to `connect-src`, in addition to the
backend DSN's own origin. `CSP_CONNECT_EXTRA` replaces only the backend-DSN
origin.

Symbolication artifacts upload with `SENTRY_AUTH_TOKEN` (upload scope only):
the Android R8 mapping (Sentry Gradle plugin), the web source maps (Sentry Vite
plugin, which then deletes the public `.map` files from `dist`), and the iOS
dSYMs (`sentry-cli debug-files upload`, run before the TestFlight upload). The
token is a build secret, never a build arg or env baked into an image. A missing
DSN or token never fails a release: CI emits a warning, and that client ships
inert or unsymbolicated.

## What Is Collected

A report is sent only at the moment of a failure, and only by a device that
opted in (clients) or an instance whose admin turned reports on (backend).
Every report may contain non-identifying diagnostics:

| Data | Example | Why It Helps |
|------|---------|--------------|
| Stack trace | `NullPointerException at TodoService.kt:42` | Finds the failing code path |
| Failure kind | Crash, ANR or app hang with thread data, OOM or watchdog kill, unhandled error, `slow_operation` | Separates what went wrong from how often |
| Scrubbed message | `Failed to connect to [host]` | Explains the error without hosts, addresses, or identifiers |
| Release / build | `tday-ios@0.8.0`, dist = build number | Connects regressions to releases |
| Runtime context | OS and version (browser on web), device model, architecture, memory, battery level, online state; on iOS also coarse connection type (`wifi`, `cellular`, `none`) from sentry-cocoa 9.30 | Reproduces platform-specific failures, including offline/sync ones |
| Context tags | `tz_offset` (`UTC+2`), `locale_lang` (`de`), `mode` (`local` or `server`), `client` (`web`, `android`, `ios`), `app_version` | Dates are central to a task planner and its bugs are time-zone and DST sensitive; the tags are as coarse as a date bug allows |
| Sanitized route | `PATCH /api/todo/:id` | Shows failing surface without IDs or query strings |
| HTTP status | `503` | Separates server, auth, and network failures |
| Structural breadcrumbs | `sync.replay`, `server.probe`, `realtime.connect`, and a few steps before the failure | Reconstructs failure sequence |
| Slow-operation timing | `duration_ms`, `threshold_ms`, and a duration bucket on `slow_operation` events only | Finds unusually slow starts and syncs without timing healthy sessions |

Backend reports (when enabled) add:

| Data | Example | Why It Helps |
|------|---------|--------------|
| Request transactions | `PATCH /api/todo/:id`, sampled | Finds slow or failing server routes (not sent by clients) |
| Client build tags | `client.platform` = `android`, `client.version` = `0.8.0` | Ties a server error to the app build that triggered it |
| Security event code | `auth_lockout`, `request_rate_limit_triggered` | Connects backend abuse signals to failures |

### Context Tags

Every client event carries the same five tags. `tz_offset` is the UTC offset
(`UTC`, `UTC+2`, `UTC-5`, `UTC+5:30`), never the IANA zone, which names a
region. `locale_lang` is the language alone, lower case (`de`, not `de-DE`),
never the full locale. When the locale has no usable language, web reports `und`,
Android reports `und` for a blank language, and iOS omits the tag unless the
language code is exactly two letters. On Android `mode` is set once the UI
has resolved the workspace, so a crash in a headless start (widget, boot, worker)
before that carries no `mode`. Both offset and language are disclosed in the
in-app FAQ.

### Slow Operations

A slow operation is a failure-class event, not a performance span, so it still
reaches Sentry with tracing off. The event has the fixed message
`slow_operation`, level warning, fingerprint `["slow_operation", <operation>]`,
tags `operation` and `duration_bucket` (`<5s`, `5-10s`, `10-30s`, `30-60s`,
`>60s`), and extras `duration_ms` and `threshold_ms`. Extra attributes pass
through the platform's `safeDataValue`.

| Operation | Threshold | Clients |
|-----------|-----------|---------|
| `cold_start` | 5 s | Web, Android, iOS |
| `first_data_ready` | 8 s | Web, Android, iOS |
| `db_open_migrate` | 3 s | Web, Android, iOS |
| `cache_hydrate` | 2 s | Web, Android, iOS |
| `sync_replay` | 15 s | Web, Android, iOS |
| `api_call` (per route template, excluding offline and cancelled) | 10 s | Web, Android, iOS |
| `widget_refresh` | 10 s | Web, Android, iOS |
| `reminder_reschedule` | 10 s | Web, Android, iOS |
| `local_vault_unlock` | 8 s | Web only |
| `route_chunk_load` | 8 s | Web only |
| `app_bootstrap` | 6 s | Web only |

Rate limits keep one slow device from becoming a stream: one report per
operation per process (per page load on web), at most five per process, and a
24-hour cooldown per operation that survives restarts (web
`localStorage["tday.slowOperation.cooldowns"]`, Android `SharedPreferences`, iOS
`UserDefaults` key `telemetry.slowOperation.lastReported`). Nothing is reported,
and no cooldown slot is used, while consent is off. The web helper declares all
eleven ids so the vocabulary is one table everywhere, even though
`widget_refresh` and `reminder_reschedule` have no web caller.

**Status:** the helpers, thresholds, and limits are implemented and unit-tested
on all three clients (web `lib/observability/slowOperation.ts`, Android
`SlowOperation.kt`, iOS `Core/Telemetry/SlowOperation.swift`). No call site
reports an operation yet; wiring them is Phase 2 (see
[Coverage Roadmap](#coverage-roadmap-not-yet-implemented)).

## What Is NOT Collected

| Excluded Data | Enforcement |
|---------------|-------------|
| Task, floater, list titles, descriptions, notes, or user text | Telemetry helpers sanitize breadcrumbs, paths, labels, and log messages; exception and message text is redacted on the device before sending |
| Local Mode task/list/floater content | Local Mode breadcrumbs are structural only, such as `local_mode.enter`; Local Mode diagnostics never imply a server upload |
| Name, username, email, user ID, install ID | The event `user` is removed on every client (web deletes it, Android and iOS null it), so the per-install UUID the SDKs attach as `user.id`, and Android's `device.id`, never leave the device. `sendDefaultPii = false` on backend/Android/iOS (Kotlin: `isSendDefaultPii`) and an explicit `dataCollection` block with `userInfo` off on web, Android, and the backend (Sentry web SDK 11 removed `sendDefaultPii`) |
| IP address, location | The IP is never set and `ip_address` is cleared on every event. The Sentry project setting "Prevent Storing of IP Addresses" must also be on for all four projects (an operator step, see the runbook): Sentry necessarily sees the network address of any connection, and is set not to store it |
| Your server's address, hostnames, URLs | URLs become route templates; hosts, IPs, URLs, and emails are redacted from message text; web stack-frame and debug-image paths are reduced to path-only; iOS image paths lose the app-container ID; Android `serverName` and iOS `serverName` are cleared; the backend uses the fixed name `tday-backend` |
| Cookies, auth headers, CSRF values, session IDs | Request bodies are not attached; web `dataCollection` sets `cookies: false`, `httpBodies: []` and `urlQueryParams: false`, and the web request is rebuilt to the route template plus `User-Agent` only (which also removes `Referer`); Android and iOS drop the request entirely |
| Query strings and raw URLs | Route helpers remove queries and replace IDs with `:id` |
| Device name, full locale, IANA time zone, device hash | Dropped (`device.name`, `device.locale`, `device.timezone`, the `culture` context, iOS `device_app_hash`, Android `device.id`) and replaced by the coarse `tz_offset` and `locale_lang` tags |
| Navigation arguments | Android records the destination's route pattern only (`/todos/list/:listId/:listName`) through `TdayTelemetry.recordNavigation`, because Sentry's own navigation listener also attaches the arguments, which are the list's ID and name |
| Console output, DOM/UI interaction, logcat, system-event breadcrumbs | Breadcrumbs pass a per-platform category allow-list (below); everything else is dropped. Android's Sentry Gradle plugin logcat instrumentation is off |
| Screenshots, view hierarchy, screen replay or recordings | Off on every client: replay sample rates `0`, screenshots and view hierarchy not attached |
| Sessions, release-health pings, performance traces (clients) | Auto session tracking off, `tracesSampleRate` `0`, no tracing integrations, no client reports, no trace propagation |
| Failed-request captures (clients) | Off: a 5xx is the server's report to make, and a 4xx, offline phone, or timeout is noise. Clients capture contract/decode failures, unexpected exceptions, and slow operations |
| Product analytics, engagement funnels, ad identifiers | No analytics SDKs are installed or allowed by guardrail tests |

### Scrub Rules

Each client has one pure, unit-tested scrubber that runs in `beforeSend` and
`beforeBreadcrumb` (web `webScrub.ts`, Android `TelemetryScrubber.kt`, iOS
`TelemetryScrubber.swift`; the backend has its own `TelemetryScrubber.kt`). The
rule is allow, don't deny: a category or context the scrubber has not heard of
is dropped. A web event that cannot be scrubbed is not sent.

| Item | Rule |
|------|------|
| `user`, install UUID, Android `device.id` | Removed |
| IP | Never set; `ip_address` cleared |
| Exception and message text | URLs, hosts, IPv4/IPv6 addresses, emails, `jdbc:` strings, Postgres `Key (x)=(y)` fragments, UUIDs and cuids, and long digit runs are replaced with placeholders such as `[url]`, `[host]`, `[ip]`, `[email]`, `[id]`, and `[number]`; the text is cut to 300 characters. The exception type is kept |
| URL and request | Host and query dropped, route template kept. Web keeps only `User-Agent`; Android and iOS keep no request |
| Device context | IANA `timezone`, full `locale`, and `device.name` dropped; `tz_offset` and `locale_lang` added as tags |
| Contexts | Android keeps only `app`, `device`, `os`, `runtime`, `trace`, and `art`; iOS removes `culture` and `user info` |
| Breadcrumb categories | Allow-list below. `http` and `navigation` on Android are reduced to method, status code, route template, and `from`/`to` templates |

Breadcrumb category allow-list:

| Platform | Categories kept |
|----------|-----------------|
| Web | `api`, `fetch`, `xhr`, `navigation`, `tday` |
| Android | `http`, `navigation`, `tday`, `error`, `app.lifecycle`, `network.event` |
| iOS | `tday`, `api`, `error`, `app.lifecycle`, `device.connectivity` |
| Backend | `tday`, `http`, `security` |

### Client SDK Posture

Every option that matters is written out, including the ones that are the SDK's
default today, because a default is a promise only until the next release.

| Setting | Web | Android | iOS |
|---------|-----|---------|-----|
| Traces | `tracesSampleRate: 0`, `traceLifecycle: "static"`, no tracing integration bundled | `tracesSampleRate = 0.0`, `beforeSendTransaction` drops everything, Sentry Gradle plugin `tracingInstrumentation` off | `tracesSampleRate = 0`, transactions dropped in `beforeSend`, auto performance tracing off |
| Session tracking | No session integration (`defaultIntegrations: false` with a named allow-list) | `isEnableAutoSessionTracking = false` | `enableAutoSessionTracking = false` |
| Client reports | `sendClientReports: false` | `isSendClientReports = false` | `sendClientReports = false` |
| Trace propagation | `tracePropagationTargets: []` | `setTracePropagationTargets(emptyList())` | `tracePropagationTargets = []` |
| Failed requests | None installed | `SentryOkHttpInterceptor(captureFailedRequests = false)` | `enableCaptureFailedRequests = false`, swizzling off |
| User and PII | `dataCollection` with `userInfo: false`, cookies, query strings, and bodies off | `dataCollection.userInfo = false` first, then every other field; `isSendDefaultPii = false` | `sendDefaultPii = false` |
| Replay, screenshots | Replay rates `0` | Replay `0.0`, screenshot and view hierarchy off | Replay `0`, screenshot and view hierarchy off |
| Failure detectors | Global error handlers, React root error handlers, `linkedErrors`, `dedupe` | Uncaught exceptions, ANR detection, NDK (native crashes); `isReportHistoricalAnrs = false`, and the timestamp guard covers whatever ANR the SDK still replays on the next launch | Crash handler, app hang tracking (2 s), watchdog termination tracking |

Web only reports errors thrown by scripts served from the page's own origin
(`allowUrls`), ignores browser-extension scripts (`denyUrls`), and ignores
network noise (`Failed to fetch`, `Load failed`, `AbortError`, and similar).
Android's second layer is `options.setTransportGate`, which drops an envelope
already queued when consent is withdrawn, while `GatedTransportFactory` stops new
ones.

## Coverage Matrix

| Area | Current Coverage | Required for New Work |
|------|------------------|-----------------------|
| Backend HTTP | Gated Sentry init (`BackendSentry`), logback error appender (breadcrumbs at ERROR only), sanitized request transactions, per-request scope, security event breadcrumbs | New routes use structural errors and avoid raw path/query/body telemetry |
| Web | Consent-gated init (`sentryInit.ts`), global handler and React root error capture, ErrorBoundary/route error capture, sanitized API breadcrumbs, scrub allow-list; no router instrumentation or tracing | New API clients use `src/lib/observability/sentry.ts` helpers |
| Android | Consent-gated init (`TelemetryBootstrap`), crash/ANR/native capture, OkHttp breadcrumbs (no failed-request capture), route-pattern navigation breadcrumbs, sync/realtime/local-mode breadcrumbs, sanitized developer logs | New repositories/ViewModels use `TdayTelemetry` for diagnostics |
| iOS | Consent-gated init (`SentryConfiguration`), crash/hang/watchdog capture, memory-warning breadcrumb, release/dist, DSN via Info.plist build settings, API/sync/realtime/local-mode breadcrumbs | New async flows use `TdayTelemetry` structural breadcrumbs |
| Offline sync | Pending replay and Local Mode no-op breadcrumbs | New mutation types emit counts/kinds only, never payload text |
| Mobile server setup | Probe/version breadcrumbs | New connection/version flows report phase and result class only |
| Reminders/widgets | Reminder reschedule breadcrumbs and nonfatal capture around scheduler/boot/worker/widget-create failure paths | New failure paths should capture exception type and operation only |
| Security events | Backend `eventLog` plus Sentry breadcrumbs for event code | New security events must use documented reason codes |
| Slow operations | `slow_operation` helpers with thresholds and rate limits on web, Android, iOS (no call sites yet) | New slow-path wrappers use the helper and the shared operation ids |

## Post-Sentry Feature Coverage

Sentry was introduced on 2026-04-04. Newer work must keep diagnostic coverage
structural and content-free. These events are breadcrumbs: they reach Sentry
only attached to a failure report from a device that opted in, and only if their
category is on the allow-list above.

| Feature Area | Current Diagnostic Events | Privacy Boundary |
|--------------|---------------------------|------------------|
| Local Mode | `local_mode.enter`, `local_mode.sync_noop` | No local task/list/floater content, no server upload side effect |
| Floater / Anytime tasks | `task.create`, `task.update`, `task.complete`, `task.delete`, `todo_list.load` with `mode=floater`, `widget_create_floater.submit` on widget submit failure | No floater title, description, list ID, or local cache record |
| Floater and scheduled lists | `list.create`, `list.update`, `list.delete` with `kind=floater` or `kind=scheduled` | Only `has_color`, `has_icon`, and scoped-list booleans |
| Offline sync replay | `sync.replay`, `sync.replay_rate_limited`, `sync.manual`, `server.probe` | Counts, phase, and result class only; never pending mutation payloads |
| Credential manager / password autofill | `credential.request`, `credential.result`, `credential.save`, `credential.clear` | No email, password, server URL, credential ID, or host entered by the user |
| Mobile probe and version gate | `server.probe`, `update.check` | Phase/status/scope only; no raw server URL |
| Update install flow | `update.check` | Release/version comparison only; no user identifiers |
| Realtime reconnect | `realtime.connect`, `realtime.disconnect` | Connection phase/status only; message payload types are normalized |
| Reminder scheduling | `reminder.reschedule`, `reminder.cancel_all` | Counts/source only; no task titles or reminder text |
| Device-calendar mirror | `calendar.sync.reconcile`, `calendar.sync.disabled`, `calendar.sync.permission_denied`, `calendar.sync.calendar_unavailable`, `calendar.sync.reconcile`/`calendar.sync.delete_calendar`/`calendar.sync.save_event` captures | Event counts and failure operation only; never task titles, descriptions, task IDs, device calendar identifiers, or the user's other calendars |
| Calendar paging and mode changes | `calendar.page`, `calendar.mode`, `calendar.today`, `calendar.load`, `calendar.refresh` | Mode/direction/counts only; no selected date, task ID, or title |
| Task/list drag-reschedule | `calendar.drag_reschedule`, `calendar.task.reschedule`, `task.reschedule` | Recurring/scope/source only; no task ID, title, or target date |
| Car task surfaces | `car_surface.open`, `car_surface.switch_mode`, `car_task.voice_create`, `car_task.complete` | Platform, mode, result, and counts only; no task title, voice transcript, task ID, or local cache record |
| Security event monitoring | `security.event` | Reason code and route template only; no IP, raw URL, email, or session value |
| Crash reports consent | None: no telemetry about consent changes is ever sent | The answer lives on the device (`telemetry.consent`) and never leaves it |
| Server error reports toggle | Reason codes `telemetry_enabled` and `telemetry_disabled` in the security event log | Reason code only; no user or instance detail |

## Privacy Safeguards In Code

The relevant code paths. Each platform's SDK init call lives in exactly one file
(marked "init"), and `tday-web/tests/guardrails/sentry-privacy.test.ts` pins that,
so a new init anywhere else fails CI.

- Backend:
  - `Application.kt` creates the `TelemetryGate`, calls `BackendSentry.init`, and
    loads the gate from the database once migrations have run.
  - `observability/BackendSentry.kt` (init), `observability/TelemetryGate.kt`,
    `observability/GatedTransport.kt`, `observability/TelemetryScrubber.kt`,
    `observability/ClientTags.kt`, `observability/TdayObservability.kt`.
  - `plugins/SentryPlugin.kt` (per-request scope, transactions, client tags),
    `plugins/SecurityHeaders.kt` (CSP ingest origins),
    `services/InstanceSettingsService.kt`, `routes/AdminRoutes.kt`, and
    `resources/logback.xml`.
- Web:
  - `src/main.tsx` calls `initSentryIfConsented()` before `createRoot` and hands
    React the root error handlers.
  - `src/lib/observability/sentryInit.ts` (init: `buildWebSentryOptions`,
    `startSentry`, `stopSentry`, `initSentryIfConsented`),
    `src/lib/observability/webScrub.ts`, `src/lib/observability/sentry.ts`
    (helpers and route sanitizers), `src/lib/observability/slowOperation.ts`.
  - `src/lib/privacy/telemetryConsent.ts`, `src/hooks/useTelemetryConsent.ts`,
    `src/components/privacy/CrashReportsConsentGate.tsx`,
    `src/components/settings/PrivacyRows.tsx`.
- Android:
  - `TdayApplication.kt` calls `TelemetryBootstrap.shared(this).start()` first in
    `onCreate`, so a widget refresh, boot receiver, worker, or alarm that wakes
    the app cold is covered. Only a consenting device pays for starting Sentry;
    everyone else does one preferences read and a purge.
  - `core/observability/TelemetryBootstrap.kt` (init), `TelemetryOptions.kt`,
    `TelemetryScrubber.kt`, `TelemetryConsentStore.kt`,
    `TelemetryConsentManager.kt`, `TelemetryGate.kt`, `GatedTransport.kt`,
    `TelemetryEventTags.kt`, `SlowOperation.kt`, `TdayTelemetry.kt`.
  - `feature/telemetry/TelemetryConsentGate.kt` and its ViewModel;
    `AndroidManifest.xml` removes `SentryInitProvider`,
    `SentryPerformanceProvider`, and `SentryNdkPreloadProvider` and keeps
    `io.sentry.auto-init` false; `core/network/NetworkModule.kt`.
  - Debug builds only: `TelemetryDebugReceiver` (`crash`, `anr`, and `slow_op`
    triggers) and LeakCanary.
- iOS:
  - `TdayApp.swift` calls `SentryConfiguration.start()` in `init`.
  - `Core/SentryConfiguration.swift` (init: `start`, the pure `makeOptions`, and
    `TdayTelemetry`).
  - `Core/Telemetry/TelemetryConsentStore.swift`, `TelemetryConsentModel.swift`,
    `TelemetryLifecycle.swift` (grant, revoke, purge, `TelemetryGate`),
    `TelemetryScrubber.swift`, `SlowOperation.swift`, and
    `Feature/Telemetry/TelemetryConsentCard.swift`.

Layers that make "off means off" a property of the code rather than of call
sites:

- **No SDK before consent.** Nothing initialises, patches, or buffers.
- **An in-memory gate**, closed first on revoke and checked in `beforeSend`,
  `beforeBreadcrumb`, and (web, Android, backend) the transport. A wrapped
  transport implements every `ITransport` method explicitly, because Kotlin `by`
  delegation would let the interface's default `send(envelope)` bypass the gate;
  a test fails if an SDK upgrade adds a method that does.
- **Disk purge** on revoke and on every launch that is not granted.
- **A timestamp guard** that drops events older than the grant.

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

## Backend Server Reports

The backend is operator-owned and holds no device or user content, so it keeps
its own, simpler model: `SENTRY_DSN` stays the master switch, and an admin
toggle decides whether anything is sent.

- **Master switch.** With `SENTRY_DSN` unset the SDK initialises with an empty
  DSN and sends nothing, and the admin toggle is hidden.
- **Admin toggle.** With a DSN set, nothing is sent until an admin turns
  "Server error reports" on in web Settings → Privacy. The row is shown only to
  an `ADMIN` in Server Mode, and only while `GET /api/admin/telemetry` reports
  `dsnConfigured`. It is web-only on purpose (a documented parity exception): it
  configures the operator's server, which a phone does not.
- **Default off after deploy.** The setting is stored in the `instance_settings`
  table (Flyway `V32`, key `telemetry.sentry.enabled`, value `true` or `false`);
  a missing row means off. A server that deploys 0.8.0 with a DSN already set
  therefore goes quiet until an admin switches the toggle on.
- **Gate, not close and re-init.** `Sentry.init` runs once at process start
  (`Application.kt` to `BackendSentry.init`), before the database is up, and the
  SDK is never closed or re-initialised. An in-memory `TelemetryGate` (default
  closed) is loaded from the database right after `dbConfig.init()` and updated
  by the admin endpoint. It is checked by the transport wrapper
  (`GatedTransportFactory`, which drops rather than caches; the SDK's own
  `transportGate` is a connectivity check and would cache and resend later), by
  `beforeSend`, `beforeSendTransaction`, and `beforeBreadcrumb`, and by
  `tracesSampler`, which returns `0.0` while closed. Client reports are off.
  There is no on-disk event cache, so enabling the gate later sends nothing from
  before. A boot that fails before the gate can be read (database down) is not
  reported: that blind spot is accepted.
- **Writes are serialised** by a mutex and the gate updates after commit, so a
  failed write never leaves the gate open. Each change logs the reason code
  `telemetry_enabled` or `telemetry_disabled` through `SecurityEventLogger`, and
  nothing else.
- **Sampling.** `tracesSampler` returns `0` for `/health`, `/api/mobile/probe`,
  `/ws`, and `/calendar/*` (the paths the rate limiter treats as infrastructure,
  about 576 health transactions a day on their own), and
  `SENTRY_TRACES_SAMPLE_RATE` for everything else: `0.1` in production and `1.0`
  elsewhere unless overridden.
- **Hardening.** The user and IP are dropped, the request is rebuilt as method
  plus path template, `serverName` is the fixed `tday-backend`, and
  `dataCollection` is set field by field with `userInfo` off. Messages are
  redacted (including Postgres `Key (x)=(y)` and `Failing row contains (...)`
  fragments), cancellation and client-abort IO exceptions are dropped, and
  logback sends breadcrumbs at ERROR only, because INFO and WARN lines carry
  user IDs, request paths, and push endpoints. The logback appender is given a
  sentinel DSN so it cannot start an ungated client of its own.
- **Per-request scope.** `SentryRequestPlugin` gives every request its own forked
  pair of scopes (`SentryContext(Sentry.forkedScopes("request"))`), so
  breadcrumbs and tags written while serving one request do not show up on
  another request's error. The `X-Tday-Client` and `X-Tday-App-Version` headers
  become the `client.platform` (`web`, `ios`, `android`) and `client.version`
  (`x.y.z`) tags, validated and never echoed. Android and iOS send these
  headers; the web app does not, so web requests are untagged.

## Memory Leaks

Sentry does not detect memory leaks. It reports an out-of-memory crash and, on
iOS, a watchdog termination, which is the system ending the app for using too
much memory. That is a symptom, not a leak report, and a kernel low-memory kill
of a background Android process leaves no event at all. The in-app FAQ says the
same.

Leak hunting uses other tools:

- Android: LeakCanary, added as `debugImplementation` only, so it never ships in
  a release build.
- iOS: Instruments (Leaks and Allocations) and `XCTMemoryMetric` in tests.
- Production proxies: the trend of OOM and watchdog events in Sentry, an iOS
  `memory.warning` breadcrumb (with `available_mb`) on the next event, Play
  Console vitals, and Xcode Organizer.
- Web: there is no browser memory signal.

## New Feature Observability Checklist

When adding or changing UI, API, sync, auth, reminder, widget, realtime, or
storage behavior:

- **Consent first.** Client telemetry must be consent-gated: never initialise or
  start a Sentry SDK anywhere but the platform's consent-gated initializer
  (`sentryInit.ts`, `TelemetryBootstrap.kt`, `SentryConfiguration.swift`, and
  `BackendSentry.kt` for the backend), and never record outside the allow-list
  above. A new category, tag, context, or extra means updating the scrubber, its
  tests, and this document.
- Add a structural breadcrumb or error capture for the feature's main failure
  path if a silent failure would be hard to debug. Report new slow paths with the
  `slow_operation` helper and the shared operation ids rather than a new span or
  event name.
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

Sentry is optional. With an empty DSN the clients never start the SDK (no consent
card, no Settings row) and the backend sends nothing and hides its admin toggle.

| Platform | DSN | Notes |
|----------|-----|-------|
| Backend | `SENTRY_DSN` (runtime) | `SENTRY_TRACES_SAMPLE_RATE` (default `0.1` in production, `1.0` elsewhere). `TDAY_CLIENT_SENTRY_DSN` is baked into the published image and only adds the browser's ingest origin to the CSP. An admin must also turn the toggle on |
| Web | `VITE_SENTRY_DSN` (build time) | No sample-rate setting: web sends no traces. `SENTRY_AUTH_TOKEN` (build secret) uploads source maps |
| Android | `SENTRY_DSN` or `local.properties:sentryDsn` | No sample-rate setting. `SENTRY_AUTH_TOKEN` uploads the R8 mapping |
| iOS | Xcode build setting `SENTRY_DSN` into `Info.plist` | No sample-rate setting. CI uploads dSYMs with `SENTRY_AUTH_TOKEN` |

The client trace-rate knobs (`VITE_SENTRY_TRACES_SAMPLE_RATE`, the Android
`SENTRY_TRACES_SAMPLE_RATE` build field, and iOS `SENTRY_TRACES_SAMPLE_RATE` in
`Info.plist`) were removed in 0.8.0; clients no longer sample traces.

Production backend sampling defaults to `0.1` unless overridden. Development
defaults to `1.0` so local issues are easier to reproduce.

## Coverage Roadmap (Not Yet Implemented)

This table is the planned Phase 2 and is **not implemented**. Clients stay
failures-only: nothing here adds traces, sessions, or analytics.

| Layer | Planned additions | Skipped, and why |
|-------|-------------------|------------------|
| Network | Web: breadcrumb on fetch rejection, and React Query `onError` captures only non-`ApiError`. Mobile: capture contract/decode failures and add retry/transport breadcrumbs | 5xx/4xx/offline captures on clients (noise, or the server's own report) |
| Data | Android: Room/SQLCipher open and migration captures, `SQLiteFullException`, debug-only StrictMode (`allowMainThreadQueries()` is an ANR source). iOS: replace `try!` on `ModelContainer` in `AppContainer.swift` with a captured failure. Web: IndexedDB, quota, and vault-open failures in `lib/local/localDb.ts`. Backend: p6spy or sentry-jdbc spans behind an env flag | Client DB spans (traces are off) |
| Logic and sync | `SyncManager` replay failures become captures on Android and iOS (mostly swallowed by `runCatching` today), conflict-count breadcrumbs, slow-operation wrappers | |
| UI | Keep `ErrorBoundary` and `RouteErrorPage` | All UI transaction, interaction, and SwiftUI tracing (analytics-like) |
| Background | Android WorkManager, boot, and widget failure captures at the existing sites; iOS BGTask expiry; backend scheduler failures (`ReminderPushScheduler`, `RetentionScheduler`, summary warm-up, MCP tool failures) and a `retention-sweep` Cron check-in | Per-minute check-ins; the widget and watch targets stay uncovered (no Sentry linked) |
| Startup | `cold_start` and `first_data_ready` slow-operation call sites (Android `Process.getStartUptimeMillis()`) | |
| Memory and OOM | Android: attach heap and native-heap data on `OutOfMemoryError`, an `onTrimMemory` breadcrumb, and a next-launch `REASON_LOW_MEMORY` capture (timestamp-gated). iOS: evaluate `enableMetricKit` (timestamp-gated). Backend: capture `OutOfMemoryError` in the `StatusPages` handler, flush, and rethrow | Browser memory signal |
| ANR and hang | Android ANR replayed on the next launch, timestamp-gated. iOS hang tracking then MetricKit | Web (no signal outside spans) |

Already shipped from the roadmap's ideas: the web `onUncaughtError` and
`onRecoverableError` handlers, the backend client-abort filter, the iOS
memory-warning breadcrumb, and the `slow_operation` helpers themselves. The open
part is the call sites.

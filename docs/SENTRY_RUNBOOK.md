# Sentry Runbook

This runbook is for debugging failures in a private, self-hosted T'Day install.
It is not a product analytics plan. T'Day uses Sentry to understand crashes,
exceptions, slow surfaces, release regressions, and privacy-safe diagnostic
breadcrumbs.

Since 0.8.0 the web, Android, and iOS clients report only after a person opts in
(default off, per device) and only at the moment of a failure, so Sentry will
never show healthy-session data, crash-free-session rates, or a user to look up.
The backend reports only after an admin turns on "Server error reports". What is
and is not collected, and how consent works, is in
[`TELEMETRY.md`](TELEMETRY.md).

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

A DSN is a public, build-time identifier: the release builds carry the
maintainer's client DSNs, and the in-app consent gate (not DSN secrecy) is what
protects users. The auth token is the secret.

## Project Setup

Use separate Sentry projects so noisy platform failures do not hide each other:

| Platform | Suggested Project | Release Name |
|----------|-------------------|--------------|
| Backend | `tday-backend` | `tday-backend@<version>` |
| Web | `tday-web` | `tday-web@<version>` |
| Android | `tday-android` | `tday-android@<version>` |
| iOS | `tday-ios` | `tday-ios@<version>` |

Configure DSNs through environment/build inputs only:

| Platform | DSN Input |
|----------|-----------|
| Backend | `SENTRY_DSN` (runtime environment) |
| Web | `VITE_SENTRY_DSN` (build time, baked into the bundle) |
| Android | `SENTRY_DSN` or `local.properties:sentryDsn` (build time, into `BuildConfig`) |
| iOS | Xcode build setting `SENTRY_DSN` (build time, into `Info.plist`) |

A client build with no DSN shows no consent card and no Settings row, and never
starts the SDK. A backend with no DSN sends nothing and hides the admin toggle.

### GitHub Secrets For Release Builds

The maintainer's release workflows inject the client DSNs and the upload token.
Create these as repository **secrets** (not variables):

| Secret | Used by | What it is |
|--------|---------|------------|
| `SENTRY_DSN_WEB` | `release.yml`, both Docker build steps, as the `VITE_SENTRY_DSN` build arg | DSN of the `tday-web` project. Baked into the web bundle; its ingest origin is also added to the CSP `connect-src` |
| `SENTRY_DSN_ANDROID` | `release.yml`, the APK build step, as `SENTRY_DSN` | DSN of the `tday-android` project, baked into `BuildConfig.SENTRY_DSN` |
| `SENTRY_DSN_IOS` | `ios-testflight.yml`, the archive step, as `SENTRY_DSN` | DSN of the `tday-ios` project. It must match `https://<hex key>@<host>/<digits>` or the iOS build fails, even in a verify run |
| `SENTRY_AUTH_TOKEN` | `release.yml` (Android build, both Docker steps) and `ios-testflight.yml` (release mode only) | An Organization Auth Token for org `tday-kb`, upload scope only (confirm the exact scope in the Sentry UI when you create it) |

One token serves all three uses: the Android R8 mapping (Sentry Gradle plugin),
the web source maps (Sentry Vite plugin, passed to the Docker build as a build
secret, never a build arg), and the iOS dSYMs (`sentry-cli`). The backend DSN
stays a runtime environment variable and is not a GitHub secret.

A missing secret never fails a release. It produces a `::warning::` annotation
from the "Check the crash-report inputs" step, and that client ships inert (no
DSN: no consent card) or unsymbolicated (no token: no R8 deobfuscation, no iOS
symbolication, and the web source maps stay public).

### Sentry Project Settings (Release Blockers)

Do these before the first release that carries a DSN:

- Turn on **Prevent Storing of IP Addresses** (Security & Privacy settings) on
  all four projects. The clients never set an IP, but Sentry still sees the
  network address of every connection, and the in-app FAQ promises it is not
  stored.
- Confirm event retention is 30 days, the figure the FAQ states.

### Trace Sampling

Only the backend traces; the clients send no traces and have no sample-rate
input. The backend input is `SENTRY_TRACES_SAMPLE_RATE`, and it defaults to `0.1`
in production and `1.0` elsewhere. `/health`, `/api/mobile/probe`, `/ws`, and
`/calendar/*` are never traced. A backend that has the admin toggle off sends no
transactions at all; with it on, the sampled requests arrive as `http.server`
transactions next to the error events.

| Environment | Sample Rate |
|-------------|-------------|
| Local development | `1.0` |
| Staging / private test server | `0.5` |
| Production self-hosted | `0.1` (default) to `0.2` |

Raise production sampling temporarily only while investigating a specific issue.

## Release Artifacts

Before considering Sentry fully operational, verify release artifacts:

- Web source maps upload only when `SENTRY_AUTH_TOKEN` is configured, under the
  release `tday-web@<version>`. With a token, the build then deletes the public
  `.map` files from `dist`, so the unminified source is not served to visitors.
  A failed upload (unreachable Sentry, wrong token scope, wrong org or project
  slug) fails the Docker build, so the release stops before anything is
  published, the same as the Android and iOS uploads. The service worker is
  built without a source map (`injectManifest.sourcemap: false`), so no
  `sw.js.map` is ever served.
- Android R8 mapping/native metadata upload only when `SENTRY_AUTH_TOKEN` is
  configured. A release stack that is still obfuscated means the upload was
  skipped.
- iOS dSYMs upload from the `beta` Fastlane lane (`upload_dsyms_to_sentry`) with
  `sentry-cli debug-files upload --org tday-kb --project tday-ios`, before the
  TestFlight upload, in release mode only. With a token set, a missing dSYM zip
  or a failed upload fails the lane while the build number is still free; with
  no token the step is skipped. Without dSYMs iOS stacks do not symbolicate.
- Backend release uses `TDAY_BACKEND_VERSION`, then `TDAY_APP_VERSION`, then
  the embedded `version.json` value; it should not stay stuck on an old hardcoded app version.
- The release shown in Sentry matches the version shipped to users.

### Maintaining The sentry-cli Pin

`ios-testflight.yml` installs `sentry-cli` in the "Install sentry-cli" step from
a direct GitHub release download, pinned to one version and checked against that
release's SHA-256 before it runs (`SENTRY_CLI_VERSION` and the digest in
`.github/pinned/cli-darwin-universal.sha256`, kept in its own file because a bare
64-hex string beside the word "sentry" reads as an access token to secret scanners).
It is pinned, not fetched through an installer script or `latest`, because the
binary runs with `SENTRY_AUTH_TOKEN` in its environment: what executes must be
exactly the file named in the workflow.

To move the pin, take the new version's `sentry-cli-Darwin-universal` digest
from its GitHub release page and change **the version and the SHA-256 together**.
A wrong digest fails closed: the step exits non-zero and adds nothing to `PATH`.

## Suggested Alerts

Create alerts per project:

| Alert | Why |
|-------|-----|
| New issue in production | Catches brand-new failures after deploy |
| Regression after resolved issue | Catches reintroduced bugs |
| Event spike above baseline | Catches loops, outages, or bad releases |
| Spike in out-of-memory or watchdog events (mobile) | The clearest memory signal Sentry has; it does not detect leaks (see `TELEMETRY.md`) |
| High p95/p99 transaction duration on API routes (backend) | Catches backend or network slowness |
| Frequent `security.event` breadcrumbs (backend) | Surfaces lockouts, rate limits, and auth abuse signals |
| Events without a release value | Finds broken release wiring |

Crash-free sessions/users alerts are not available for the clients: they send no
session data by design, so Sentry has no denominator. Opt-in rates will be low
and the sample is biased toward engaged users, so read client event counts as a
floor, not a rate.

Use issue ownership rules to route backend, web, Android, and iOS failures to the
right code area.

## Failure Triage

When a Sentry issue appears:

1. Confirm platform, environment, release, and first-seen time.
2. Check the `tday.operation` tag (or the `operation` tag on a `slow_operation`
   event), the `client`, `app_version`, and `mode` tags, the `tz_offset` and
   `locale_lang` tags for date and language bugs, and breadcrumb messages such
   as `sync.replay`, `server.probe`, `realtime.connect`, `reminder.reschedule`,
   `update.check`, `local_mode.enter`, or `api.request`. Client events carry no
   user, IP, or install ID, so one report cannot be traced to a person or
   deleted per person.
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
| Sync | `sync.replay`, `sync.replay_rate_limited`, `sync.manual`, `local_mode.sync_noop` |
| Server setup | `server.probe`, `update.check` |
| Realtime | `realtime.connect`, `realtime.disconnect` |
| Reminders | `reminder.reschedule`, `reminder.cancel_all` |
| Calendar/tasks | `calendar.page`, `calendar.task.reschedule`, `task.reschedule` |
| Lists | `list.create`, `list.update`, `list.delete` |
| Credentials | `credential.request`, `credential.result`, `credential.save` |
| Security | `security.event` with reason code only |
| Slow operations | `slow_operation` events (fixed message, fingerprint `["slow_operation", <operation>]`, `operation` and `duration_bucket` tags); thresholds are in `TELEMETRY.md`. Helpers exist on every client but no call site reports yet |

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
- Platform helpers remain the single place for manual breadcrumbs and captures.
- Route-like data keys are sanitized by helper code.
- Required docs and checklists exist.

The consent and failures-only rules (one consent-gated SDK init per platform,
session tracking off, sample rate `0`, empty trace propagation targets on the
clients) are pinned by the guardrail test, so run it too:

```bash
cd tday-web && npx vitest run tests/guardrails/sentry-privacy.test.ts
```

Then run the platform tests from `docs/TESTING.md`.

## Production Smoke Drill

Use a staging or private test deployment. Do not add a public production endpoint
that intentionally crashes.

1. Configure DSNs for all platforms you want to test: the backend through its
   runtime environment, the clients through a build that carries a DSN (a
   release build, or `SENTRY_DSN` / `VITE_SENTRY_DSN` / the iOS build setting on a
   staging build). Backend trace sampling is the only sample rate.
2. Deploy backend/web and install mobile builds from the same release version.
3. Check that off means off before turning anything on. With the client's consent
   unanswered, and again after "Not now" or switching it off, trigger a failure
   (step 5) and confirm nothing reaches Sentry. Then trigger a failure while it
   is off, turn consent on, relaunch, and confirm that earlier failure never
   arrives (the timestamp guard). Repeat for the backend with its toggle.
4. Turn consent on. On a client, answer "Share reports" on the consent card or
   switch on Settings → Privacy → Crash & problem reports. On the backend, sign
   in to the web app as an admin and switch on Settings → Privacy → Server error
   reports; the row only appears when `SENTRY_DSN` is set and the workspace is in
   Server Mode.
5. Trigger a safe, expected failure. Android debug builds have a trigger
   (`adb shell am broadcast -n com.ohmz.tday.compose/.debug.TelemetryDebugReceiver
   -a com.ohmz.tday.compose.debug.TELEMETRY_TRIGGER --es kind crash|anr|slow_op`;
   an ANR is reported on the next launch). Web, iOS, and the backend have no
   debug trigger: use a throwaway build that fails on purpose, and do not merge
   it. An invalid authenticated request, a disconnected server probe, or a
   blocked realtime reconnect is not a client report by itself (failed requests
   are not captured), but it shows up as a breadcrumb on the next failure.
6. Verify Sentry receives:
   - Correct project and environment.
   - Correct release/dist.
   - Sanitized route template.
   - Structural breadcrumbs.
   - The `client`, `app_version`, `mode`, `tz_offset`, and `locale_lang` tags on
     client events, and the `client.platform` and `client.version` tags on
     backend events from Android and iOS requests.
   - A symbolicated, deobfuscated, or un-minified stack.
   - No `user`, IP address, install ID, self-hosted server address, email,
     raw URL, auth/session/cookie value, task title, list name, or local-only
     content. A web event is the one place a site appears: the browser sends
     `Origin` with the report, so Sentry knows which site it came from (no
     `Referer`, no URL in the body). On Android, check an ANR's thread names and
     library paths for the server's name or the install directory.
7. Resolve the test issue or mark it as ignored with a clear note.

## When More Context Is Needed

Prefer improving diagnostic structure before raising sample rates:

- Add a new structural breadcrumb at the start and failure edge of the operation.
- Add counts or enum-like result classes.
- Add a route template or status code if relevant.
- Add a focused sanitizer test when introducing a new data field.

Do not add engagement analytics, ad tracking, session replay, or user behavior
funnels for failure debugging.

# ADR 009: Crash reporting becomes opt-in and failures-only

**Status:** Accepted
**Date:** 2026-10-01

## Context

Sentry was added on 2026-04-04 (`4b79a1b`) as an operator-controlled diagnostic: it ran whenever a DSN was supplied, on all four platforms, with SDK defaults for everything not explicitly set. Two things were true of that design and neither matched what `docs/TELEMETRY.md` promised.

First, it was not consent-based. The mobile and web clients initialised the SDK unconditionally, and the only brake was an empty DSN. No published artifact carried a DSN, so in practice only the owner's backend reported, but the moment an official build was given one, every install would have reported with no choice offered.

Second, it was not failure-only. The SDK defaults send release-health session pings on every launch and sampled performance traces on healthy sessions, which is closer to product analytics than to "tell me when something breaks." The defaults also leaked more than the docs claimed: a per-install UUID attached to every event (`user.id`, and `device.id` on Android), self-hosted server URLs and raw IDs in HTTP breadcrumbs and spans, unscrubbed exception messages, Jetpack navigation arguments such as `todos/list/{listId}/{listName}`, a `Referer` header on web, and logcat lines turned into breadcrumbs.

The goal: learn why a crash, hang or memory kill happened and on what device, well enough to reproduce it in an emulator, from users who chose to share that, without ever receiving an IP address, location, account detail or database content.

## Decision

- Client crash reporting (web, Android, iOS) is **opt-in, default off, per device**. Consent is tri-state — `unanswered`, `granted`, `denied` — where `unanswered` behaves as off. It is a local preference, readable synchronously before the SDK starts, and it survives sign-out and local-data clears. It is not synced and not tied to an account or workspace mode.
- Before consent the SDK is **never initialised**. Nothing is buffered or queued, and the SDK installs no handler. Failures during the first run, including onboarding, are lost by design. No telemetry about consent changes is ever sent.
- Withdrawing consent closes the SDK and **purges on-disk state** (Android `cacheDir/sentry`; iOS `<Caches>/io.sentry` and `<Caches>/SentryCrash`), because both SDKs persist crash reports and send them on a later launch. Granting consent purges first and then drops any event whose timestamp predates the grant, which blocks Android's `ApplicationExitInfo` history replay (up to 91 days) and the iOS equivalents.
- Clients send **failures only**: crashes, ANRs and hangs, out-of-memory and watchdog kills, unhandled errors, and a custom `slow_operation` event when an operation exceeds a fixed threshold. As shipped in 0.8.0 (see [TELEMETRY.md](../TELEMETRY.md#what-triggers-a-report-in-080)), Android reports crashes (native and Java out-of-memory included), ANRs, and captured errors; iOS reports crashes, hangs over 2 s, watchdog terminations, and captured errors; web reports unexpected errors only; and `slow_operation` has a helper but no call site yet. There are no session pings and no sampled traces (`tracesSampleRate` 0, auto session tracking off, no tracing integrations, trace propagation off). Performance failures are modelled as events, not spans, so they survive in every SDK mode.
- Events are scrubbed on the device against an allow-list: user and install ID removed, IP never set, URLs reduced to route templates, exception messages stripped of hosts, addresses, identifiers and database fragments, device name and full locale and IANA time zone dropped. A coarse `UTC+N` offset and a two-letter language are kept as tags, because T'Day's bugs are date- and DST-sensitive, and are disclosed in the FAQ.
- The consent card appears once, **after** the wizard, when the workspace first opens (Server or Local Mode). None of the three wizards has a final step: each is a connect-and-sign-in flow that ends when it unmounts, and on web the sign-in redirect unmounts it immediately. Existing installs see the card once. The Settings toggle (Privacy) and a "?" to the new `crash-reports` guide topic are the durable surface.
- The owner's DSN is injected by CI into the release APK, the iOS TestFlight build and the web Docker image. It is a public, build-time constant; the consent gate is what makes that safe. The web CSP allows exactly that ingest origin. Symbolication artifacts (R8 mapping, web source maps, iOS dSYMs) upload with a CI-only auth token.
- The **backend** keeps `SENTRY_DSN` as the master switch and adds a server-wide admin toggle in web Settings, stored in a new `instance_settings` table (`V32`), default off. The SDK initialises at process start when a DSN exists, and an in-memory gate drops every outgoing item — a transport wrapper first, then `beforeSend`, `beforeSendTransaction`, `beforeBreadcrumb` and `tracesSampler` — until an admin enables it. The gate is read from the database once it is up, so no `close()` and re-init is needed. Backend data is operator-owned and holds no device or user content, so it may keep sampled tracing when enabled.

## Rationale

- Crash data is not "strictly necessary" for a service the user asked for, so opt-in consent is the safe basis under ePrivacy, and it matches how comparable products treat diagnostics (Home Assistant, JetBrains, Firefox, VS Code `crash` level).
- Not initialising the SDK is the only way to get zero activity. An SDK started with `enabled = false` or an empty DSN still installs global handlers and, on some platforms, accumulates breadcrumbs that attach to the first event after consent.
- `close()` alone is not withdrawal: sentry-java and sentry-cocoa both keep cached envelopes and crash reports on disk and send them on the next init. Purging is the part that makes "off means off" true.
- Failures-only is the only reading of "like Crashlytics, not analytics" the FAQ can state honestly. Sampled traces and session pings send healthy-session data from users who did not hit a failure.
- A per-device local flag, rather than an account preference, matches where the SDK decision must be made — at process start, before auth, with no network, and in Local Mode where there is no server — and keeps each user's choice out of the operator's database.
- A single allow-list scrub per platform is easier to test than chasing individual leaks. A golden-event test pushes an event full of UUIDs, hosts, IPs and emails through it and greps the serialised result.
- An in-memory gate on the backend avoids the init-before-database ordering problem and the close/re-init lifecycle, and wrapping the transport covers events, transactions, check-ins, sessions and client reports at one choke point.

## Consequences

- Opt-in rates will be low and the sample biased toward engaged users. There are no crash-free-session metrics, by design.
- Sentry does not detect memory leaks; it reports OOM and watchdog kills. Leak hunting stays LeakCanary (Android debug only), Instruments and `XCTMemoryMetric` (iOS), plus platform-side signals (Play Console vitals, Xcode Organizer).
- The release now ships a DSN, reversing the README claim that published builds do not. `README.md`, `docs/TELEMETRY.md`, `docs/SENTRY_RUNBOOK.md`, `docs/DEPLOYMENT.md` and `docs/security/*` are corrected in the same change. The web CSP gains the client ingest origin.
- Each SDK init call lives in exactly one consent-gated file per platform, and `tday-web/tests/guardrails/sentry-privacy.test.ts` pins that. A new init anywhere else fails CI.
- The owner's own server goes silent after deploying this change until an admin switches the server toggle on.
- Apple App Privacy and Google Play Data Safety must declare Crash Data and Diagnostics even though collection is opt-in. There is no `PrivacyInfo.xcprivacy` yet; it becomes a pre-submission task if iOS distribution moves beyond TestFlight.
- A data subject cannot be located in Sentry, because reports carry no user ID. That is deliberate and stated in the FAQ.

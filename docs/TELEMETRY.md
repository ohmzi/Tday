# Telemetry & Crash Reporting

T'Day uses [Sentry](https://sentry.io) for crash reporting and performance
monitoring across all four platforms (backend, web, Android, iOS). This document
explains exactly what is collected, what is not, and why.

## TL;DR

- Sentry tells us **when the app crashes and where in the code it happened**.
- It does **not** tell us who you are, what tasks you create, or how you use the
  app.
- No personal data is collected. No user habits are tracked. No analytics.

---

## What Is Collected

Every Sentry event may contain the following **non-identifying** information:

| Data | Example | Why It Helps |
|------|---------|--------------|
| Stack trace | `NullPointerException at TodoService.kt:42` | Pinpoints the exact line of code that crashed |
| App version / build | `tday-android@1.13.0`, build `11300` | Tells us which release introduced or fixed a bug |
| OS version | `Android 14`, `iOS 17.4`, `Chrome 124` | Reproduces platform-specific bugs |
| Device model | `Pixel 8`, `iPhone 15` (generic model only) | Identifies hardware-specific rendering or memory issues |
| HTTP status codes | `500 Internal Server Error` on `PATCH /api/todo` | Reveals failing API endpoints without exposing request bodies |
| Route / screen name | `TodayPage`, `CalendarScreen` | Shows which screen the user was on when the error occurred |
| Performance timings | "request took 3200 ms" | Flags slow endpoints or UI jank before users report it |
| Session health | Crash-free rate per release | Measures overall stability across versions |
| Breadcrumbs | "HTTP GET /api/todo → 200", "navigated to /app/tday" | Reconstructs the sequence of actions leading to a crash |
| Environment tag | `production` or `development` | Separates real-world crashes from developer testing |

## What Is NOT Collected

The following data is **never** sent to Sentry:

| Excluded Data | How It's Enforced |
|---------------|-------------------|
| Task titles, descriptions, notes, or any user content | Sentry only receives stack traces and HTTP metadata — request/response bodies are never attached |
| Email addresses, usernames, display names | `sendDefaultPii = false` on every SDK; `beforeSend` callback strips any residual user fields |
| IP addresses | Explicitly nulled in every `beforeSend` callback across all four platforms |
| Cookies, auth tokens, session IDs | `sendDefaultPii = false` prevents header capture; sensitive headers are never attached |
| Location or GPS data | Not requested by the app, not sent to Sentry |
| Usage patterns, feature analytics, or engagement metrics | Sentry is a crash reporter, not an analytics platform — no events are fired for normal user actions |
| Screen recordings or session replays | `replaysSessionSampleRate` and `replaysOnErrorSampleRate` are both set to `0` on web; not available on mobile SDKs |
| Device identifiers (IMEI, serial, advertising ID) | Not collected; only generic model names (e.g. "Pixel 8") are included |
| Hostnames or server IPs | Backend `serverName` is hardcoded to `"tday-backend"`, never the real hostname |

## How This Helps Debug Issues

Without telemetry, when something breaks in production the developer has no
visibility — users experience a blank screen or a silent failure and the bug may
go unnoticed for days. Sentry changes that:

1. **Instant crash alerts** — When an unhandled exception occurs on any platform,
   Sentry creates an event with the full stack trace, breadcrumb trail, and
   environment info. The developer is notified immediately.

2. **Release health** — Every event carries the app version. Sentry's Release
   Health dashboard shows crash-free rates per version so regressions from a new
   release are caught within hours, not weeks.

3. **Performance monitoring** — Slow API endpoints and sluggish screen
   transitions are captured as performance transactions. This helps identify
   bottlenecks before they become user-facing problems.

4. **Breadcrumb trails** — When a crash occurs, the events leading up to it
   (HTTP calls, navigation changes) are included. This gives the developer the
   "story" of what happened without ever seeing the user's actual data.

5. **Source maps and ProGuard deobfuscation** — Minified JavaScript and
   obfuscated Android stack traces are mapped back to readable source code,
   making bugs actionable.

## Privacy Safeguards in Code

These aren't just policy — they're enforced programmatically in every SDK init:

```
sendDefaultPii = false          — disables automatic PII collection
beforeSend: strip IP address    — nulls out user.ipAddress on every event
beforeSend: strip email         — deletes user.email on web events
serverName = "tday-backend"     — generic tag, not the real hostname
replaysSessionSampleRate = 0    — no session replay recording
replaysOnErrorSampleRate = 0    — no error replay recording
```

The relevant code paths:
- Backend: `Application.kt` → `Sentry.init { ... }`
- Android: `TdayApplication.kt` → `SentryAndroid.init { ... }`
- Web: `main.tsx` → `Sentry.init({ ... })`
- iOS: `SentryConfiguration.swift` → `SentrySDK.start { ... }`

## Self-Hosted Users

If you self-host T'Day and build from source, Sentry is **completely inactive**
unless you set the `SENTRY_DSN` environment variable. With an empty or missing
DSN, the Sentry SDK initializes in no-op mode and sends nothing.

## Questions

If you have concerns about what data is collected, every `beforeSend` callback
and SDK init is fully auditable in the source code. Search the codebase for
`Sentry.init`, `SentryAndroid.init`, or `SentrySDK.start` to review the exact
configuration.

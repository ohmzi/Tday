# Test crashes (temporary)

> **Temporary.** These buttons exist only to check that crashes from every kind of screen reach Sentry from all three
> apps. They are in the regular build on purpose and are removed by reverting the single commit that added them
> ("TEMPORARY: add test-crash buttons…"). Every touch point is tagged `TEST-CRASH`:
> `grep -rn "TEST-CRASH" tday-web/src android-compose/app/src ios-swiftUI/Tday`.

## Before you test

- Turn **Crash & problem reports** on (Settings → Privacy, or answer "Share reports" on the card). With it off the
  button still crashes the app, but nothing is sent.
- The build must carry a DSN. Release builds get one from CI (`SENTRY_DSN_WEB`, `SENTRY_DSN_ANDROID`,
  `SENTRY_DSN_IOS`); a local build only reports if you gave it one (`local.properties:sentryDsn`, the `SENTRY_DSN` Xcode
  build setting, or `VITE_SENTRY_DSN`).
- Native crashes are stored and sent on the **next launch**, so reopen the app after a crash. Web errors are sent
  immediately. The iOS hang is sent while the app is still running.

## What each button does

A red button labelled **Test crash: `<ID>`**, with a muted line under it, sits in the middle of the screen's content. Every
error message is exactly `TEST-CRASH <ID>: <description>`, so one search finds all three apps:

```
message:"TEST-CRASH TC-LIST-SCHED"        # one trigger, every platform
message:"TEST-CRASH"                      # all of them
tag:client:android  (or ios, web)         # one platform
```

Where a platform's runtime produces its own message (for example a Swift force-unwrap or an Android out-of-memory),
the title is the runtime's and the ID is in the `test_crash` breadcrumb on the event instead.

Every trigger is also **its own Sentry issue**: the harness fingerprints each one as `test-crash:<ID>`. Two screens that
raise the same error kind, and two failures reported from the same route, stay separate issues instead of collapsing into
one — which is what put `TC-BUILTIN-TODAY` inside `TC-CALENDAR`'s issue, and the two server triggers in one, before this.

Two title traps are handled in each client's `beforeSend`, after the scrubber:

- **iOS** rewrites the exception value from the trigger's breadcrumb and clears the synthetic flag, because a Swift trap
  reaches Sentry with no exception value and Sentry otherwise titles the issue after the crashing symbol
  (`closure in _assertionFailure`).
- **Web** does the same for a `DOMException` trigger: the SDK marks those synthetic and sends no exception type, so the
  issue was titled after whatever SDK frame caught it (`captureException`, `AsyncContextStack$3.withScope`). The
  fingerprint alone would have separated them; clearing the flag is what makes the title readable.

The **first task of any list** (every feed, built-in list, user list, the calendar and Completed) crashes when you open it
and crashes differently when you edit it. Android: tap the row / swipe it and tap Edit. iOS: tap the row / swipe left and
tap Edit. Web: click the row body / open its Edit form.

## Cross-check

Tick a row once the report shows up in Sentry from that app. Each screen uses a different crash kind on each platform.

| Done | ID | Where | Android | iOS | Web |
|---|---|---|---|---|---|
| ☐ | `TC-FEED-SCHED` | Scheduled home feed | IllegalStateException | `fatalError` | TypeError |
| ☐ | `TC-FEED-ANY` | Anytime feed | NullPointerException | array index out of range | RangeError |
| ☐ | `TC-BUILTIN-TODAY` | Today list | IndexOutOfBoundsException | `preconditionFailure` | ReferenceError |
| ☐ | `TC-BUILTIN-OVERDUE` | Overdue list | ArithmeticException | nil force-unwrap | SyntaxError |
| ☐ | `TC-BUILTIN-SCHED` | Scheduled list | ClassCastException | integer overflow | EvalError |
| ☐ | `TC-BUILTIN-ALL` | All tasks list | NumberFormatException | divide by zero | Error |
| ☐ | `TC-BUILTIN-PRIO` | Priority list | UnsupportedOperationException | invalid pointer (EXC_BAD_ACCESS) | URIError |
| ☐ | `TC-BUILTIN-DONE` | Completed | ConcurrentModificationException | `try!` | InvalidStateError |
| ☐ | `TC-LIST-SCHED` | Your scheduled list | IllegalArgumentException | duplicate dictionary keys | NotFoundError |
| ☐ | `TC-LIST-ANY` | Your Anytime list | NoSuchElementException | negative array count | DataCloneError |
| ☐ | `TC-TASK-OPEN` | First task, opened | ArrayIndexOutOfBoundsException | forced cast `as!` | TypeError (unhandled rejection) |
| ☐ | `TC-TASK-EDIT` | First task, edited | NegativeArraySizeException | invalid range `5..<3` | RangeError |
| ☐ | `TC-NEW-LIST` | Create-list sheet | ArrayStoreException | negative → `UInt` | QuotaExceededError |
| ☐ | `TC-NEW-TASK` | Create-task sheet | IllegalMonitorStateException | `Int8(1000)` narrowing | SyntaxError (unhandled rejection) |
| ☐ | `TC-CALENDAR` | Calendar | DateTimeException | `removeFirst()` on empty | ReferenceError (unhandled rejection) |
| ☐ | `TC-SET-CRASH` | Settings, fatal | SecurityException | String index out of bounds | Error |
| ☐ | `TC-SET-ERROR` | Settings, **handled** (app keeps running) | IOException via `TdayTelemetry.capture` | NSError via `TdayTelemetry.capture` | Error via `captureUiException` |
| ☐ | `TC-SET-FREEZE` | Settings, main-thread freeze | ANR (13 s, **Stop** ends it early; reported next launch, Android 11+) | App Hanging (6 s, **Stop** ends it early; sent immediately) | busy loop 6 s, then a **Reload** notice (no hang detector on web) |

Platform extras, all on Settings:

| Done | ID | Platform | What |
|---|---|---|---|
| ☐ | `TC-SET-OOM` | Android | allocates until a real `OutOfMemoryError` crash |
| ☐ | `TC-SET-NSEXC` | iOS | raises an `NSException` |
| ☐ | `TC-SET-REJECT` | Web | unhandled promise rejection |
| ☐ | `TC-SET-RENDER` | Web | a component throws while rendering; the error boundary shows the error screen |

## Getting out of the freeze

A blocked main thread cannot draw or take a tap, so every platform has a way back rather than a dead end:

- **Android and iOS** run the freeze in half-second slices and put a **Stop** control where the trigger was, so a queued
  tap ends it at the next slice instead of waiting out the whole budget. Each platform keeps a first window *solid* — the
  block never returns to its run loop, so the platform's own detector still fires — and only starts servicing the loop
  afterwards: 6 s of the 13 s on Android, where an input-dispatch ANR needs ~5 s, and 3 s of the 6 s on iOS, where the
  display-link hang tracker needs 2 s. Servicing the loop earlier would make the app look responsive and the hang would
  never be reported. The consequence is honest and bounded: a Stop tapped during that window takes effect when it ends,
  then within a slice. Android also cancels when the tap lands on the *stale* frame the trigger occupied — a frozen thread
  cannot redraw, so the button the user actually hits may still be the trigger, and that path cancels too. After it ends
  the panel says how long the main thread was blocked, or that it was cancelled.

  Two device-verified details worth keeping if this is ever reworked: the slices need a short idle gap (~32 ms) between
  them, or the frame pipeline never gets a turn and the Stop control is never drawn; and the solid window must run inside
  the trigger's own input dispatch, because a nested `Looper.loop()` cannot deliver pointer events (Compose drops
  re-entrant input), so a hold that never returns to the looper is untappable by construction.
- **Web** keeps the freeze an honest block — nothing can interrupt a synchronous loop — and offers the way out the moment
  the thread is free: a notice reading *"T'Day was unresponsive for N s"* with a **Reload** action. The same notice covers
  any real hang, and `TC-SET-RENDER` leaves the error boundary's own error screen, which also reloads.

## Notes

- Web "crashes" are uncaught errors fired from a click so the app's global handlers see them (a timer, a microtask or an
  unhandled rejection), not a process death. `TC-SET-RENDER` is the one that takes the whole UI down until reload.
- iOS fatal triggers and Android out-of-memory are sent best-effort at the moment of the crash and guaranteed on the next
  launch.
- The event also carries the `client`, `app_version`, `mode`, `tz_offset` and `locale_lang` tags, and no user, IP or
  server address, as for any report.

## Removing it

```bash
git revert <the TEMPORARY test-crash commit>      # preferred: it is a single commit
```

If the revert conflicts, delete the new files (`tday-web/src/lib/testCrash.ts`, `tday-web/src/components/TestCrashButton.tsx`,
`tday-web/tests/unit/test-crash.test.ts`, `tday-web/tests/unit/web-test-crash-title.test.ts`,
`android-compose/.../core/testcrash/`, `ios-swiftUI/Tday/Core/TestCrash/`,
`ios-swiftUI/Tests/TdayCoreTests/TestCrashTests.swift`, this file) and remove every line or block tagged `TEST-CRASH`,
the two `test_crash_*` strings in `android-compose/app/src/main/res/values/strings.xml`, and the `TestCrash` entries in
`ios-swiftUI/TdayApp.xcodeproj/project.pbxproj`.

Three things that came out of this work are **not** part of the harness and stay when it goes: the unresponsive-thread
notice (`tday-web/src/hooks/useUnresponsiveNotice.ts` and its `app.unresponsive*` strings), and the backend's
per-failure fingerprint support (`observability/FingerprintedFailure.kt`, the `fingerprint` parameter on
`TdayObservability.captureException`, and the line in `StatusPages` that passes it through).

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
| ☐ | `TC-SET-FREEZE` | Settings, main-thread freeze | ANR (13 s; reported next launch, Android 11+) | App Hanging (6 s; sent immediately) | busy loop 6 s, no report expected (no hang detector on web) |

Platform extras, all on Settings:

| Done | ID | Platform | What |
|---|---|---|---|
| ☐ | `TC-SET-OOM` | Android | allocates until a real `OutOfMemoryError` crash |
| ☐ | `TC-SET-NSEXC` | iOS | raises an `NSException` |
| ☐ | `TC-SET-REJECT` | Web | unhandled promise rejection |
| ☐ | `TC-SET-RENDER` | Web | a component throws while rendering; the error boundary shows the error screen |

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
`tday-web/tests/unit/test-crash.test.ts`, `android-compose/.../core/testcrash/`, `ios-swiftUI/Tday/Core/TestCrash/`,
`ios-swiftUI/Tests/TdayCoreTests/TestCrashTests.swift`, this file) and remove every line or block tagged `TEST-CRASH`,
the two `test_crash_*` strings in `android-compose/app/src/main/res/values/strings.xml`, and the `TestCrash` entries in
`ios-swiftUI/TdayApp.xcodeproj/project.pbxproj`.

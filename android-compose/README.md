# T'Day Native Android (Compose)

Native Kotlin + Jetpack Compose Android client for T'Day.

## Product Role

The Android app is a primary T'Day client, not a web wrapper. It should stay behaviorally aligned
with the iOS SwiftUI app while using Android-native implementation patterns.

Current feature surface:

- Local Mode for offline-only planning without server setup.
- Server Mode with JWE cookie auth, optimistic local writes, realtime refresh, and pending mutation
  replay.
- Scheduled task home and Floater task home root feeds controlled by `RootFeedDock`.
- Scheduled tasks, floaters, scheduled-task lists, floater lists, completed history, calendar,
  search, settings, reminders, an opt-in device-calendar mirror, Today/Floater/List home-screen widgets, an internal car-mode task surface,
  and in-app APK updates.
- Room-backed local cache with a one-time migration from the older encrypted JSON cache.

## Module

- `app`: Android application module.

## Structure

```text
android-compose/app/src/main/java/com/ohmz/tday/compose/
├── core/
│   ├── data/          # Repositories, Room cache, sync, auth/server stores
│   ├── model/         # API/domain models and UI-facing data
│   ├── navigation/    # AppRoute
│   ├── network/       # Retrofit, cookies, realtime, connectivity
│   ├── calendar/      # Opt-in device-calendar mirror (CalendarContract)
│   ├── notification/  # Reminders, boot receiver, workers
│   ├── observability/ # Opt-in crash reports: consent store, bootstrap, scrubber, gated transport
│   ├── security/      # Probe/decryption helpers
│   └── ui/            # Shared app UI helpers
├── feature/
│   ├── app/           # Bootstrap, Local/Server Mode, sync, version state
│   ├── auth/          # Login/register and credential handoff
│   ├── scheduledtaskhome/ # Scheduled task home root feed
│   ├── todos/         # Todo/Floater/List screens
│   ├── calendar/      # Month/week/day calendar
│   ├── car/           # Internal car-mode Today/Floater surface
│   ├── completed/     # Completed todo/floater history
│   ├── settings/      # Settings and admin toggles
│   ├── telemetry/     # Crash-reports consent: the wizard step (asked each sign-in) and the card
│   ├── release/       # Latest release and APK installer
│   └── widget/        # Today/Floater/List widgets (plain RemoteViews) and the refresh coordinator
└── ui/
    ├── component/     # RootFeedDock, sheets, pull refresh, controls
    └── theme/         # Colors, typography, dimensions
```

## Run

1. Open `android-compose/` in Android Studio.
2. Ensure the Android SDK 37 platform is installed (`compileSdk`; Gradle can fetch it once the SDK licenses are accepted). `targetSdk` stays at 35.
3. Run on emulator/device.

Useful command-line checks:

```bash
cd android-compose
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
```

## First Launch

The onboarding overlay offers two workspace paths:

- **Local Mode**: start immediately with local data only. Pull-to-refresh, server sync, realtime
  updates, and admin server settings are not active. Settings shows the workspace as local-only.
- **Server Mode**: configure a self-hosted T'Day URL, verify the mobile probe/version compatibility,
  then login/register. Settings shows Server sync status, last sync metadata, pending change count,
  and a duplicate-safe manual sync action.

Server URLs are normalized and persisted only after successful authenticated setup. Server URL
credentials use Android Credential Manager where available, while real login credentials and cookies
are stored through encrypted local stores.

### Cold launch

A cold launch holds the branded splash until `AppViewModel.bootstrap()` has resolved the persisted
session. `AppUiState.rootDestination` is the tri-state routing decision (`SPLASH` while the session
is `UNKNOWN`, then `WORKSPACE` or `ONBOARDING`), and `TdayApp` does not build the `NavHost` until
it has left `SPLASH`. Setting the graph is where Navigation handles the launch intent's deep link
(`tday://home` from the update notification, widget links) and restores a saved back stack after
process death, both of which land on `home` directly — and `home` shows the onboarding wizard
whenever no workspace is available, which is always the case before bootstrap has answered.
Building the graph only once the answer is known means the first real screen composed is already
the right one, so a signed-in user never sees sign-in on the way in. `SessionResolution` is
monotonic: sign-out and session expiry leave it `RESOLVED`, so the splash never returns mid-session.

While the graph is withheld there is no `NavController` back stack, and
`NavController.handleDeepLink` dereferences the graph unconditionally, so anything that reaches
for it must first check that a current entry exists — both `HandleStartupNavigation` and the
pending-deep-link effect in `TdayApp` do, and both keep `currentRoute` as an effect key so the
work is deferred rather than dropped. The window is real because the splash's tap-and-hold can
outlast the session resolving. Verification note: the routing decision and the ViewModel's
resolution writes are covered by `RootDestinationTest` and `AppViewModelTest`; the composition
gate itself — that `TdayApp` withholds the `NavHost` — has no automated test, because the module
has no Robolectric toolchain and an instrumented test of `TdayApp` would need the full Hilt graph
and a device. It is verified on device.

### Compiled code

The APK is installed from GitHub releases, so there is no Play cloud profile, and Android does not
read a profile embedded in an APK at install: every install or update starts as `verify`, with the
app's code interpreted and JIT-compiled. `androidx.profileinstaller` (pinned in
`app/build.gradle.kts`; the version that arrives transitively cannot install on Android 14+) writes
the APK's baseline profile on first launch, and ART compiles it at its next idle dexopt. That
profile is the libraries' own plus `app/src/main/baseline-prof.txt`, one wildcard over the app's
code, which R8 rewrites to the obfuscated names. To check the profile on a device without waiting
for idle:

```bash
adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE \
  com.ohmz.tday.compose/androidx.profileinstaller.ProfileInstallReceiver   # result=1 is installed
adb shell cmd package compile -m speed-profile -f com.ohmz.tday.compose
adb shell dumpsys package dexopt | grep -A2 com.ohmz.tday.compose          # status=speed-profile
```

## Version Compatibility

- `android-compose/app/build.gradle.kts` reads root `../version.json` for `versionName` and computes
  `versionCode` as `major * 10_000_000 + minor * 10_000 + patch` (`0.7.2` → `70002`). Android only
  installs an update whose `versionCode` is strictly greater than the installed one, so that encoding
  gives every component its own decimal slot and the build fails outright if `minor` exceeds 999,
  `patch` exceeds 9999, the code passes 2_100_000_000, or the code lands at or below the highest
  `versionCode` this project ever shipped.
- Server Mode sends `X-Tday-Client: android-compose` and `X-Tday-App-Version`.
- When `compatibility.updateRequired` is true, Android and the backend use exact version matching.
  The app shows installed/server/latest version state in Settings and blocks Server Mode with
  update/server-update guidance when versions differ.
- Do not edit Android version fields directly. Update `version.json`, then run
  `node scripts/version.mjs sync` and `node scripts/version.mjs check` from the repo root.

## Persistence and Sync

- Room stores todos, floaters, lists, floater lists, completed records, pending mutations, and sync
  metadata.
- `OfflineCacheManager` exposes `cacheDataVersion`; ViewModels reload from cache when it changes.
- `OfflineCacheManager` also exposes `syncMetadataVersion` for pending-mutation and sync-timestamp
  changes that should refresh status UI without forcing task-list reloads.
- Repositories write optimistically to Room first.
- In Server Mode, `SyncManager` replays pending mutations and refreshes snapshots.
- In Local Mode, pending mutations are cleared/ignored because there is no remote target.
- Logout or invalid session clears session/user data according to the auth flow.

See [`../docs/DATA_MODEL.md`](../docs/DATA_MODEL.md) for the shared cache model.

## Widgets

The Today Tasks and Floater Tasks widgets are plain RemoteViews built from XML layouts
(`layout/widget_task.xml`, `widget_task_list_row.xml`) and rendered from the same cache-backed task
models as the app. They do not use Glance: HyperOS 4's launcher ignores the runtime-assigned view ids
Glance depends on, which left its widgets blank and untappable (see `docs/WIDGET_SYNC.md`). Today shows pending scheduled tasks due today only; Floater shows active
unscheduled floaters across all floater lists. Completed tasks and overdue scheduled tasks stay out
of these widget surfaces.

- Android exposes small, medium, and large picker entries backed by separate AppWidget provider
  metadata for each widget kind. These picker choices are starting sizes; every placed entry shares
  the same 2x2-to-4x4 resize range and renders the matching responsive layout bucket as it is
  stretched or compressed.
- Static picker previews use RemoteViews-compatible XML, and Android 15+ generated previews are
  published from `TodayTasksWidgetPreviewPublisher` when the app starts.
- Medium and large layouts show the title, neutral count text, a mode-accented native plus icon add
  target, and dense scan-first rows; compact layouts stay count-first and prioritize task titles
  over due-time detail. The short wide bucket also suppresses due-time chips so resized widgets keep
  long task titles readable.
- All widget states keep a subtle oversized Today/Floater watermark in the background; the Today
  watermark follows the app title icon rule, showing the sun from 6 AM to 5:59 PM and the moon at
  night. Empty and setup states add centered message text over that persistent motif.
- Tapping Today widget content opens the app; tapping Floater widget content opens the Floater root.
- Task layouts keep the header and plus action fixed, then render a scrollable task body. The first
  viewport fits the highest-value rows for the current compact, wide, medium, or tall bucket, and
  reveals the remaining cached rows as the user scrolls. Task bullets use the task priority color.
  Widget rows are capped at 50.
- The add actions open `tday://todos/create?target=today` or
  `tday://todos/create?target=floater` through a dedicated translucent widget-create activity that
  hosts the matching in-app create-task sheet directly over the launcher without auto-focusing the
  title field or opening the keyboard. Floater creation hides schedule controls and creates an
  unscheduled floater.
- `OfflineCacheManager` requests widget refreshes after local cache changes, so Local Mode and
  optimistic writes update the widget without waiting for a server sync path.

Keep future widget work responsive across compact, wide, and tall sizes, preserve large add/content
tap targets, keep the persistent watermark calm behind both rows and empty text, reserve
Today/Floater accent treatment for the plus add button, and prefer system widget bounds, dynamic
color, and native RemoteViews idioms over custom chrome.

### List widget (per-instance configuration)

The third widget kind, beside Today and Floater, shows one list the user picks for that placed
instance. There are exactly three kinds — Today, Floater, List — each offered in Small, Medium and
Large (`ListWidgetSmallReceiver`, `ListWidgetReceiver`, `ListWidgetLargeReceiver`).

- **It lands unconfigured.** Its provider info declares `widgetFeatures="reconfigurable|configuration_optional"`,
  so on Android 12+ the launcher places it straight away. It shows the setup picture
  (`widget_list_setup_art`, drawn the same on iOS) and "Choose a list"; a tap anywhere — "+"
  included — opens `WidgetListPickerActivity` for that `appWidgetId`. The same activity is the
  `android:configure` target, so older launchers open it at placement and every launcher offers it
  again from the widget's long-press reconfigure.
- **The picker** lists every scheduled and floater list from the offline cache with its open count,
  writes the selection and the instance's first snapshot, and repaints it. With no lists it says so
  and offers to open the app.
- **Content is the whole list.** A scheduled list shows every open task in it, whatever day it is
  due: today's rows carry their time and any other row its day ("Sep 30"), tinted when overdue. A
  floater list shows its open floaters undated. Both count "open" and empty to "Nothing left in this
  list".
- **"+" creates straight into the list** — its task type, the list preselected, and so the list's own
  default priority, which the create sheet applies.
- **Configuration is per `appWidgetId`**, not per kind — `WidgetListSelectionStore` (SharedPreferences,
  keyed by widget id) and a `widget-list-snapshot-<appWidgetId>.json` file exist per placed instance,
  so two List widgets can show two different lists. `WidgetSnapshotWriter` rebuilds every configured
  instance on each cache write and prunes selections whose id now belongs to another kind; the
  receiver's `onDeleted` clears both stores for a removed instance.
- **A deleted list** makes its widget ask again ("That list was deleted. Tap to choose another.");
  a renamed one shows its new name on the next write, since the snapshot carries the cache's name.
- While app lock is on, the widget shows the lock and the generic "List" title, never the list name.
- Every receiver, List included, renders its own ids straight from `onUpdate` (no WorkManager
  session), so a List widget repaints after a reboot on the same timeline as Today/Floater.

## Car Surface

Android keeps the car task UI as an app-internal adaptive surface at `tday://car`. It does not
declare Android Auto or Android Automotive production metadata because Google Play's current car app
categories do not include generic task/calendar productivity apps. The surface defaults to Today,
switches to Floater with icon-only controls, uses voice capture for the plus action when speech
recognition is available, and falls back to the existing create-task flow otherwise.

## Natural-language scheduling

The new-task title field recognizes date/time phrases **on-device** with
[Natty](https://github.com/joestelmach/natty) (`com.joestelmach:natty`) — no network and no AI, so
it also works in Local Mode. Typing e.g. "pay rent on the 1st at 9am" auto-sets the Due, highlights
the recognized phrase in place, and strips it from the saved task title.

- `core/data/todo/OnDeviceTitleNlpParser.kt` wraps Natty and returns the matched span, the cleaned
  title, and the due instant (mirroring the previous backend parser, so a bare "8pm" is never
  shifted).
- `TodoRepository.parseTodoTitleNlp` is the single entry point used by every create-task surface
  (scheduled task home, Calendar, List, widget); swapping it to the local parser made them all offline at once.
- `CreateTaskBottomSheet` keeps the full typed text, highlights the phrase via a Compose
  `VisualTransformation`, sets the Due, and removes the phrase from the title only on submit.
- Parsing runs in the device timezone; the due is saved as a UTC instant. Release builds keep
  Natty/ANTLR via `proguard-rules.pro`.

## Crash reports (opt-in)

Off by default and per device. Nothing starts, is queued or is stored until the person says yes. The
question is the last step of the sign-in wizard — a "Privacy" chip beside Mode, Server and Login,
which the wizard is held on screen for once a workspace opens (Server and Local Mode alike). It is
asked on **every** sign-in: a device that answered before is asked again, and the answer on record
stays in force until the new question replaces it. The
after-sign-in card asks the same thing when the step cannot: an install that is already signed in at
launch, a restart mid-step, or a failed sign-in or gate that took the wizard's place. It can also be
answered any time in Settings → Privacy → "Crash & problem reports", which has a "?" to the
`crash-reports` guide topic. Mobile keeps its per-device answer; the web and the server are governed
by the admin's instance setting instead ([ADR 010](../docs/adr/010-instance-governed-web-crash-reports.md)).
The full privacy contract is in `docs/TELEMETRY.md`.

- **No DSN, no surface.** The DSN comes from `SENTRY_DSN` (environment or `sentryDsn` in
  `local.properties`). Empty (forks, self-built APKs) hides the step, the card and the Settings row and the
  SDK never starts. `SENTRY_AUTH_TOKEN` only gates the R8 mapping upload.
- **One entry point.** `core/observability/TelemetryBootstrap` is the only code that starts or stops
  Sentry. `TdayApplication.onCreate` calls `start()`, so a boot, widget or alarm process is covered:
  consent granted starts the SDK; any other state deletes whatever an older build left under
  `cacheDir/sentry`. Auto-init and Sentry's content providers are removed in the manifest.
- **Consent store.** `TelemetryConsentStore` keeps `state` and `granted_at_ms` in plain
  SharedPreferences (`telemetry_consent_prefs`), readable in `onCreate`. It is not part of
  `SecureConfigStore` and is never cleared by sign-out or `clearAllLocalData`: the choice belongs to
  the device. `beforeSend` drops any event older than `granted_at_ms`, so an ANR replayed from the
  system's exit history can never arrive after opt-in. The store uses `commit()` and returns whether
  the write reached the disk: `TelemetryBootstrap.apply` returns that result, retries a failed "no"
  once, and if it still fails the process obeys the "no" anyway (gate closed, SDK stopped, files
  purged) and logs `consent.deny.not_persisted` locally with `Log.w`, never to Sentry. No fallback
  marker is kept (it would go to the same disk). A failed "yes" does not start the SDK and puts the
  answer back to denied.
- **Switching.** The card and the Settings row both go through `TelemetryConsentManager` (Hilt
  singleton). Off closes the in-memory gate, then the SDK, then deletes its files, without a flush;
  on purges first and starts fresh.
- **Failures only.** `TelemetryOptions` sets sample rate 0, no tracing, session tracking, client
  reports or trace headers, and every `dataCollection` field explicitly. `TelemetryScrubber` removes
  the user, install id, hosts, IPs, emails and ids from every event and keeps only allow-listed
  breadcrumbs; `GatedTransportFactory` is the last check before the network. Message text is scanned
  only up to 2000 characters, redacts what parsers quote back (`JSON input:`, `Text '...' could not be
  parsed`, `For input string:`), and cuts to 300. An ANR event also brings thread names and library
  paths: thread names pass an allow-list (`OkHttp tday.example.com` becomes `OkHttp`), and stack-frame
  and debug-image paths are reduced to the file name, so neither names the server or the install dir.
- **What is reported.** Uncaught crashes (native crashes and Java out-of-memory crashes included),
  ANRs (read from the system's exit history on the next launch), and errors passed to
  `TdayTelemetry.capture`. A kernel low-memory kill of a background process leaves no event yet.
- **Slow operations.** `SlowOperation` is the helper for the `slow_operation` event. No call site
  uses it yet, so a release build sends none; only the debug receiver's `slow_op` trigger does.
- **Guide.** `crash-reports` shows under What's New and in its section. The What's New copy is keyed
  `whats-new:<id>` (`guideCardKey` in `HelpGuideScreen.kt`), so a deep link, which uses the plain id,
  expands and scrolls to the section card only.
- **Debug builds** include LeakCanary (Sentry reports a Java out-of-memory crash, never a leak) and
  `TelemetryDebugReceiver`:

```bash
adb shell am broadcast -n com.ohmz.tday.compose/.debug.TelemetryDebugReceiver \
  -a com.ohmz.tday.compose.debug.TELEMETRY_TRIGGER --es kind crash   # or anr, slow_op
```

## Mobile Parity

For user-facing Android changes, compare the iOS implementation in `ios-swiftUI/Tday/Feature/` and
`ios-swiftUI/Tday/Core/`. Match behavior, counts, empty states, Local Mode affordances, and
navigation rules while keeping Android Compose idioms.

## Theme

- Theme mode can be changed in Settings: `System`, `Light`, or `Dark`.
- Selection is applied immediately and is cleared when unauthenticated data wipe runs.
- New shared dimensions belong in `ui/theme/Dimens.kt` before screen code uses them.
- New Material palette values belong in `ui/theme/Color.kt`; repeated domain colors belong in
  `ui/theme/TdaySemanticColors.kt` (`tdayPriorityColor`, `tdayListAccentColor`,
  `TdayListColorOptions`, mode accents, and related semantic tokens).
- List icon options and persisted key lookup belong in `ui/theme/TdayListIcons.kt`, using
  `TdayListIconOptions` and `tdayListIconForKey` instead of screen-local maps.
- User-facing Android copy belongs in `res/values/strings.xml`; repeated copy such as splash
  taglines should use string arrays rather than Kotlin lists.

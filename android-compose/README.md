# T'Day Native Android (Compose)

Native Kotlin + Jetpack Compose Android client for T'Day.

## Product Role

The Android app is a primary T'Day client, not a web wrapper. It should stay behaviorally aligned
with the iOS SwiftUI app while using Android-native implementation patterns.

Current feature surface:

- Local Mode for offline-only planning without server setup.
- Server Mode with JWE cookie auth, optimistic local writes, realtime refresh, and pending mutation
  replay.
- Home and Floater/Anytime root feeds controlled by `RootFeedDock`.
- Scheduled tasks, floaters, scheduled-task lists, floater lists, completed history, calendar,
  search, settings, reminders, Glance Today/Floater widgets, and in-app APK updates.
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
│   ├── notification/  # Reminders, boot receiver, workers
│   ├── security/      # Probe/decryption helpers
│   └── ui/            # Shared app UI helpers
├── feature/
│   ├── app/           # Bootstrap, Local/Server Mode, sync, version state
│   ├── auth/          # Login/register and credential handoff
│   ├── home/          # Home root feed
│   ├── todos/         # Todo/Floater/List screens
│   ├── calendar/      # Month/week/day calendar
│   ├── completed/     # Completed todo/floater history
│   ├── settings/      # Settings and admin toggles
│   ├── release/       # Latest release and APK installer
│   └── widget/        # Today/Floater Glance widgets and refresh coordinators
└── ui/
    ├── component/     # RootFeedDock, sheets, pull refresh, controls
    └── theme/         # Colors, typography, dimensions
```

## Run

1. Open `android-compose/` in Android Studio.
2. Ensure Android SDK 35 is installed.
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
  updates, and admin server settings are not active.
- **Server Mode**: configure a self-hosted T'Day URL, verify the mobile probe/version compatibility,
  then login/register.

Server URLs are normalized and persisted only after successful authenticated setup. Server URL
credentials use Android Credential Manager where available, while real login credentials and cookies
are stored through encrypted local stores.

## Version Compatibility

- `android-compose/app/build.gradle.kts` reads root `../version.json` for `versionName` and computes
  `versionCode`.
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
- Repositories write optimistically to Room first.
- In Server Mode, `SyncManager` replays pending mutations and refreshes snapshots.
- In Local Mode, pending mutations are cleared/ignored because there is no remote target.
- Logout or invalid session clears session/user data according to the auth flow.

See [`../docs/DATA_MODEL.md`](../docs/DATA_MODEL.md) for the shared cache model.

## Widgets

The Today Tasks and Floater Tasks widgets are implemented with Glance and the same cache-backed task
models as the app. Today shows pending scheduled tasks due today only; Floater shows active
unscheduled floaters across all floater lists. Completed tasks and overdue scheduled tasks stay out
of these widget surfaces.

- Android exposes small, medium, and large picker entries backed by separate AppWidget provider
  metadata for each widget kind. These picker choices are starting sizes; every placed entry shares
  the same 2x2-to-4x4 resize range and renders the matching responsive Glance layout as it is
  stretched or compressed.
- Static picker previews use RemoteViews-compatible XML, and Android 15+ generated previews are
  published from `TodayTasksWidgetPreviewPublisher` when the app starts.
- Medium and large layouts show the title, neutral count text, a mode-accented native plus icon add
  target, and dense scan-first rows; compact layouts stay count-first and prioritize task titles
  over due-time detail. The short wide bucket also suppresses due-time chips so resized widgets keep
  long task titles readable.
- All widget states keep a subtle oversized Today/Floater watermark in the background; empty and
  setup states add centered message text over that persistent motif.
- Tapping Today widget content opens the app; tapping Floater widget content opens the Floater root.
- Task layouts keep the header and plus action fixed, then render a scrollable task body. The first
  viewport fits the highest-value rows for the current compact, wide, medium, or tall bucket, shows
  `+N more` when hidden tasks remain, and reveals the remaining cached rows as the user scrolls.
  Widget rows are capped at 50, with a final neutral app handoff row when more tasks exist.
- The add actions open `tday://todos/create?target=today` or
  `tday://todos/create?target=floater` through a dedicated translucent widget-create activity that
  hosts the matching in-app create-task sheet directly over the launcher with the title field
  focused. Floater creation hides schedule controls and creates an unscheduled floater.
- `OfflineCacheManager` requests widget refreshes after local cache changes, so Local Mode and
  optimistic writes update the widget without waiting for a server sync path.

Keep future widget work responsive across compact, wide, and tall sizes, preserve large add/content
tap targets, keep the persistent watermark calm behind both rows and empty text, reserve
Today/Floater accent treatment for the plus add button, and prefer system widget bounds, dynamic
color, and Material/Glance idioms over custom chrome.

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

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
  search, settings, reminders, widgets, and in-app APK updates.
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
│   └── widget/        # Today tasks widget
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

## Persistence and Sync

- Room stores todos, floaters, lists, floater lists, completed records, pending mutations, and sync
  metadata.
- `OfflineCacheManager` exposes `cacheDataVersion`; ViewModels reload from cache when it changes.
- Repositories write optimistically to Room first.
- In Server Mode, `SyncManager` replays pending mutations and refreshes snapshots.
- In Local Mode, pending mutations are cleared/ignored because there is no remote target.
- Logout or invalid session clears session/user data according to the auth flow.

See [`../docs/DATA_MODEL.md`](../docs/DATA_MODEL.md) for the shared cache model.

## Mobile Parity

For user-facing Android changes, compare the iOS implementation in `ios-swiftUI/Tday/Feature/` and
`ios-swiftUI/Tday/Core/`. Match behavior, counts, empty states, Local Mode affordances, and
navigation rules while keeping Android Compose idioms.

## Theme

- Theme mode can be changed in Settings: `System`, `Light`, or `Dark`.
- Selection is applied immediately and is cleared when unauthenticated data wipe runs.
- New shared colors/dimensions belong in `ui/theme/Color.kt` or `ui/theme/Dimens.kt` before screen
  code uses them.

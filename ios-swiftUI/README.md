# T'Day iOS (SwiftUI)

Native SwiftUI client for T'Day. It should stay behaviorally aligned with the Android Compose app while using SwiftUI, SwiftData, Observation, and iOS-native interactions.

## Product Role

Current feature surface:

- Local Mode for offline-only planning without server setup.
- Server Mode with JWE cookie auth, optimistic local writes, realtime refresh, and pending mutation replay.
- Home and Floater/Anytime root feeds controlled by `RootFeedDock`.
- Scheduled tasks, floaters, scheduled-task lists, floater lists, completed history, calendar, search, settings, reminders, and WidgetKit-ready snapshots.
- SwiftData-backed local cache mirrored with Android's Room-backed `OfflineSyncState`.

## Structure

```text
ios-swiftUI/
├── TdayApp.xcodeproj
├── Tday/
│   ├── TdayApp.swift
│   ├── Info.plist
│   ├── Core/
│   │   ├── Data/        # AppContainer, repositories, SwiftData cache, sync
│   │   ├── Domain/      # Use cases where they clarify app flows
│   │   ├── Model/       # API/domain/offline sync models
│   │   ├── Navigation/  # AppRoute
│   │   ├── Network/     # URLSession API, cookies, realtime
│   │   ├── Notification/# Deep links and reminders
│   │   ├── Security/    # Probe/decryption helpers
│   │   ├── UI/          # Shared app UI helpers
│   │   └── Widget/      # Today tasks widget snapshot store
│   ├── Feature/
│   │   ├── App/
│   │   ├── Auth/
│   │   ├── Home/
│   │   ├── Todos/
│   │   ├── Calendar/
│   │   ├── Completed/
│   │   ├── Settings/
│   │   └── Onboarding/
│   ├── UI/
│   │   ├── Component/
│   │   └── Theme/
│   └── Resources/
├── TdayWidget/
│   └── TodayTasksWidget.swift
└── Tests/
```

## Run

- Targets iOS 17+.
- Open `ios-swiftUI/TdayApp.xcodeproj`.
- Select the `Tday` scheme.
- Run on simulator/device.

Useful command-line check:

```bash
xcodebuild test -project ios-swiftUI/TdayApp.xcodeproj -scheme Tday -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.6'
```

## Persistence and Sync

- SwiftData stores todos, floaters, lists, floater lists, completed records, pending mutations, and sync metadata.
- `OfflineCacheManager` posts `.offlineCacheDidChange`; ViewModels refresh from cache when it changes.
- Repositories write optimistically to SwiftData first.
- In Server Mode, `SyncManager` replays pending mutations and refreshes snapshots.
- In Local Mode, pending mutations are cleared/ignored because there is no remote target.
- Keychain-backed `SecureStore` handles server URL, cookies, credentials, theme, reminders, and mode state.

See [`../docs/DATA_MODEL.md`](../docs/DATA_MODEL.md) for the shared cache model.

## Mobile Parity

For user-facing iOS changes, compare the Android implementation in `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/` and `core/`. Match behavior, counts, empty states, Local Mode affordances, and navigation rules while keeping SwiftUI idioms.

## Environment Notes

- Uses `URLSession`, `SwiftData`, `Observation`, `CryptoKit`, `UserNotifications`, and Sentry Cocoa.
- `AppContainer` owns repository/service wiring. Keep dependencies explicit there unless a broader architecture decision says otherwise.
- The root `Package.swift` is support-only and intentionally non-runnable; it exists for source indexing/package resolution, not for launching the iOS app.

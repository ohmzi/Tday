# T'Day iOS (SwiftUI)

Native SwiftUI client for T'Day. It should stay behaviorally aligned with the Android Compose app while using SwiftUI, SwiftData, Observation, and iOS-native interactions.

## Product Role

Current feature surface:

- Local Mode for offline-only planning without server setup.
- Server Mode with JWE cookie auth, optimistic local writes, realtime refresh, and pending mutation replay.
- Home and Floater/Anytime root feeds controlled by `RootFeedDock`.
- Scheduled tasks, floaters, scheduled-task lists, floater lists, completed history, calendar, search, settings, reminders, and the Today Tasks WidgetKit extension.
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
│   ├── TodayTasksWidget.swift
│   ├── Info.plist
│   └── TdayWidget.entitlements
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

## Widgets

`TdayWidget` is a WidgetKit app extension with small, medium, and large Today Tasks widgets. The app
writes snapshots through the App Group suite `group.com.ohmz.tday` using key
`tday.widget.todayTasksSnapshot`; the extension decodes schema version 2 snapshots and keeps a legacy
fallback for older payloads.

- Snapshot status is `setup`, `empty`, or `tasks`, with task count, generated time, and capped task rows.
- Tapping the widget body, header, empty/setup message, or task rows opens the main app.
- The add action opens `tday://todos/create?target=today` and starts the in-app create-task sheet.
- System-family WidgetKit widgets remain snapshot/glanceable; the count reflects all due-today tasks,
  while the visible rows stay capped to the family size.
- The widget shows pending scheduled tasks due today only; floaters, completed tasks, and overdue tasks
  are intentionally excluded from v1.

Widget UI should keep using system WidgetKit margins/backgrounds, removable container backgrounds, and
tinted/accented rendering support while carrying T'Day identity through the Today accent, rounded
typography, priority dots, and calm empty/setup states.

## Mobile Parity

For user-facing iOS changes, compare the Android implementation in `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/` and `core/`. Match behavior, counts, empty states, Local Mode affordances, and navigation rules while keeping SwiftUI idioms.

## Environment Notes

- Uses `URLSession`, `SwiftData`, `Observation`, `CryptoKit`, `UserNotifications`, and Sentry Cocoa.
- `AppContainer` owns repository/service wiring. Keep dependencies explicit there unless a broader architecture decision says otherwise.
- The root `Package.swift` is support-only and intentionally non-runnable; it exists for source indexing/package resolution, not for launching the iOS app.

## Version Compatibility

- iOS marketing version, build number, and `TdayUpdateURL` mirror root `../version.json`.
- Server Mode sends `X-Tday-Client: ios` and `X-Tday-App-Version`.
- When `compatibility.updateRequired` is true, iOS and the backend use exact version matching. Settings shows installed/server/latest version state, and Server Mode is blocked with app-update or server-update guidance when versions differ.
- `ios.updateUrl` in `version.json` should be set to the App Store or TestFlight URL before distributing an iOS build with a direct update action.
- Do not edit Xcode version fields directly. Update `version.json`, then run `node scripts/version.mjs sync` and `node scripts/version.mjs check` from the repo root.

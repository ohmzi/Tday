# T'Day iOS (SwiftUI)

Native SwiftUI client for T'Day. It should stay behaviorally aligned with the Android Compose app while using SwiftUI, SwiftData, Observation, and iOS-native interactions.

## Product Role

Current feature surface:

- Local Mode for offline-only planning without server setup.
- Server Mode with JWE cookie auth, optimistic local writes, realtime refresh, and pending mutation replay.
- Home and Floater/Anytime root feeds controlled by `RootFeedDock`.
- Scheduled tasks, floaters, scheduled-task lists, floater lists, completed history, calendar, search, settings, reminders, and Today/Floater WidgetKit widgets.
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
│   │   └── Widget/      # Today/Floater widget snapshot stores
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

`TdayWidget` is a WidgetKit app extension with small, medium, and large Today Tasks and Floater
Tasks widgets. The app writes snapshots through the App Group suite `group.com.ohmz.tday` using keys
`tday.widget.todayTasksSnapshot` and `tday.widget.floaterTasksSnapshot`; the Today extension model
decodes schema version 2 snapshots and keeps a legacy fallback for older payloads.

- Snapshot status is `setup`, `empty`, or `tasks`, with task count, generated time, and capped task rows.
- Today includes pending scheduled tasks due today; Floater includes active unscheduled floaters
  across all floater lists. Completed tasks and overdue scheduled tasks are excluded.
- Medium and large layouts show the title, neutral count text, a mode-accented native plus icon add
  target, and dense scan-first rows; compact layouts stay count-first and prioritize task titles
  over due-time detail.
- Empty and setup states use a subtle oversized Today/Floater watermark behind the centered message,
  echoing the in-app empty-list background without adding extra widget controls.
- Tapping Today widget content opens the app; tapping Floater widget content opens the Floater root.
- The add actions open `tday://todos/create?target=today` or
  `tday://todos/create?target=floater`, select the matching root feed, and immediately start the
  in-app create-task sheet with the title field focused. WidgetKit cannot present the app sheet over
  the Home Screen widget host, so the interaction uses a focused in-app handoff.
- System-family WidgetKit widgets remain snapshot/glanceable. The widget stores a larger capped task
  set, renders a fixed family-specific neutral row set, and uses tighter row metrics so medium and
  large families surface more task context before the compact overflow row.

Widget UI should keep using system WidgetKit margins/backgrounds, removable container backgrounds, and
tinted/accented rendering support while carrying T'Day identity through rounded typography, native
add icons, calm watermark-backed empty/setup states, and Today/Floater accent treatment reserved for
the plus add button.

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

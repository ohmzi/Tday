# T'Day iOS (SwiftUI)

Native SwiftUI client for T'Day. It should stay behaviorally aligned with the Android Compose app while using SwiftUI, SwiftData, Observation, and iOS-native interactions.

## Product Role

Current feature surface:

- Local Mode for offline-only planning without server setup.
- Server Mode with JWE cookie auth, optimistic local writes, realtime refresh, and pending mutation replay.
- Scheduled task home and Floater task home root feeds controlled by `RootFeedDock`.
- Pull-to-refresh is a Server Mode root-feed affordance on the scheduled task home and floater task home roots only; detail, category, calendar, and completed screens refresh through cache observation, sync, foreground reconnect, or explicit retry actions.
- Scheduled tasks, floaters, scheduled-task lists, floater lists, completed history, calendar, search, settings, reminders, an opt-in device-calendar mirror, Today/Floater WidgetKit widgets, CarPlay templates, and Siri/App Shortcuts for car task creation.
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
│   │   ├── Calendar/    # Opt-in device-calendar mirror (EventKit)
│   │   ├── Notification/# Deep links and reminders
│   │   ├── Security/    # Probe/decryption helpers
│   │   ├── UI/          # Shared app UI helpers
│   │   └── Widget/      # Today/Floater widget snapshot stores
│   ├── Feature/
│   │   ├── App/
│   │   ├── Auth/
│   │   ├── ScheduledTaskHome/
│   │   ├── Todos/
│   │   ├── Calendar/
│   │   ├── CarPlay/
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

## Releasing to TestFlight

- `.github/workflows/ios-testflight.yml` archives the `Tday` scheme on a macOS runner and uploads
  it to TestFlight when `release.yml` pushes a `v*` tag whose diff against **the last release that
  actually reached TestFlight** touched an iOS-relevant file. Anchoring on the previous *tag*
  instead would silently strand a release whose iOS build failed.
- Signing is App Store Connect API key plus Xcode automatic signing (`-allowProvisioningUpdates`);
  there is no `match` repository and no certificate in the repo. The five App IDs and the
  `group.com.ohmz.tday` App Group must already exist — Xcode mints *profiles* on demand, not those.
  The API key needs the **Admin** role, not App Manager.
- Automatic signing always *development*-signs the archive — the App Store re-sign happens at
  export — so CI also needs one persistent **Apple Development** certificate *with its private
  key*, held as the `IOS_DEVELOPMENT_CERT_P12_BASE64` / `IOS_DEVELOPMENT_CERT_P12_PASSWORD`
  secrets and imported into a throwaway keychain for the run. The pipeline refuses to archive
  without them, in build-only mode too: letting `-allowProvisioningUpdates` mint a fresh
  certificate every run is what filled the team's certificate quota and broke v0.7.7. See
  `docs/DEPLOYMENT.md` § CI development certificate.
- `workflow_dispatch` runs the same pipeline in **build-only** mode on any ref
  (`gh workflow run ios-testflight.yml --ref develop`): it archives, signs and exports, then
  stops. That is how a change to the pipeline — or a Swift compile error only a macOS runner
  reproduces — gets tested without cutting a release. The mode is derived from the event and the
  ref, so no input can make a manual run upload; only the push of a `v*` tag uploads.
- The lane never derives a build number — `CURRENT_PROJECT_VERSION` already equals
  `version.json`'s `ios.buildNumber` at the tag.
- The upload does not wait for App Store Connect processing (`skip_waiting_for_build_processing`),
  so a green archive/upload only means ASC *accepted* the binary. A follow-up `confirm-processing`
  job — Linux, not macOS, since it is only an HTTP poll — checks the result on a bounded window and
  fails loudly if it comes back `FAILED`/`INVALID`, naming the spent build number and the one the
  next release needs.
- `XcodeGen must never be run here.` `project.yml` is a mirror kept for tooling;
  `TdayApp.xcodeproj/project.pbxproj` is the source of truth, and regenerating would discard it
  along with the shared `Tday.xcscheme` the pipeline archives.
- Lane and Ruby toolchain live in `ios-swiftUI/fastlane/` and `ios-swiftUI/Gemfile`. Full setup
  checklist: `docs/DEPLOYMENT.md` § iOS TestFlight Releases.

## Persistence and Sync

- SwiftData stores todos, floaters, lists, floater lists, completed records, pending mutations, and sync metadata.
- `OfflineCacheManager` posts `.offlineCacheDidChange`; ViewModels refresh cache-backed task data and
  Settings sync status when it changes.
- Repositories write optimistically to SwiftData first.
- In Server Mode, `SyncManager` replays pending mutations and refreshes snapshots.
- In Server Mode, Settings shows Server sync status, last sync metadata, pending change count, and a
  duplicate-safe manual sync action.
- In Local Mode, pending mutations are cleared/ignored because there is no remote target, and Settings
  shows the workspace as local-only.
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
- All widget states keep a subtle oversized Today/Floater watermark in the background; the Today
  watermark follows the app title icon rule, showing the sun from 6 AM to 5:59 PM and the moon at
  night. Empty and setup states add centered message text over that persistent motif.
- Tapping Today widget content opens the app; tapping Floater widget content opens the Floater root.
- The add actions open `tday://todos/create?target=today` or
  `tday://todos/create?target=floater`, select the matching root feed, and immediately start the
  in-app create-task sheet without auto-focusing the title field or opening the keyboard. WidgetKit
  cannot present the app sheet over the Home Screen widget host, so the interaction uses an in-app
  handoff.
- System-family WidgetKit widgets remain snapshot/glanceable because WidgetKit does not support true
  in-widget scrolling lists. The widget stores up to 50 task rows, renders the best-fit
  family-specific neutral row set, and keeps `+N more` as the tap-to-open cue when additional tasks
  exist.

Widget UI should keep using system WidgetKit margins/backgrounds, removable container backgrounds, and
tinted/accented rendering support while carrying T'Day identity through rounded typography, native
add icons, persistent calm watermarks, and Today/Floater accent treatment reserved for the plus add
button.

### Per-list configuration (R7)

Both widget kinds are `AppIntentConfiguration`s: long-press ▸ Edit Widget (or the placement flow) lets
the user pick any one todo list or floater list via `TdayWidgetListEntity`/`TdayWidgetListEntityQuery`
(`TdayWidget/TodayTasksWidget.swift`), backed by a lightweight catalog file
(`widget-lists-snapshot.json`, written by `WidgetConfigurableListsStore`) so the extension never needs
`AppContainer`/SwiftData. Leaving the picker unset keeps the ORIGINAL global feed — this is also what
every widget placed before R7 falls back to.

The picked list's TYPE, not the gallery slot it came from, decides the rendered shape: a todo list
always renders due-date-shaped (due times, overdue tinted red); a floater list always renders
undated-shaped. So a floater list picked from the "Today's Tasks" gallery entry renders
floater-shaped, and vice versa — there is no third shape. Content for a configured instance comes
from the same two snapshot files' new `perList[listId]` map (see `docs/DATA_MODEL.md`); a per-list
instance's todo window is due-today-OR-overdue (wider than the global "due today" feed), since the
user explicitly chose that one list rather than the aggregate.

This is a sibling to, not a replacement for, iOS Focus Filters (`Feature/CarPlay/CarTaskIntents.swift`
— `TdayFocusFilterStore`/`TdayListAppEntity`), which narrows the Today feed to a set of lists while a
Focus is active. Both mechanisms can be in play at once; a per-list widget's content ignores the
active Focus filter (it already narrowed to one list on purpose).

## CarPlay

The app includes a CarPlay template scene in `Feature/CarPlay/` for the same Today/Floater task
surface. CarPlay uses `CPListTemplate` and bar buttons rather than custom freeform SwiftUI, so the
Today/Floater switcher is represented by icon controls and system transitions. App Store
distribution requires Apple to grant the CarPlay entitlement for the appropriate category; until
then, the code remains buildable but entitlement-gated for real CarPlay deployment.

Voice creation is exposed through App Intents/App Shortcuts. The CarPlay plus action offers a
template-compliant iPhone handoff, while Siri can run the same create operation directly by voice.

## Natural-language scheduling

The new-task title field recognizes date/time phrases **on-device** with Foundation's
`NSDataDetector` (Apple's built-in date detector) — no network and no AI, so it also works in Local
Mode. Typing e.g. "call mom next monday 9am" auto-sets the Due, highlights the recognized phrase in
place, and strips it from the saved task title.

- `OnDeviceTitleNlpParser` (in `Core/Data/Todo/TodoRepository.swift`) runs `NSDataDetector` and
  returns the matched span, the cleaned title, and the due instant. A bare "8pm" is never shifted.
- `TodoRepository.parseTodoTitleNlp` is the single entry point used by every create-task surface;
  swapping it to the local parser made them all offline at once.
- `CreateTaskSheet` keeps the full typed text and overlays the highlight (an `AttributedString` over
  the `TextField`, so editing/caret behaviour is unchanged), sets the Due, and removes the phrase
  from the title only on submit.
- Parsing runs in the device timezone; the due is saved as a UTC instant. `NSDataDetector` resolves
  relative phrases ("tomorrow") against the current date.

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

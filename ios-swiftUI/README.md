# T'Day iOS (SwiftUI)

Native SwiftUI client that mirrors the existing Android Compose app against the same Next.js REST API.

## Structure

```text
ios/
  Package.swift
  Tday/
    TdayApp.swift
    Info.plist
    Core/
    Feature/
    UI/
  TdayWidget/
    TodayTasksWidget.swift
```

## What Is Implemented

- SwiftUI app shell with `NavigationStack`, deep links, onboarding overlay, and settings flow.
- Shared models, URLSession API layer, Keychain-backed secure store, cookie handling, and CryptoKit credential envelope login.
- SwiftData-backed offline cache plus pending mutation replay and remote merge logic.
- Home, todo list, calendar, completed history, and settings screens.
- Reminder scheduling and a WidgetKit placeholder entry point for the future app extension target.

## Environment Notes

- Targets iOS 17+.
- Uses `URLSession`, `SwiftData`, `Observation`, `CryptoKit`, and `UserNotifications`.
- This workspace is not running on macOS/Xcode, so `xcodebuild` and project generation are not available here.
- A Swift Package manifest is included at `ios/Package.swift` so the source tree can be opened directly in Xcode on macOS.
- No validated `.xcodeproj` metadata is included from this Linux environment, and the package manifest has not been compiled here.

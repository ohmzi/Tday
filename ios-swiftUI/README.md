# T'Day iOS (SwiftUI)

Native SwiftUI client that mirrors the existing Android Compose app against the same Next.js REST API.

## Structure

```text
ios/
  Package.swift
  TdayApp.xcodeproj
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
- Open `TdayApp.xcodeproj` and run the `Tday` scheme for simulator/device installs.
- The root `Package.swift` is support-only and intentionally non-runnable; it exists for source indexing/package resolution, not for launching the iOS app.

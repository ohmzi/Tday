# T'Day Native Android (Compose)

Native Kotlin + Jetpack Compose Android client for T'Day.

## Goals
- Replace old web-wrapper APK approach with a real native app.
- Keep feature parity with backend domains: auth, todos, calendar feed, completed, notes, projects, preferences/profile.
- iOS Reminders-inspired UI direction for mobile UX.

## Module
- `app`: Android application module.

## Run
1. Open `/home/ohmz/StudioProjects/Tday/android-compose` in Android Studio.
2. Ensure Android SDK 35 is installed.
3. Run on emulator/device.

First launch behavior:
- The app shows a server URL dialog before login.
- Enter your host (for example: `tday.ohmz.cloud`).
- URL is normalized and kept in memory for the current auth flow.

Persistence:
- Server URL is persisted only after a successful authenticated login.
- Login credentials are stored encrypted only after successful login.
- If no valid authenticated session exists, local user data is wiped (server URL, credentials, offline cache, cookies, and encrypted local prefs).
- After logout or expired/invalid session, the app requires server setup and login again.

Theme:
- Theme mode can be changed in Settings: `System`, `Light`, or `Dark`.
- Selection is applied immediately and is cleared when unauthenticated data wipe runs.

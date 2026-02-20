# Tday Native Android (Compose)

Native Kotlin + Jetpack Compose Android client for Tday.

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
- URL is normalized and stored encrypted.

Persistence:
- Server URL is stored encrypted.
- Login credentials are stored encrypted after successful login.
- On future app launches, the app reuses saved server + credentials and does not ask again.
- Data is reset only if app data is cleared or app is uninstalled.

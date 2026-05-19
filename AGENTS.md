# T'Day Agent Guide

This file is the working agreement for AI agents contributing to T'Day. Read it with `README.md`, `docs/ARCHITECTURE.md`, `docs/CODING_STANDARDS.md`, and `docs/TESTING.md`.

## Project Shape

T'Day is a private, self-hosted personal task planner with:

- `tday-web/`: Vite, React, TypeScript, Tailwind, i18next.
- `tday-backend/`: Ktor, Exposed, Flyway, PostgreSQL, JWE sessions.
- `shared/`: Kotlin Multiplatform DTOs, enums, and validators consumed by backend, Android, and iOS.
- `android-compose/`: Native Android app using Kotlin, Jetpack Compose, Hilt, Retrofit, offline cache and sync.
- `ios-swiftUI/`: Native iOS app using SwiftUI, SwiftData, Observation, URLSession, Keychain/cookie handling.

The native mobile apps should feel like one product expressed through two platform-native implementations.

## How To Work In This Repo

- Start by checking `git status --short --branch`. The worktree may already contain user changes.
- Never revert or overwrite user work unless explicitly asked.
- Avoid destructive git commands. Do not use `git reset --hard` or `git checkout --` to clean up.
- Prefer small, focused changes. Do not opportunistically refactor unrelated modules.
- When the user asks for implementation, implement it, verify it, then report clearly.
- When the user asks for a PR, push the active branch and open/update the PR they requested.
- When resolving merge conflicts into an outdated base, prefer the active/latest branch behavior unless the user explicitly says otherwise.

## Git And Attribution

- Commits should be authored as the user's GitHub identity:
  - `user.name=ohmzi`
  - `user.email=6551272+ohmzi@users.noreply.github.com`
- Check the local git config before committing if attribution matters.
- Do not add AI trailers or tool attribution to commit messages.
- Do not use `--no-verify` to bypass hooks. Fix the hook or the commit message instead.
- Keep commit messages short and human, for example `Refine Android calendar paging polish`.

## Cross-Platform UX Rule

Any user-facing mobile change on Android or iOS should trigger a quick parity check on the other platform.

Before finishing a mobile UI task, ask:

- Does Android and iOS expose the same feature surface?
- Do labels, task counts, date rules, empty states, and disabled states match?
- Do navigation rules match, including lower bounds and edge cases?
- Does the interaction feel platform-native on each OS?
- Is one platform now clearly nicer? If yes, bring the other platform up to the same product quality.

Do not blindly copy implementation details across platforms. Copy behavior, interaction rules, information architecture, and visual intent while using native APIs and established local patterns.

## Calendar UX Contract

The calendar is a cross-platform feature and should stay behaviorally aligned across Android and iOS.

Core rules:

- Modes are `Month`, `Week`, and `Day`.
- Default mode is `Month`.
- Week starts on Sunday.
- Navigation cannot go before the current month.
- The top-right calendar button jumps to today without changing the active mode.
- The FAB creates a task using the currently selected calendar date.
- Task counts come from pending scheduled items grouped by local start-of-day.
- Day and week task counts cap display at `9+`.
- The task section title stays in the form `Tasks due EEE, MMM d`.

Interaction rules:

- Swipe, chevron buttons, and Today jumps should use the same horizontal paging motion.
- Headers should stay anchored. In month view, the month title and weekday row should not slide with the date grid. In week/day view, the period header should not slide with the date content.
- Animate only the changing calendar content, then commit the selected date/period after the page settles.
- Avoid fade-heavy redraws for calendar paging. It should feel like a native pager, not a full card replacement.
- Prevent rapid repeated taps from stacking broken page transitions.
- If jumping a long distance back to today, animate toward the target in the correct direction and settle directly on today.

Relevant files:

- iOS: `ios-swiftUI/Tday/Feature/Calendar/CalendarScreen.swift`
- Android: `android-compose/app/src/main/java/com/ohmz/tday/compose/feature/calendar/CalendarScreen.kt`

## Mobile UI Direction

T'Day is a task app, not a marketing site. Mobile screens should feel quiet, useful, and polished.

- Prefer direct usable UI over explanatory copy.
- Keep controls discoverable through familiar icons, clear labels, and expected placement.
- Use rounded typography and the existing soft, focused visual language.
- Keep cards purposeful. Do not nest decorative cards.
- Use haptics where the surrounding code already does for primary button actions.
- Text must fit in compact mobile layouts without overlap or truncation that hides meaning.
- Empty states should be calm and short.
- Preserve dark mode.

## Design Tokens And Strings

- Web strings live in i18next locale files.
- Android strings live in `android-compose/app/src/main/res/values/strings.xml`.
- iOS strings should follow the current local SwiftUI patterns until a broader localization layer exists.
- Prefer theme and dimension tokens over inline colors or magic sizes.
- If a new semantic color or repeated dimension is needed, add it to the platform's theme/token layer instead of scattering literals.
- Existing feature-scoped constants can remain when they are already part of that feature's local style, but do not expand hardcoded styling casually.

## Architecture Expectations

Backend:

- Keep request/response contracts aligned with `shared/` models when possible.
- Services return typed errors and avoid leaking internals.
- Preserve tenant isolation in all data access.

Android:

- Use MVVM with `@HiltViewModel`, `StateFlow`, repositories, and app services.
- Keep mutable state private and expose read-only state.
- Respect offline-first cache/sync behavior.
- Use Compose idioms and Material 3.

iOS:

- Use SwiftUI, Observation, SwiftData, and URLSession patterns already present in the app.
- Keep feature code inside `Feature/<Name>/` unless it is truly shared.
- Prefer small local helpers before creating broad abstractions.

Web:

- Use React Query for server state.
- Use the shared API client, not raw backend `fetch` calls from components.
- Use Tailwind semantic tokens and locale keys.

## Verification Commands

Run the smallest meaningful verification for the change, then broaden when risk is higher.

Common commands:

```bash
# Web
cd tday-web && npm run lint
cd tday-web && npm run test

# Backend
./gradlew :tday-backend:test

# Android
cd android-compose && ./gradlew :app:compileDebugKotlin
cd android-compose && ./gradlew :app:testDebugUnitTest

# iOS
xcodebuild test -project ios-swiftUI/TdayApp.xcodeproj -scheme Tday -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.6'
```

For mobile UI changes, prefer running the platform build even when there are no dedicated UI tests. If a simulator/device visual check is practical, do it.

## Review Checklist Before Final Response

- `git status --short --branch` is clean or only contains intentional uncommitted changes.
- User-facing behavior is aligned across Android and iOS when the feature exists on both.
- Strings, colors, and dimensions follow the project conventions.
- No secrets, build outputs, dependency folders, or generated artifacts were added.
- Tests/builds relevant to the touched area were run, or skipped with a clear reason.
- If committed, the commit author is `ohmzi <6551272+ohmzi@users.noreply.github.com>`.
- If pushed/opened as a PR, report the branch, commit, and PR URL.

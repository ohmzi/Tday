# T'Day Agent Guide

This file is the working agreement for AI agents contributing to T'Day. Read it with `README.md`, `docs/PRODUCT_DIRECTION.md`, `docs/DATA_MODEL.md`, `docs/ARCHITECTURE.md`, `docs/CODING_STANDARDS.md`, `docs/TESTING.md`, and `docs/REPO_HOUSEKEEPING.md`.

## Project Shape

T'Day is a private, self-hosted personal task planner with:

- `tday-web/`: Vite, React, TypeScript, Tailwind, i18next.
- `tday-backend/`: Ktor, Exposed, Flyway, PostgreSQL, JWE sessions.
- `shared/`: Kotlin Multiplatform DTOs, enums, validators, and route constants consumed by backend/Android and mirrored by iOS contract models.
- `android-compose/`: Native Android app using Kotlin, Jetpack Compose, Hilt, Retrofit, Room offline cache, reminders, widgets, and sync.
- `ios-swiftUI/`: Native iOS app using SwiftUI, SwiftData, Observation, URLSession, Keychain/cookie handling, reminders, and widget snapshots.

The native mobile apps should feel like one product expressed through two platform-native implementations.

Current product direction:

- Scheduled `Todo` items have due-date, recurrence, reminder, calendar, and scheduled-list semantics.
- `Floater` items are unscheduled Anytime tasks with separate floater lists and completed history.
- Local Mode is a first-class mobile workspace and must not silently upload local-only data to a server workspace.
- Server Mode uses local optimistic writes plus pending mutation replay.
- Documentation is part of the deliverable when behavior, structure, API, data shape, or verification changes.

## How To Work In This Repo

- Start by checking `git status --short --branch`. The worktree may already contain user changes.
- Never revert or overwrite user work unless explicitly asked.
- Avoid destructive git commands. Do not use `git reset --hard` or `git checkout --` to clean up.
- Prefer small, focused changes. Do not opportunistically refactor unrelated modules.
- When the user asks for implementation, implement it, verify it, then report clearly.
- When the user asks for a PR, push the active branch and open/update the PR they requested.
- When resolving merge conflicts into an outdated base, prefer the active/latest branch behavior unless the user explicitly says otherwise.
- When the user asks for documentation, inspect the real project state and recent commits before writing broad claims.

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
- Does Local Mode hide/disable server-only affordances consistently?
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
- Floaters do not appear on the calendar unless a future product decision gives them scheduled semantics.

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
- Icons come from a single Lucide source shared across web/Android/iOS — never platform-native icon sets (Material `Icons.*`, SF Symbols) for shared surfaces. See `docs/ICONS.md`.
- Use rounded typography and the existing soft, focused visual language.
- Keep cards purposeful. Do not nest decorative cards.
- Use haptics where the surrounding code already does for primary button actions.
- Text must fit in compact mobile layouts without overlap or truncation that hides meaning.
- Empty states should be calm and short.
- Preserve dark mode.
- Root feed behavior should stay aligned: Home and Floater/Anytime are sibling root feeds controlled by `RootFeedDock`, with the create action available from the root controls.

## Design Tokens And Strings

- Web strings live in i18next locale files.
- Android strings live in `android-compose/app/src/main/res/values/strings.xml`.
- iOS strings should follow the current local SwiftUI patterns until a broader localization layer exists.
- Prefer theme and dimension tokens over inline colors or magic sizes.
- If a new semantic color or repeated dimension is needed, add it to the platform's theme/token layer instead of scattering literals.
- Existing feature-scoped constants can remain when they are already part of that feature's local style, but do not expand hardcoded styling casually.

## Global Version Management

`version.json` at the repository root is the single source of truth for the T'Day release version, mobile/server compatibility policy, iOS build number, and iOS update URL.

- Bump versions with `node scripts/version.mjs bump patch|minor|major`, or edit `version.json` directly only when intentionally changing manifest fields.
- After any manifest edit, run `node scripts/version.mjs sync`, then `node scripts/version.mjs check`.
- Never hand-edit Android `versionName`/`versionCode`, backend Gradle version values, iOS marketing/build versions, web package versions, package-lock versions, or example `TDAY_APP_VERSION` mirrors.
- Backend, Android, and iOS compatibility behavior must be updated together. Exact compatibility means mobile Server Mode clients and the backend should agree on the same release version when `compatibility.updateRequired` is true.
- iOS update actions use `ios.updateUrl` from `version.json`; set it to the App Store or TestFlight URL before distributing an iOS build that should offer direct updates.

## New Feature Observability Checklist

T'Day is Sentry-first and privacy-first. New UI, API, sync, auth, reminder, widget, realtime, or storage behavior should include only diagnostic telemetry that helps debug failures or performance.

- Use platform helpers: backend `TdayObservability`, web `src/lib/observability/sentry.ts`, Android `TdayTelemetry`, and iOS `TdayTelemetry`.
- Keep breadcrumb and transaction names stable and structural: route templates, screen/operation names, status codes, durations, counts, and enum-like states.
- Pass route-like fields such as `route`, `path`, `url`, `from`, and `to` through the platform helper so they are stored as templates, not raw URLs.
- Never record task/list/floater titles, descriptions, user text, raw IDs, raw URLs, query strings, emails, cookies, auth tokens, session IDs, request bodies, response bodies, or local cache records.
- Local Mode diagnostics may describe the operation, but must not upload local-only content or imply server sync.
- Update `docs/TELEMETRY.md`, guardrail tests, and platform docs when collected fields, SDK config, helper behavior, or privacy boundaries change.

## Feature Guide

The in-app How-To guide (Settings → "How-To & Tips") is content-driven from the shared `GuideCatalog` (`shared/src/commonMain/kotlin/com/ohmz/tday/shared/guide/`). It works fully offline and in Local Mode because content is compiled/bundled into every client.

- Any PR that ships user-visible behavior must add its `GuideTopic` to `GuideCatalog.kt` (with `sinceVersion` = the release it ships in, to feed "What's New"), add `guide.topics.<id>.*` strings to **every** `tday-web/messages/<locale>.json`, then run `./gradlew :shared:exportGuideContent` and commit the regenerated artifacts.
- Direction of truth: structure flows Kotlin→web/iOS; strings flow web→Kotlin/iOS. `./gradlew :shared:verifyGuideContent` is the CI drift gate — if it fails, regenerate and commit.
- Topic icons must be real Lucide glyphs present on every platform (web `lucide-react`, Android `ic_lucide_<glyph>.xml`, iOS `Lucide<Glyph>.imageset`); the `guide-icons` coverage test enforces this. Add missing assets per `docs/ICONS.md`.
- The i18n parity guardrail blocks partial translations, so a new topic needs all locales up front (machine-translate, then refine).

## Architecture Expectations

Across the repo, keep changes shaped around readable boundaries: a file or type should have a clear reason to exist, dependencies should flow from UI to state to services/repositories to storage/network, and helpers should start local before being promoted to shared.

Backend:

- Keep request/response contracts aligned with `shared/` models when possible.
- Services return typed errors and avoid leaking internals.
- Preserve tenant isolation in all data access.
- Keep scheduled-task routes, floater routes, scheduled-list routes, and floater-list routes distinct.
- Routes translate HTTP and validation; services own business decisions; Exposed table/query code stays out of UI-facing layers.

Android:

- Use MVVM with `@HiltViewModel`, `StateFlow`, repositories, and app services.
- Keep mutable state private and expose read-only state.
- Respect Room-backed offline-first cache/sync behavior and Local Mode.
- Use Compose idioms and Material 3.
- ViewModels depend on injected repositories/services, not Retrofit, Room DAOs, or storage details directly.

iOS:

- Use SwiftUI, Observation, SwiftData, and URLSession patterns already present in the app.
- Keep feature code inside `Feature/<Name>/` unless it is truly shared.
- Prefer small local helpers before creating broad abstractions.
- Keep `AppContainer` wiring explicit and update SwiftData/cache mappers with data model changes.
- Views render state and invoke actions; repositories/cache managers own persistence and sync details.

Web:

- Use React Query for server state.
- Use the shared API client, not raw backend `fetch` calls from components.
- Use Tailwind semantic tokens and locale keys.
- Keep feature modules cohesive and move repeated logic into `src/lib/`, `src/hooks/`, or feature-scoped helpers only when that reduces real duplication.

Docs:

- Update `README.md` for project shape and documentation-map changes.
- Update `docs/DATA_MODEL.md` for table, DTO, local cache, and pending mutation changes.
- Update `docs/API_GUIDELINES.md` for route changes.
- Update `docs/ARCHITECTURE.md` for data flow, module, and platform architecture changes.
- Update platform READMEs for Android/iOS setup, storage, sync, or feature-surface changes.

## Verification Commands

Run the smallest meaningful verification for the change, then broaden when risk is higher.

Mandatory compile/build checks by touched area:

- Android: if files under `android-compose/` or shared Android-consumed contracts change, run the Android compile before finishing.
- iOS: if files under `ios-swiftUI/` or shared iOS contract assumptions change, run the iOS build before finishing.
- Backend: if files under `tday-backend/`, backend Gradle config, migrations, or shared server contracts change, run the backend verification before finishing.
- Web: if files under `tday-web/`, web package config, or shared web-facing contracts change, run the web build/lint verification before finishing.
- If a change touches more than one area, run each touched area's required build. Do not finish by saying the next compile should work when a local compile/build check was practical.

Common commands:

```bash
# Web
cd tday-web && npm run lint
cd tday-web && npm run build
cd tday-web && npm run test

# Backend
./gradlew :tday-backend:test

# Android
cd android-compose && ./gradlew :app:compileDebugKotlin
cd android-compose && ./gradlew :app:testDebugUnitTest

# iOS
xcodebuild build -project ios-swiftUI/TdayApp.xcodeproj -scheme Tday -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO
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

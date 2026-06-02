# Product Direction

This document records the intended shape of T'Day so future work moves toward the same product instead of accreting unrelated screens.

## Product Goal

T'Day is a private, self-hosted personal task planner that should feel immediate on mobile, dependable offline, and quiet enough to use every day. The long-term product is:

- A self-hosted planning system for one person or a small private group.
- A native Android and iOS app pair with the same feature surface and platform-native implementation.
- A web app that remains the administrative, desktop, and broad-access surface.
- A local-first mobile experience that can be used without a server, then safely syncs when a server workspace exists.
- A documented monorepo where data contracts, UI rules, verification, deployment, and housekeeping are discoverable before coding starts.

## Current Product Surfaces

| Surface | Role |
|---------|------|
| Web SPA | Desktop planner, admin settings, release/version surfaces, full API consumer, i18n reference implementation |
| Backend | Auth, tenant isolation, task/list/floater persistence, recurrence expansion, WebSocket events, AI summaries, mobile probe and version compatibility |
| Shared KMP | DTOs, enums, validators, and shared route constants for backend/Android alignment; iOS mirrors these contracts in Swift models |
| Android | Primary native mobile app using Compose, Room-backed local cache, Hilt, Retrofit, reminders, widgets, an internal car surface, and in-app updates |
| iOS | Primary native mobile app using SwiftUI, SwiftData-backed local cache, Observation, URLSession, reminders, widgets, CarPlay templates, App Intents, and password/keychain support |

## Planning Model

T'Day separates work by scheduling intent.

| Concept | Meaning |
|---------|---------|
| Scheduled task | A task with a `due` date/time. It appears in Today, Scheduled, Calendar, lists, and recurrence-aware flows. |
| Floater | An unscheduled task for Anytime/Floater planning. It has title, description, priority, pinning, completion, ordering, and optional floater-list membership, but no due date. |
| List | A project/group for scheduled tasks. |
| Floater list | A project/group for floaters. Keep it distinct from scheduled-task lists because the data and UI semantics are different. |
| Completed item | Immutable-ish history created when scheduled tasks or floaters are completed, preserving list metadata where possible. |

## Mobile Product Rules

Mobile is now the center of the product experience. Any user-facing Android or iOS change should ask whether the other platform exposes the same behavior, language, counts, empty states, and edge cases.

- Build Android and iOS as siblings, not clones. Copy behavior and intent; use native APIs and local conventions.
- Treat Home and Floater/Anytime as root feeds. `RootFeedDock` switches between them and collapses into a compact icon state while preserving quick creation.
- Keep pull-to-refresh disabled in Local Mode. In Server Mode, treat it as a root-feed affordance for Home and Floater/Anytime rather than a default on detail, category, calendar, or completed screens.
- Use local cache as the screen source of truth. Network sync updates the cache; screens observe cache changes.
- Keep offline notices calm and rate-limited. Do not interrupt normal use when cached data can satisfy the screen.
- Preserve dark mode, compact layouts, and text fit. Avoid explanatory UI copy when a familiar control can do the job.
- Treat calendar paging as a product contract: headers stay anchored, page content moves horizontally, today jumps keep the active mode, and previous-month navigation remains bounded.
- Treat car surfaces as constrained extensions of mobile: Today/Floater only, view + complete, voice or handoff creation, no false store categorization, and no custom UI that violates CarPlay or Play policy.

## Local Mode

Local Mode is an offline-only workspace on Android and iOS.

- It does not require server setup, login, or session cookies.
- Data is written directly to the local cache.
- Pending mutation queues are cleared/ignored because there is no remote target.
- Server-only features such as manual sync, remote updates, admin AI settings, and pull-to-refresh should be hidden or disabled.
- Local Mode data should not be silently uploaded later without an explicit migration/import design.

Server Mode remains the authenticated self-hosted workspace:

- Mobile writes optimistically to local storage first.
- Sync replays pending mutations and refreshes server snapshots.
- Realtime events and foreground reconnects should refresh cache state without destabilizing the UI.

## UX Direction

T'Day should feel like a focused task app, not a marketing surface.

- Directly usable screens beat onboarding copy.
- Familiar icons, labels, haptics, and expected placement beat custom explanation.
- Cards are for actual grouped content or sheet chrome, not decoration.
- Empty states are short, calm, and consistent.
- Motion should explain continuity: list removals, root-feed switching, calendar paging, sheet transitions, and swipe actions should feel attached to the thing changing.
- If one mobile platform gets a nicer interaction, bring the other platform up to the same product quality.

## Documentation Expectations

Documentation is part of the product.

- Update `README.md` when the project shape, setup, or document map changes.
- Update `docs/ARCHITECTURE.md` when module boundaries, data flow, or platform architecture changes.
- Update `docs/DATA_MODEL.md` when backend tables, shared DTOs, local cache records, or sync mutations change.
- Update `docs/API_GUIDELINES.md` when REST/WebSocket contracts change.
- Update `docs/CODING_STANDARDS.md` when local patterns or guardrails change.
- Update `docs/TESTING.md` when verification expectations change.
- Update platform READMEs when Android or iOS setup, storage, navigation, or feature surface changes.
- Update ADRs when a decision changes direction rather than merely adding implementation detail.

## Near-Term Direction

- Keep server/local data boundaries explicit and user-controlled.
- Continue converging Android and iOS around Home, Floater/Anytime, Calendar, Completed, Settings, reminders, car surfaces, and update flows.
- Make list and floater-list behavior clear in UI and contracts.
- Keep backend contracts stable for mobile clients; add compatibility handling before making breaking changes.
- Prefer focused cleanup that reduces future drift: shared DTOs, mirrored cache records, platform design tokens, and documented verification commands.
- Keep implementation pieces small enough to understand in isolation, with clear ownership and dependency direction from UI to state to data services.

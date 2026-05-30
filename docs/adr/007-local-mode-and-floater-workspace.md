# ADR 007: Local Mode and Floater Workspace

**Status:** Accepted
**Date:** 2026-05-29

## Context

T'Day has grown from a self-hosted web planner with native clients into a mobile-first personal planning app. Recent work added:

- Native Android and iOS offline caches.
- Optimistic writes with pending mutation replay.
- A root mobile feed with Home and Floater/Anytime modes.
- Unscheduled "Floater" tasks and floater lists.
- Local Mode for offline-only usage without a server login.

The project needs a clear decision so future work does not blur scheduled tasks, unscheduled tasks, local-only data, and server-backed sync.

## Decision

- Treat **Local Mode** as a first-class mobile workspace on Android and iOS.
- Treat **Server Mode** as the authenticated self-hosted workspace with optimistic local writes and sync replay.
- Store mobile screen data in local platform databases: Room on Android and SwiftData on iOS.
- Keep scheduled tasks and floaters as separate domain concepts:
  - `Todo` has due/recurrence/calendar semantics.
  - `Floater` has Anytime semantics and no due date.
  - `List` groups scheduled tasks.
  - `FloaterList` groups floaters.
- Use `RootFeedDock` as the mobile root switch between Home and Floater/Anytime.
- Keep Local Mode data local unless a future migration/import design explicitly asks the user to move it to a server workspace.

## Rationale

- Users should be able to start using T'Day immediately without configuring a server.
- Mobile apps must remain fast and useful during network failures.
- Separating floaters from scheduled todos avoids nullable due dates becoming ambiguous.
- Mirrored Room/SwiftData cache records make Android and iOS parity easier to reason about.
- Explicit Local Mode boundaries reduce accidental sync, privacy, and conflict-resolution surprises.

## Consequences

- Mobile repositories must handle both Local Mode and Server Mode.
- Server-only features such as pull-to-refresh, manual sync, realtime reconnect, and admin AI settings must be disabled or hidden in Local Mode.
- Backend APIs must preserve scheduled-task and floater endpoints separately.
- Data model changes often require updates across shared DTOs, backend tables/migrations, Android Room, iOS SwiftData, sync mappers, and docs.
- Future import/export or server-migration work needs a dedicated product decision and cannot be assumed.

# Data Model

This document describes the durable and local data structures that define T'Day. Keep it aligned with `shared/`, backend Exposed tables, Android Room entities, and iOS SwiftData entities.

## Sources of Truth

| Layer | Files | Purpose |
|-------|-------|---------|
| Shared contracts | `shared/src/commonMain/kotlin/com/ohmz/tday/shared/model/` | Serializable DTOs, request/response bodies, enums, and validators consumed across platforms |
| Backend tables | `tday-backend/src/main/kotlin/com/ohmz/tday/db/tables/` | PostgreSQL schema mapping through Exposed |
| Backend migrations | `tday-backend/src/main/resources/db/migration/` | Flyway SQL history and clean-install schema |
| Android cache | `android-compose/app/src/main/java/com/ohmz/tday/compose/core/data/db/` and `core/data/OfflineSyncModels.kt` | Room entities plus cache records used by repositories |
| iOS cache | `ios-swiftUI/Tday/Core/Data/Database/` and `Core/Model/OfflineSyncModels.swift` | SwiftData entities plus cache records used by repositories |

## Core Entities

| Entity | Backend table | Shared/mobile DTOs | Notes |
|--------|---------------|--------------------|-------|
| User | `Users` | `SessionUser`, auth responses | Owns all private data through `userID`; includes role, approval, and `tokenVersion`. |
| Account | `Accounts` | Auth models | OAuth/account compatibility and credential metadata. |
| Todo | `Todos` | `TodoDto`, `CreateTodoRequest`, `UpdateTodoRequest` | Scheduled task with required `due`, optional `rrule`, priority, pinning, ordering, and optional scheduled-task list. |
| Todo instance | `TodoInstances` | `TodoInstancePatchRequest`, `TodoInstanceDeleteRequest` | Per-occurrence overrides/deletions for recurring tasks. |
| Completed todo | `CompletedTodos` | `CompletedTodoDto` | Completion history preserving original task/list details where possible. |
| List | `Lists` | `ListDto`, `ListDetailResponse` | Scheduled-task project/group with color and icon metadata. |
| Floater | `Floaters` | `FloaterDto`, `CreateFloaterRequest`, `UpdateFloaterRequest` | Unscheduled task for Anytime/Floater planning. No `due`. |
| Floater list | `FloaterLists` / `FloaterProject` | `FloaterListDto`, `FloaterListDetailResponse` | Project/group for floaters. Keep separate from scheduled-task lists. |
| Completed floater | `CompletedFloaters` | `CompletedFloaterDto` | Completion history for floaters. |
| Preferences | `UserPreferences` | `PreferencesDto`, `PreferencesResponse` | Per-user sorting/grouping/direction preferences. |
| App config | `AppConfigs` | `AppSettingsResponse`, `AdminSettingsResponse` | Public/admin app settings such as AI summary availability. |
| Event/auth logs | `EventLogs`, `AuthThrottles`, `AuthSignals`, `CronLogs` | Internal models | Security, throttling, diagnostics, and operational state. |

## Scheduling Rules

Scheduled tasks and floaters are intentionally different:

- `Todo` requires a due timestamp and can participate in Today, Scheduled, Calendar, recurring instances, reminders, and scheduled-task lists.
- `Floater` has no due timestamp and belongs to the Anytime/Floater root feed.
- A task should not be made "unscheduled" by nulling `Todo.due`; use a floater instead.
- Completing a todo creates completed-todo history; completing a floater creates completed-floater history.
- List deletion must preserve completed history metadata (`listName`, `listColor`) where the backend/mobile model supports it.

## Recurrence

Recurring scheduled tasks use RFC 5545 RRULE strings.

| Field | Meaning |
|-------|---------|
| `due` | Canonical due timestamp for the base task or occurrence. |
| `rrule` | RFC 5545 recurrence rule for the series. |
| `instanceDate` / `instanceDateEpochMs` | Occurrence identity for edits/completion/deletion. |
| `exdates` | Backend exclusion timestamps for skipped occurrences. |
| `durationMinutes` | Backend duration metadata for expanded instances. |

Do not apply recurrence to floaters until a new product decision explicitly defines what "unscheduled recurrence" means.

## Mobile Offline State

Android and iOS mirror the same logical `OfflineSyncState`:

```text
OfflineSyncState
├── todos
├── floaters
├── completedItems
├── completedFloaters
├── lists
├── floaterLists
├── pendingMutations
├── lastSuccessfulSyncEpochMs
├── lastSyncAttemptEpochMs
└── aiSummaryEnabled
```

Android stores this state in Room tables:

- `cached_todos`
- `cached_floaters`
- `cached_lists`
- `cached_floater_lists`
- `cached_completed`
- `cached_completed_floaters`
- `pending_mutations`
- `sync_metadata`

iOS stores the same logical records in SwiftData:

- `CachedTodoEntity`
- `CachedFloaterEntity`
- `CachedListEntity`
- `CachedFloaterListEntity`
- `CachedCompletedEntity`
- `CachedCompletedFloaterEntity`
- `PendingMutationEntity`
- `SyncMetadataEntity`

Android has a one-time migration path from the legacy encrypted JSON cache into Room. New cache work should target Room and SwiftData directly.

## Local IDs

Mobile optimistic writes create local IDs until the server returns canonical IDs.

| Prefix | Meaning |
|--------|---------|
| `local-list-` | Scheduled-task list created locally. |
| `local-floater-list-` | Floater list created locally. |
| `local-todo-` | Scheduled task created locally. |
| `local-floater-` | Floater created locally. |
| `local-completed-` | Completed scheduled item created locally. |
| `local-completed-floater-` | Completed floater created locally. |

When syncing in Server Mode, repositories must remap local IDs to server IDs and update references in todos, floaters, lists, completed history, and pending mutations.

## Pending Mutations

`PendingMutationRecord` preserves user intent while offline or while an immediate network call fails.

Current mutation kinds:

- List: `CREATE_LIST`, `UPDATE_LIST`, `DELETE_LIST`
- Floater list: `CREATE_FLOATER_LIST`, `UPDATE_FLOATER_LIST`, `DELETE_FLOATER_LIST`
- Scheduled todo: `CREATE_TODO`, `UPDATE_TODO`, `DELETE_TODO`, `SET_PINNED`, `SET_PRIORITY`, `COMPLETE_TODO`, `COMPLETE_TODO_INSTANCE`, `UNCOMPLETE_TODO`
- Floater: `CREATE_FLOATER`, `UPDATE_FLOATER`, `DELETE_FLOATER`, `COMPLETE_FLOATER`, `UNCOMPLETE_FLOATER`

Server Mode replays pending mutations through `SyncManager`. Local Mode clears/ignores pending mutations because there is no remote target.

## Tenant Isolation

Every backend query that reads or writes private data must filter by the authenticated `userID`. Admin-only operations that touch other users must be behind centralized admin checks and should avoid returning private task content unless the endpoint explicitly requires it.

## Data Change Checklist

When changing data shape:

- Update shared DTOs and validators first when the contract crosses platforms.
- Update Exposed tables and add a Flyway migration for backend persistence changes.
- Update Android Room entities, DAOs, mappers, cache records, and migration/version handling.
- Update iOS SwiftData entities, mappers, cache records, and widget snapshot logic if affected.
- Update REST docs in `docs/API_GUIDELINES.md`.
- Update architecture and platform READMEs if the data flow changes.
- Add or update tests for recurrence, tenant isolation, sync replay, local mode, and destructive operations.

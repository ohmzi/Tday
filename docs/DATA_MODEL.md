# Data Model

This document describes the durable and local data structures that define T'Day. Keep it aligned with `shared/`, backend Exposed tables, Android Room entities, and iOS SwiftData entities.

## Sources of Truth

| Layer | Files | Purpose |
|-------|-------|---------|
| Shared contracts | `shared/src/commonMain/kotlin/com/ohmz/tday/shared/{model,routes,validation}/` | Serializable DTOs, request/response bodies, enums, route constants, and validators consumed across platforms |
| Backend tables | `tday-backend/src/main/kotlin/com/ohmz/tday/db/tables/` | PostgreSQL schema mapping through Exposed |
| Backend migrations | `tday-backend/src/main/resources/db/migration/` | Flyway SQL history and clean-install schema |
| Android cache | `android-compose/app/src/main/java/com/ohmz/tday/compose/core/data/db/` and `core/data/OfflineSyncModels.kt` | Room entities plus cache records used by repositories |
| iOS cache | `ios-swiftUI/Tday/Core/Data/Database/` and `Core/Model/OfflineSyncModels.swift` | SwiftData entities plus cache records used by repositories |
| iOS widget snapshot | `ios-swiftUI/Tday/Core/Widget/TodayTasksWidgetSnapshotStore.swift` | Versioned App Group payload consumed by the WidgetKit extension |
| Web Local Mode workspace | `tday-web/src/lib/local/localDb.ts` | Browser-storage rows backing the no-login web workspace |

## Core Entities

| Entity | Backend table | Shared/mobile DTOs | Notes |
|--------|---------------|--------------------|-------|
| User | `Users` | `SessionUser`, auth responses | Owns all private data through `userID`; includes role, approval, and `tokenVersion`. |
| Account | `Accounts` | Auth models | OAuth/account compatibility and credential metadata. |
| Todo | `Todos` | `TodoDto`, `CreateTodoRequest`, `UpdateTodoRequest` | Scheduled task with required `due`, optional `rrule`, priority, pinning, ordering, and optional scheduled-task list. |
| Todo instance | `TodoInstances` | `TodoInstancePatchRequest`, `TodoInstanceDeleteRequest` | Per-occurrence overrides/deletions for recurring tasks. |
| Completed todo | `CompletedTodos` | `CompletedTodoDto` | Completion history preserving original task/list details where possible. |
| List | `Lists` | `ListDto`, `ListDetailResponse` | Scheduled-task project/group with color and icon metadata. `ListDto` carries sharing metadata (`myRole`, `isShared`, `memberCount`, `ownerUsername`). |
| List share | `ListShares` (`list_shares`) | `ListMemberDto`, `ListMembersResponse`, `AddMemberRequest`, `UpdateMemberRoleRequest`, `RemoveMemberRequest` | EDITOR/VIEWER membership on a scheduled list. The owner is implicit on `Lists.userID` and never has a share row. No DB-level FKs (Prisma-era databases keep the parent tables outside the schema Flyway migrates); referential cleanup is owned by the services. |
| Floater | `Floaters` | `FloaterDto`, `CreateFloaterRequest`, `UpdateFloaterRequest` | Unscheduled task for Anytime/Floater planning. No `due`. |
| Floater list | `FloaterLists` / `FloaterProject` | `FloaterListDto`, `FloaterListDetailResponse` | Project/group for floaters. Keep separate from scheduled-task lists. Carries the same sharing metadata as `ListDto`. |
| Floater list share | `FloaterListShares` (`floater_list_shares`) | Same share DTOs as scheduled lists | EDITOR/VIEWER membership on a floater list. |
| Completed floater | `CompletedFloaters` | `CompletedFloaterDto` | Completion history for floaters. |
| Preferences | `UserPreferences` | `PreferencesDto`, `PreferencesResponse` | Per-user sorting/grouping/direction preferences, plus `aiSummaryEnabled` and `defaultHomeScreen` (`"scheduled"` \| `"floater"` — which root feed opens on a fresh cold launch; defaults to `"scheduled"`). |
| App config | `AppConfigs` | `AppSettingsResponse`, `AdminSettingsResponse` | Public/admin app settings such as Summary availability. |
| File metadata | `Files` | Internal only | Retained table for cleanup/compatibility paths; there is no active upload/download API surface. |
| Event/auth logs | `EventLogs`, `AuthThrottles`, `AuthSignals`, `VerificationTokens`, `CronLogs` | Internal models | Security, throttling, verification, diagnostics, and operational state. |

## Mobile Probe Contract

`MobileProbeResponse` is the public server-discovery DTO used before a mobile client signs in. It
includes `service`, probe protocol `version`, `serverTime`, plain `appVersion`, and optional
`encryptedCompatibility`. Android and iOS use `appVersion` to display the server release version on
the App Version screen when encrypted compatibility is not available.

## Scheduling Rules

Scheduled tasks and floaters are intentionally different:

- `Todo` requires a due timestamp and can participate in Today, Scheduled, Calendar, recurring instances, reminders, and scheduled-task lists.
- `Floater` has no due timestamp and belongs to the Anytime/Floater root feed.
- A task should not be made "unscheduled" by nulling `Todo.due`; use a floater instead.
- Scheduled-task `listID` values must belong to the authenticated user. Stale or cross-user list IDs are rejected before database writes.
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
├── aiSummaryEnabled
└── defaultHomeScreen
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

## Widget Snapshot Payloads

The Today Tasks widgets do not add backend or shared DTOs. Android builds its widget model directly
from the Room-backed `OfflineSyncState`; iOS writes a versioned JSON snapshot into App Group defaults
for the WidgetKit extension.

The current iOS snapshot schema is version `2` (Today) / `1` (Floater) and includes:

- `schemaVersion`
- `generatedAtEpochMs`
- `title`
- `status` (`setup`, `empty`, or `tasks`)
- `taskCount`
- `tasks`, with each row carrying `id`, `title`, `dueEpochMs` (todo only), and `priority`
- `perList` (iOS only, R7): a `[listId: PerListSnapshot]` map alongside `tasks`, one entry per
  todo/floater list that currently has open items. Each entry carries its own `totalCount` (true
  count) and a capped `tasks` array, mirroring the top-level `taskCount`/`tasks` split — see
  "Per-list configurable widgets (iOS)" below.

Both platforms filter the source cache to pending scheduled tasks due today, sort by due time then
title, cap displayed rows to the widget task limit, and exclude floaters and completed tasks from the
global feed. The global Today aggregate still excludes overdue tasks (due strictly today only); a
per-list widget instance (see below) does not — it includes overdue.

### Per-list configurable widgets (iOS)

iOS widgets (R7) are `AppIntentConfiguration`s: on placement (or via "Edit Widget") the user picks
one specific todo list or floater list, or leaves it unset to keep the original global feed. The
widget extension cannot reach `AppContainer`/SwiftData directly (by design — see the file header on
`WidgetSnapshotFileStore`), so the app additionally writes a lightweight, content-free list catalog,
`widget-lists-snapshot.json` (`[{id, name, kind}]`, `kind` = `"todo"` or `"floater"`), which backs the
configuration picker's `EntityQuery`.

The picked list's TYPE decides the widget's rendered shape, not which of the two widget kinds
(`TodayTasksWidget` / `FloaterTasksWidget`) it was dragged out of: a todo list always renders
due-date-shaped (due times, overdue in red), a floater list always renders undated-shaped. Either
gallery slot can render either shape once configured. Content comes from the SAME two snapshot files
via `perList[listId]` — there is no third, per-instance file; two widget instances configured to the
same list read the same `perList` entry, and WidgetKit itself (not the app) tracks which instance
has which configuration.

## Device Calendar Mirror

The device-calendar mirror (Android and iOS, opt-in, default off) adds no backend tables and no
shared DTOs. It is a one-way projection of the local cache into the OS calendar store, so T'Day
stays the source of truth and nothing is ever read back into the app.

| Concept | Android | iOS |
|---------|---------|-----|
| Calendar | `CalendarContract` calendar with `ACCOUNT_TYPE_LOCAL`, found by account/name | `EKCalendar` on the local `EKSource`, found by stored `calendarIdentifier` |
| Event | One event per cached `TodoItem` | One `EKEvent` per cached `TodoItem` |
| Recurrence | Raw `rrule` written to `Events.RRULE` | `rrule` parsed into `EKRecurrenceRule` by `RecurrenceRuleParser` |
| Opt-in flag / bookkeeping | `CalendarSyncPreferenceStore` (SharedPreferences) | `CalendarSyncPreferenceStore` (UserDefaults) |
| Permission | `READ_CALENDAR` + `WRITE_CALENDAR` | `NSCalendarsFullAccessUsageDescription` (full access) |

Rules:

- Only pending scheduled tasks with a due timestamp are mirrored. Completed tasks and floaters are
  excluded — a floater has no due date, so it has nothing to sit on in a calendar.
- One event per cached todo row, not per occurrence. The cache holds one row per recurring template
  (the client does not expand occurrences), so the task's `rrule` is carried on the event itself.
- Tasks are point-in-time; the mirror gives each event a fixed 30-minute duration because native
  calendars require an end and a zero-length event is unreadable in a day grid.
- Reconciliation is wholesale: the pass rewrites the calendar's contents rather than diffing, and a
  content fingerprint suppresses rewrites when nothing the mirror renders has changed. A fingerprint
  must be stable across process launches, so it uses an explicit hash and never a
  platform-seeded one.
- Writes are confined to T'Day's own calendar. Turning the feature off deletes that calendar.
- Local Mode is fully supported: the calendar is device-local and nothing is uploaded.

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

## Web Local Mode Workspace

The web's no-login workspace is one JSON document in `localStorage`
(`tday.local.workspace.v1`, shaped by `LocalWorkspace` in `tday-web/src/lib/local/localDb.ts`).
It has no sync layer at all — there is no remote target, so no local-ID prefixes and no
pending mutations. Its rows mirror the backend tables one-for-one, and the handlers in
`lib/local/*` answer with the same DTOs the Ktor routes return, so the app's queries and
mutations are identical in both modes.

Differences from the server contract, all deliberate:

- Timestamps use the API's own wire format — a UTC wall clock with no offset
  (`2026-08-04T09:30:00.000`) — because that is what `parseApiDateTime` expects. Due,
  `instanceDate`, and `overriddenDue` are floored to the minute, matching `parseDueMinute`.
- Sharing has no meaning in a single-browser workspace: every list reports
  `myRole: "OWNER"`, `isShared: false`, `memberCount: 0`.
- `ListDto.todoCount` carries the real pending-task count. The server currently leaves it
  at `0` for scheduled lists (only floater lists compute it), and reporting a truthful
  count locally is better than mirroring that gap.
- Summaries always report `source: "logic"`; a browser workspace can't reach a model.
- Server-only routes (accounts, sharing, admin, push, webhooks, API keys, calendar feed)
  have no local handler and fail as a 404 rather than pretending to succeed.
- Clearing the browser's cookies/site data deletes the workspace. Export/import
  (`/api/export`, `/api/import`, same `TdayExport` bundle) is the only way to carry it off
  the device; import stays additive with the same id-remap rule as `ExportRemap`.

## Tenant Isolation

Every backend query that reads or writes private data must filter by the authenticated `userID`. Admin-only operations that touch other users must be behind centralized admin checks and should avoid returning private task content unless the endpoint explicitly requires it.

The single sanctioned exception is list sharing: queries guarded by `ListShareService` (`accessFor`/`sharedListIdsFor`) may widen the `userID` filter to "own rows OR rows in lists shared with me". Visibility includes VIEWER members; mutations require OWNER/EDITOR (viewers fail closed by matching zero rows). List rename/recolor/delete and member management are owner-only. Any new widened `where` clause must go through `ListShareService` — never inline share-table checks elsewhere.

## List Sharing

- Roles: OWNER (the list's `userID`), EDITOR (full task CRUD in the list), VIEWER (read-only). Share rows store only EDITOR/VIEWER.
- Members are added directly by username (closed, admin-approved server); members can leave at any time.
- The picker's typeahead matches username or display name, and people already on the list stay in the results as a disabled "Already a member" row — hiding them made an existing member look like a missing account. Web, Android and iOS all render it that way.
- Shared tasks ride the normal feed queries (`/api/todo?timeline=true`, `/api/floater`), so they appear in members' Today/timeline feeds and mobile offline snapshots.
- Completion history stays per-user in v1: completing a shared task writes a `CompletedTodos`/`CompletedFloaters` row under the completer's `userID`; other members just see the task disappear.
- Membership management is online-only on mobile (no pending mutations); task edits in shared lists stay offline-capable. A member demoted/removed while offline gets 403/no-op on replay, which the sync managers drop.
- Deleting a list removes every member's todos and completion history for that list (the list-scoped cascades are deliberately not user-filtered). Share rows are cleaned up explicitly by `ListService`/`FloaterListService.deleteMany` and `AdminService.purgeUser` — there is no DB-level `ON DELETE CASCADE` on the share tables.
- Realtime: every successful mutation emits a `DomainEvent` over `/ws` to the actor plus all share-connected collaborators (`RealtimePublisher`); events are lightweight "refetch" signals (`todo.changed`, `floater.changed`, `list.changed`, `floaterList.changed`, `list.members`, `completed.changed`).

## Foreign Keys: Flyway Writes Them, Exposed Owns Them

`DatabaseConfig.init()` runs Flyway and *then* `SchemaUtils.createMissingTablesAndColumns`. For
every table in that call's argument list, Exposed compares each live foreign key against the rule
the Kotlin column declares and **drops and recreates any that differ** — so for those tables the
`.references(...)` declaration, not the migration, is what the constraint ends up saying.

`.references(Users.id)` with no `onDelete` is not "unspecified": Exposed reads it as its
PostgreSQL default, `RESTRICT`. That is how `push_subscriptions` ended up `ON DELETE RESTRICT`
despite `V7` creating it `ON DELETE CASCADE`, which made every account that had ever enabled
notifications undeletable — the purge rolled back and the admin panel returned a 500.

Rules for anything under `db/tables/`:

- State `onDelete` explicitly whenever the intended rule is not `RESTRICT`, even when a migration
  already says so. The comment or the migration is not what runs.
- A migration that changes a delete rule on a reconciled table must also change the declaration,
  or the next boot reverts it. Write the constraint the way Exposed writes it — same name
  (`fk_<table>_<column>__<targetcolumn>`, lower-cased) and an explicit `ON UPDATE RESTRICT` —
  or Exposed re-issues the DDL on every start. `V26__align_cascade_delete_constraints.sql` is
  the worked example; it pairs `push_subscriptions."userID"` and `todo_instances."todoId"` with
  the `ReferenceOption.CASCADE` their columns now declare.
- Changing only one side is worse than changing neither. A Flyway-only change is reverted on the
  next boot; a Kotlin-only change lets Exposed run that `ALTER TABLE` against a live database
  outside Flyway, which is how the `push_subscriptions` divergence happened in the first place.
  `CascadeDeleteTest` guards the declaration side: `TestDatabase` builds H2 from the Exposed
  tables, so dropping an `onDelete` fails there rather than in production months later.
- `AdminServiceImpl.purgeUser` deletes from every table that references `"User"` regardless of
  the declared rule, and `AdminPurgeTest` fails if a new one is added without being listed in
  `USER_OWNED_CHILD_COLUMNS`. Do not rely on a live `CASCADE` to cover an account delete. The
  same goes for the ordered child deletes in `ListService.deleteLists` and `TodoService` — a
  cascade makes them redundant, not wrong, and they are what survives the next reconciliation
  pass.

Tables absent from the `createMissingTablesAndColumns` list (`user_api_keys`,
`calendar_feed_tokens`, `webhook_subscriptions`, `user_security_questions`, `task_steps`) keep
whatever their migration created. Their declarations now state `CASCADE` to match, so adding one
of them to that list cannot silently downgrade it.

## Data Change Checklist

When changing data shape:

- Update shared DTOs and validators first when the contract crosses platforms.
- Update Exposed tables and add a Flyway migration for backend persistence changes. If the change
  touches a foreign key, read "Foreign Keys: Flyway Writes Them, Exposed Owns Them" first.
- Update Android Room entities, DAOs, mappers, cache records, and migration/version handling.
- Update iOS SwiftData entities, mappers, cache records, and widget snapshot logic if affected.
- Update REST docs in `docs/API_GUIDELINES.md`.
- Update architecture and platform READMEs if the data flow changes.
- Extend the portable export bundle (`shared/.../model/ExportModels.kt` + `ExportService`) and, if the wire shape gains a field older importers must not drop, bump `TdayExport.CURRENT_SCHEMA_VERSION`.
- Add or update tests for recurrence, tenant isolation, sync replay, local mode, and destructive operations.

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
| Completed floater | `CompletedFloaters` | `CompletedFloaterDto` | Completion history for floaters; survives the source list being deleted (`listDeleted`), and undo recreates it under its original name/color — see `docs/design/completed-floaters-durability.md`. |
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
- List deletion must preserve completed history metadata (`listName`, `listColor`) where the backend/mobile model supports it. For floaters specifically, deleting a list no longer deletes its `CompletedFloaters` rows (backend: `ON DELETE SET NULL`, plus an unconstrained `originalListID` snapshot; Android and iOS: `FloaterListRepository` stops pruning `completedFloaters` on list delete, in both the staging step and the real commit step) — undoing such an item recreates the list under its original name/color, converging duplicate undos from the same deleted list onto one recreated list. `CompletedFloaterDto.listDeleted` / `CachedCompletedFloaterRecord.listDeleted` flag this case. See `docs/design/completed-floaters-durability.md`. The identical bug for scheduled `Todo`/`CompletedTodos` is a deliberate, separate product decision and is untouched.

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

The Today Tasks widgets do not add backend or shared DTOs. Android writes a Keystore-encrypted JSON
snapshot per widget (`feature/widget/snapshot/WidgetSnapshot`) built from the Room-backed
`OfflineSyncState`; iOS writes a versioned JSON snapshot into App Group defaults for the WidgetKit
extension.

The current iOS snapshot schema is version `4` (Today) / `1` (Floater) and includes:

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
- `dayStartEpochMs` / `dayEndEpochMs` (Today, schema 3): the local day `status`/`taskCount`/`tasks`
  describe. Absent in older snapshots, which the widget then dates by `generatedAtEpochMs`.
- `upcomingDays` (Today, schema 3): the same "due today" feed for each of the next six local days
  (`dayStartEpochMs`, `dayEndEpochMs`, true `taskCount`, `tasks` capped at 20), and on each todo
  `perList` entry `upcomingTotalCounts` (one per upcoming day) plus `upcomingTasks` (every row that
  makes the per-list cap on any upcoming day, in sort order). The widget extension cannot open the
  cache, so these let it render the day that contains its entry date after midnight without a new
  write. The Apple Watch mirror sends the snapshot without them. See `docs/WIDGET_SYNC.md`
  ("Day rollover (iOS)").
- `completedCount`, `overdueCount`, `overdueTasks` (Today, schema 4), on the snapshot and on each
  `upcomingDays` entry: the header ring's done count — tasks due that day that are already
  completed, counted by **due date**, not completion time, so finishing another day's task never
  fills today's ring — and that day's Overdue section: open tasks due before the day starts (the
  app's Today "Earlier" bucket), true count plus rows capped at 20. For an upcoming day the overdue
  set includes today's still-open tasks. Never part of `taskCount`. Absent fields decode as zero /
  empty. The Apple Watch mirror keeps the counts and drops `overdueTasks`.
- `openByList` (iOS Today snapshot, schema 5): the List widget's whole lists — for each todo list
  with anything open, every open task in it whatever day it is due (true `totalCount`, `tasks`
  capped at 20, the app's sort order), keyed by list id. Unlike `perList` it has no day window: a
  row's time-or-day label and overdue tint are worked out as the widget renders. Floater lists need
  no twin — the floater snapshot's `perList` is already the whole list. The Apple Watch mirror drops
  it. Android keeps the List widget's snapshot per placed instance instead
  (`widget-list-snapshot-<appWidgetId>.json`, the same `WidgetSnapshot` shape plus `listName` /
  `listMissing`).

Android's Today snapshot carries the same day window: `dayStartEpochMs` / `dayEndEpochMs` for its own
day, and `upcomingDays` for the next six (`dayStartEpochMs`, `dayEndEpochMs`, true `taskCount`,
`rows` capped at 20). The widget renders the day containing now (`WidgetSnapshot.todayAt`), so it
turns over at midnight without a rebuild; a snapshot written before `upcomingDays` existed decodes
with none and covers only its own day. It carries the same `completedCount`, `overdueCount` and
`overdueRows` (iOS `overdueTasks`) on its own day and on each upcoming one, with the same rules and
cap; missing fields decode as zero / empty.

Both platforms filter the source cache to pending scheduled tasks due today, sort by due time then
title, cap displayed rows to the widget task limit, and exclude floaters and completed tasks from the
global feed. The global Today aggregate's own rows and `taskCount` still exclude overdue tasks (due
strictly today only) — overdue travels separately, in `overdueCount`/`overdueRows`, and renders as
its own section below them; a per-list widget instance (see below) folds overdue into its rows.

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

The third kind, `ListTasksWidget`, has its own configuration intent (`SelectListWidgetListIntent`,
no "unset means the default feed" fallback) and reads `openByList[listId]` for a todo list or the
floater snapshot's `perList[listId]` for a floater list. A picked list missing from the catalog shows
the setup state again with a "deleted" line; an unreadable catalog is not taken as a deletion.

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

The list and floater-list create/update mutations carry the list's own fields beside `name`/`color`/`iconKey`: `reusable` (floater lists only), `defaultPriority`, and `defaultPriorityChanged`, which tells "clear the default" apart from "leave it alone". All three are nullable, and null means "not part of this mutation". Both clients persist them with the queued mutation — Android's `pending_mutations` Room table since schema v13 (`Migration12To13`), iOS's SwiftData `PendingMutationEntity` — because the queue is read back from storage before a replay, and a field the store dropped came back null: an offline "Reusable off" then replayed as "leave it alone", and the next sync switched it back on.

`staged` (Android/iOS, default `false`) marks a `DELETE_LIST`/`DELETE_FLOATER_LIST` mutation written by the delayed-commit delete's stage step (`ListRepository.stageDeleteList`/`FloaterListRepository.stageDeleteList`) while the Undo toast is still open. A staged delete is never replayed to the server (the whole point of staging is that Undo needs no network trace), but it counts the same as a real pending delete for `SyncManager`'s merge-time resurrection guard, so a pull-to-refresh landing inside the undo window can't write the still-server-side list back into the cache. The commit step (`deleteList()`) replaces the staged marker with a normal pending mutation of the same kind; Undo removes the marker outright.

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
- Server-only routes (accounts, sharing, admin, push, API keys)
  have no local handler and fail as a 404 rather than pretending to succeed.
- Clearing the browser's cookies/site data deletes the workspace. Export/import
  (`/api/export`, `/api/import`, same `TdayExport` bundle) is the only way to carry it off
  the device; import stays additive with the same id-remap rule as `ExportRemap`.
- `LocalCompletedFloaterRow`/`LocalFloaterListRow` mirror the backend's floater-completion
  durability fix (see `docs/design/completed-floaters-durability.md`): deleting a floater
  list detaches its `CompletedFloaters` rows (`listID` cleared) instead of deleting them,
  and `originalListID`/`recreatedFromListID` are the same unconstrained correlation pair
  the backend uses so `uncompleteFloater` can find-or-create the list under its original
  name/color. **Floaters only** — the identical bug in `CompletedTodos`/scheduled lists is
  left as-is, matching the backend's scope.

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
`calendar_feed_tokens`, `user_security_questions`, `task_steps`) keep
whatever their migration created. Their declarations now state `CASCADE` to match, so adding one
of them to that list cannot silently downgrade it.

## Migrations on a Clean Install

A clean install runs `DatabaseConfig.init()` against an empty database: Flyway's whole chain
first, `SchemaUtils.createMissingTablesAndColumns` after. Tables that are only ever created by
that Exposed call (everything but the ones a migration creates itself) therefore **do not exist
while the migrations run** on a clean install, even though they always existed on a database that
had booted before the migration shipped. Today that is at least `floaterproject`, `completedfloaters`
and `floaters`.

A migration that touches such a table must tolerate it being absent. The Kotlin declaration
already carries the column or constraint, so on a clean install Exposed creates the table correctly
afterwards and the migration has nothing to do:

- `ALTER TABLE IF EXISTS <table> ...` for `ALTER` statements.
- `UPDATE` / `INSERT` / `DELETE` have no `IF EXISTS`; wrap them in
  `DO $$ BEGIN IF to_regclass('public.<table>') IS NOT NULL THEN ... END IF; END $$;`
  (see V27 and V29).
- `REFERENCES <table>(...)` inside a guarded `ALTER TABLE IF EXISTS` is skipped with the statement.

V19, V27 and V31 each shipped without this and left every new self-hosted install unable to start.
`FreshInstallMigrationTest` runs the real chain through `DatabaseConfig.init()` against an empty
`postgres:15` and fails the next time it happens; it needs Docker and is skipped without it.

## Data Change Checklist

When changing data shape:

- Update shared DTOs and validators first when the contract crosses platforms.
- Update Exposed tables and add a Flyway migration for backend persistence changes. If the change
  touches a foreign key, read "Foreign Keys: Flyway Writes Them, Exposed Owns Them" first.
  If it touches a table Exposed creates after Flyway (`floaterproject`, `completedfloaters`, ...),
  read "Migrations on a Clean Install" first.
- Update Android Room entities, DAOs, mappers, cache records, and migration/version handling.
- Update iOS SwiftData entities, mappers, cache records, and widget snapshot logic if affected.
- Update REST docs in `docs/API_GUIDELINES.md`.
- Update architecture and platform READMEs if the data flow changes.
- Extend the portable export bundle (`shared/.../model/ExportModels.kt` + `ExportService`) and, if the wire shape gains a field older importers must not drop, bump `TdayExport.CURRENT_SCHEMA_VERSION`.
- Add or update tests for recurrence, tenant isolation, sync replay, local mode, and destructive operations.

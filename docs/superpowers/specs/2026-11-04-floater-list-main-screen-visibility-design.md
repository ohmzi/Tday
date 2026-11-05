# Floater list main-screen visibility: design

## Intent

Some floater lists hold tasks that don't belong in the everyday Anytime feed. A list owner can
turn off "Show on main screen" so the list's tasks live only inside that list. The setting
belongs to the list, and every task follows it, so nothing is stored per task and moving a task
between lists changes its visibility with no extra step.

The behaviour has to be identical on web, Android and iOS, in Server Mode and Local Mode. It is
driven by one stored value and one rule.

## Goals

- A per-list boolean on floater lists, on by default. Every existing list keeps showing.
- Set from the create and edit list sheets on all three clients, next to Reusable.
- Tasks in a hidden list leave the Floater main feed, the Floater widget and the Floater summary.
- A How-To topic in the guide, in every locale.

## Non-goals

- Per-member visibility on shared lists. One value applies to everyone.
- Any marker on the list tile.
- Scheduled lists. This is a floater-list concept, like Reusable.
- New MCP tool parameters. `tday_create_list` keeps its current shape, and new lists default to
  showing.

## Anti-goals

- A task must never become unreachable. It stays in its list, in search and in Completed.
- A task must never vanish because metadata is missing. Unknown or absent means visible.
- Clients must not disagree. A client missing the flag treats it as `true`, never `false`.

## Behaviour

| Surface | When the list has "Show on main screen" off |
|---|---|
| Floater main feed rows | Hidden |
| Floater widget (the all-floaters widget) | Hidden |
| Per-list widget set to that list | Still shows the tasks |
| Floater summary (no list selected) | Not counted or named |
| That list's own summary | Still covers the tasks |
| Main-screen search | Still finds the tasks |
| List tile and its count | Unchanged, no marker |
| List screen | Unchanged |
| Completed history | Unchanged |
| "All done" scene and celebration | Hidden tasks still count as pending, so it does not show |

When only hidden tasks remain, the feed shows no rows and no scene. It goes straight to the
Completed tile and My Lists, and finishing the last visible task fires no celebration. No new
empty-state copy is added.

Tasks with no list are always visible, and so are tasks whose list is not in the loaded metadata.

## Data

- Field name: `showOnMainScreen`, boolean, default `true`.
- Postgres: migration `V33` on `floaterproject`, `NOT NULL DEFAULT TRUE`, using
  `ALTER TABLE IF EXISTS ... ADD COLUMN IF NOT EXISTS` like V19 (Exposed owns this table, so a
  clean install must not fail), plus the matching column on the `FloaterLists` Exposed table.
- Shared contracts in `ListModels.kt`:
  - `FloaterListDto.showOnMainScreen: Boolean = true`
  - `CreateFloaterListRequest.showOnMainScreen: Boolean = true`
  - `UpdateFloaterListRequest.showOnMainScreen: Boolean? = null` (null means unchanged)
- Android: Room column on `cached_floater_lists` with `defaultValue = "1"`, a nullable column on
  `pending_mutations`, a new migration, and cache and sync mappers.
- iOS: optional in SwiftData, where `nil` means `true`. The `reusable` code uses `?? false`, so
  copying it unchanged would hide every list after an upgrade.
- Web Local Mode: `localDb`/`localLists` read a missing field as `true`.
- Export and import carry the field through `FloaterListDto`. Older exports import as `true`.
- `updatedAt`-style change detection in the sync managers compares the field too (Android
  `SyncManager` compares `reusable` around line 2002, and the new field needs the same check).

Only the list owner can change it, because `FloaterListService.update` is already owner-only.
Members of a shared list see the result, and the toggle follows the same owner gating the list
settings sheet already has.

## Logic

One rule: a floater appears on main-screen surfaces unless its list is known and has
`showOnMainScreen == false`.

The Kotlin rule goes in `shared/.../floater/`, beside `FloaterRestingTier.kt`. Backend and Android
call it. iOS and web can't link the Kotlin, so each has a small mirror, and a guardrail test keeps
the mirrors honest in the same way as the resting-floater and summary parity guardrails.

Filtering happens on the client at each surface. The API keeps returning every floater, because
search and list screens need them.

### Filter sites

| Surface | Web | Android | iOS | Backend |
|---|---|---|---|---|
| Feed rows | `NativeFloaterTaskHomeDashboard` | `TodoListScreen` / `TodoListViewModel` feed | floater root feed | n/a |
| Widget | n/a | `buildFloaterWidgetSnapshot` | `FloaterTasksWidgetSnapshotStore.makeSnapshot` | n/a |
| Summary (server) | n/a | n/a | n/a | `buildSummaryTasks`, `TodoRoutes.kt` |
| Summary (local) | `localSummary.ts` | `TodoRepository.summarizeLocally` | none, iOS summary is server-only | n/a |

Traps:

- Web: `sortedFloaters` feeds both the search results and the feed rows. Split them, so search
  stays unfiltered and only the rows are filtered.
- Web and the native clients: `pendingFloaterCount`, which drives the empty state, stays
  unfiltered.
- List tile counts stay unfiltered.
- Android and iOS: tapping a main-screen search result scrolls to the row in the feed, and a
  hidden task has no row. For a hidden task, open its list and focus the task instead. Web
  already routes list tasks to the list screen.
- iOS: the widget snapshot's `perList` is built from the same floaters as `tasks`. Filter `tasks`
  only, or per-list widgets lose hidden lists. The Android per-list builder already reads from
  `state.floaters` directly and must stay unfiltered.
- Backend summary: filter only when no `listId` is given, and resolve hidden lists across owned
  and shared lists.
- Widgets must refresh when the setting changes, not only when tasks change.
- Realtime: a list update from another device or the owner must refresh the feed.

## UI

- A toggle row labelled "Show on main screen", with a short hint such as "Off keeps its tasks
  inside this list only." Final copy is the builder's, kept short.
- Web `FloaterListFormSheet`, Android list settings and create sheets beside
  `ListSettingsReusableToggleRow`, and the iOS equivalents.
- Strings go in every web locale file, Android `strings.xml` for each locale, and iOS following
  its current pattern.
- Toggling needs no animation of its own beyond what the feed already does.

## Guide

- New topic `hidden-floater-lists` (name is the builder's call) in `GuideSectionId.ORGANIZING`,
  directly after `REUSABLE_LISTS`, on all three platforms, `sinceVersion = "0.8.15"`, with a real
  Lucide glyph present on every platform (for example `eye-off`, adding assets if needed).
- Its body explains what hides, what doesn't (search, list screen, Completed), and how to change
  it.
- Strings in all 10 `tday-web/messages/<locale>.json` files, then
  `./gradlew :shared:exportGuideContent`, and commit the regenerated artifacts.

## Docs

Update `docs/DATA_MODEL.md`, `docs/API_GUIDELINES.md`, `docs/ARCHITECTURE.md` where the rule
matters, and the platform READMEs if their feature lists cover list settings.

## Telemetry

Nothing new. This adds no failure path, and the flag isn't recorded.

## Verification

- Unit tests for the shared rule, including no-list and unknown-list cases, plus the web and iOS
  mirrors.
- Backend tests: default `true`, update, owner-only, summary filter with and without `listId`,
  shared-list case, migration on a clean database.
- Android and iOS: migration keeps existing lists visible, widget filter keeps per-list intact.
- Web: feed hides, search still finds, empty state stays off while hidden tasks remain.
- Compile and test each area per `AGENTS.md`. iOS can't be built on this Linux host, so it gets a
  parse check here and real verification in macOS CI.

## Left to the builder

- Exact Postgres column casing, following the nearest migration.
- Room schema version numbers.
- Final toggle copy and translations.
- Whether the shared rule takes hidden list ids or list objects.
- Version bump. `0.8.15` assumes the next release, so adjust `sinceVersion` if it ships later.

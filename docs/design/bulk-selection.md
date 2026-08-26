# Design note: bulk selection and bulk actions

Status: accepted, foundation merged. Ships in **0.7.4**.
Scope: web (React), Android (Compose), iOS (SwiftUI). Backend: **no change**.

The feature: in a task list a user enters a selection mode, picks individual tasks or
uses **Select all**, and then applies **one** action to the whole selection —
**Complete**, **Delete**, **Priority**, or **Move to another list**.

This document is the contract. The three surface implementations are built in parallel
from this note plus their own platform survey, so anything not written here is a
divergence waiting to happen. Where this note and a platform habit disagree, this note
wins; where this note is silent, copy the single-task behaviour that already exists on
that platform.

---

## 1. Architectural decision: client-side fan-out, no new backend route

**Decision.** Every bulk action is implemented as N calls to the single-item endpoints
that already exist:

| Action   | Endpoint (scheduled)                                    | Endpoint (floater)             |
|----------|---------------------------------------------------------|--------------------------------|
| Complete | `PATCH /api/todo/complete` `{id, instanceDate?}`         | `PATCH /api/floater/complete`  |
| Delete   | `DELETE /api/todo` `{id}`                                | `DELETE /api/floater` `{id}`   |
| Priority | `PATCH /api/todo/prioritize` `{id, priority}`            | `PATCH /api/floater/prioritize`|
| Move     | `PATCH /api/todo` `{id, listID}`                         | `PATCH /api/floater` `{id, listID}` |

No shared DTO changes. No `ApiRoutes` constant. No `localApi.ts` case. No new
`MutationKind`. No deploy is coupled to this feature.

### Why not a batch endpoint

1. **The safety story is already client-side.** Undo on all three platforms is
   delayed-commit: the client prunes its own cache, shows a toast, and only sends the
   request when the window closes without Undo. `docs/PLAN_UNDO_TOAST_DELETE.md`
   records the decision that no server soft-delete/restore is needed. A batch route
   would change only *what fires at commit time* — it buys the user nothing.
2. **The offline queues already model all four operations per item.** Android's
   `MutationKind` has `DELETE_TODO`, `SET_PRIORITY`, `COMPLETE_TODO`,
   `COMPLETE_TODO_INSTANCE` and `UPDATE_TODO` (which carries `listId`); iOS mirrors it.
   Fan-out enqueues N existing records and `SyncManager` replays them with zero new
   code. A batch route needs new mutation kinds, a new id-list payload field, new
   replay branches, and a migration of the persisted pending-mutation store on Room
   *and* SwiftData — a far larger blast radius than the route itself, on the two
   platforms that cannot be compiled or tested on the maintainer's Linux box.
3. **Local Mode has no server.** Web's `localApi.ts` dispatches on `"METHOD /path"`;
   Android/iOS short-circuit before the network. A per-item local path is required
   either way, and fan-out reuses it for free. A new route without a matching
   `localApi.ts` case silently 404s in Local Mode.
4. **Self-hosted version skew.** `version.json` sets `compatibility.mode = "exact"`
   with `updateRequired = true`, so a mobile client whose version differs from the
   server is already rejected with a friendly 426/409. A brand-new route bypasses
   that gate and instead 404s for anyone who updated the app before the server.
   Routes that have shipped for several releases have zero deployment risk.
5. **Cost.** A route touches the shared DTO, `ApiRoutes`, the route file, the service,
   a backend test, the `docs/API_GUIDELINES.md` surface table, plus three clients —
   one of which (iOS) hand-mirrors the contract in Swift and cannot be compiled here.

### What fan-out genuinely costs, and how it is contained

- **Not atomic.** N transactions, so a dropped connection can leave a half-applied
  bulk action. Contained by *not claiming atomicity*: collect per-item outcomes and
  report partial failure honestly (§6).
- **Side-effect amplification.** Every mutation publishes one `/ws` event to the actor
  and every share collaborator, dispatches one webhook POST per matching subscription,
  fires one UnifiedPush data-changed POST per subscribed device, and invalidates the
  server cache. Nothing coalesces, and web's realtime listener invalidates queries on
  every message with no debounce.
- **Rate limit.** The `api_global` policy covers all of `/api/` at
  `API_RATE_LIMIT_MAX` (default **180**) requests per `API_RATE_LIMIT_WINDOW_SEC`
  (default **60**), keyed per authenticated user. A large select-all would take 429s
  partway through — a *partially applied destructive action*.

Both of the last two are contained by the same lever: **a hard cap of 100 tasks per
bulk action**, plus bounded concurrency (§5). The cap lives in shared Kotlin at
`shared/src/commonMain/kotlin/com/ohmz/tday/shared/bulk/BulkSelectionPolicy.kt`
(`BulkSelectionPolicy.MAX_SELECTION`) and is covered by `:shared:jvmTest`. Android
reads it directly; web and iOS mirror the literal with a comment pointing back here.

### When to revisit

If real users hit the cap, or a webhook subscriber complains about storms, add `ids`
to `DELETE /api/todo` modelled byte-for-byte on the existing
`DeleteListRequest(id: String? = null, ids: List<String> = emptyList())` /
`DeleteListResponse(message, deletedIds)` served by `ListService.deleteMany`. That
shape is wire-compatible in both directions — an old client's `{"id": "..."}` still
parses because `ids` defaults to empty, and a new client's single delete still works
against an old server — so only the `ids`-only call needs the new server. It is a
cheap migration and it is the path the repo already took for lists. Keep it in
reserve; do not take it now.

### Known backend trap this feature must route around (not fix)

`TodoServiceImpl.completeTodo` branches
`if (rrule == null) { mark Todos.completed } else if (instanceDate != null) { write TodoInstances }`.
With `rrule != null` **and** `instanceDate == null` it inserts a `CompletedTodos`
history row and neither marks the parent complete nor records an occurrence — the task
stays visible and history gains a phantom entry. Bulk complete must therefore **always**
send the occurrence's `instanceDate` for a recurring row (§4.1). Fixing the server guard
is a separate, deploy-coupled change and is deliberately out of scope: a client must not
rely on a fix that self-hosters have not deployed.

Also note: `update`, `prioritize` and `completeTodo` return `Unit.right()` even when the
tenant/share filter matched **zero** rows; only `delete` returns a count. A fan-out
therefore cannot distinguish "applied" from "silently skipped" except by HTTP-level
errors. Do not invent a success count from the responses (§6).

---

## 2. Selection model

### 2.1 Scope and lifetime

- Selection is **screen-local**. It never survives navigating away, switching list
  mode, changing the scoped list, or a completed action.
- Selection is **not** ViewModel/global state on mobile: both mobile ViewModels
  re-hydrate their item list on every cache-version bump, and selection held there
  would fight that. Hoist it to the screen (Compose `rememberSaveable` keyed on
  mode + listId; SwiftUI `@State`; a React provider mounted by the container).
- Entering selection mode is only possible when the list has at least one task **and**
  the surface is not read-only (`isViewerList` on mobile, `readOnly` /
  `myRole === "VIEWER"` on web). Viewer lists get no Select affordance at all.

### 2.2 Identity

The selection set holds **row ids**, not task ids.

- Web row ids are composite: `` `${todoId}:${instanceEpochMs}` `` (built by
  `get-todo.ts`, `get-todo-timeline.ts`, `get-list-todos.ts`). Requests must send
  `canonicalTodoId(id)`, and `todoInstanceTimestampFromId(id)` where an instance date
  is needed.
- Android/iOS `TodoItem.id` already encodes the occurrence; `canonicalId` +
  `instanceDate` are the fields the repositories need.

Rule: **select by row id, act with the full row object.** Never reconstruct a request
from the id string alone.

### 2.3 Select all

**Select all selects every task in the screen's current result set after search and
scope filtering** — the same set the screen would render if you scrolled to the end and
expanded every section. It never reaches tasks on other screens, other lists, completed
history, or the server beyond what the screen already holds.

Concretely:
- Web: all of the container's filtered list, **not** just the currently paged slice
  (`AllTasksTimelineContainer` pages at `PAGE_SIZE = 10`).
- Android: all items across every section, **including collapsed sections**.
- iOS: `groupedSections.flatMap(\.items)` of the search-filtered timeline.

### 2.4 The cap

`BulkSelectionPolicy.MAX_SELECTION = 100`.

- `selectAll()` takes at most 100 rows **in display order from the top**, deterministically.
- When the result set is larger, the selection bar shows
  `bulkSelectionCapped` — "100 selected — the most one action can cover" — in place of
  the plain count. No toast.
- Tapping an unselected row while already at 100 does **not** select it; show the same
  capped label. No toast, no error.
- Deselecting frees capacity again.

### 2.5 Reconciliation and exit

- On every re-render/re-hydration, **intersect the selection with the currently visible
  row ids**. A task that synced away, was completed elsewhere, or was filtered out by a
  search edit must drop out of the selection silently.
- If reconciliation **emptied a selection that had rows in it**, exit selection mode.
  Note the precondition: you always *enter* with an empty selection, so a bare
  "selection is empty → exit" test makes the mode impossible to enter, and turns
  **Deselect all** into a synonym for Cancel even though the copy table lists them as
  two separate controls. Exit only when rows actually dropped out and nothing is left.
- Exit also on: Cancel, system back (Android `BackHandler`, registered *after* the
  existing exit-to-launcher handler so it wins — Compose dispatches to the most
  recently added enabled callback, which is why the search handler in that file is
  likewise "registered last so back dismisses the field before it leaves the list"),
  Escape (web), navigating away, and
  **after any bulk action is dispatched** (see §4 — the mode always closes, whether or
  not the action skipped rows).

### 2.6 What selection mode suppresses

While selecting, on every surface:

- Swipe-to-reveal row actions are disabled, and any open row is closed on entry.
- Long-press drag-to-reschedule is disabled (Android `detectDragGesturesAfterLongPress`
  via `dragEnabled = false`; iOS `TodoInAppDragModifier(enabled:)`; web's dnd-kit
  sensors).
- The row's complete toggle becomes the selection checkbox — a plain tap must never
  complete a task while selecting.
- Tapping anywhere on the row body toggles selection (on mobile this replaces the
  swipe-hint tap; on web it replaces the hover action cluster).
- The create FAB / root dock is hidden or replaced by the selection action bar; the two
  must never overlap.

### 2.7 Entry point

**An explicit "Select" button in the screen's existing header action cluster.** Not a
long-press: long-press already starts drag-to-reschedule on five of seven Android/iOS
list modes, and stealing it would be an untestable gesture-arbitration bug on a build
the user cannot try before it ships.

Icon: the Lucide glyph `circle-check-big` (present on web, Android
`ic_lucide_circle_check_big.xml`, and iOS `LucideCircleCheckBig.imageset`). Never a
platform-native icon set — see `docs/ICONS.md`.

---

## 3. The four actions, and only these four

| Action     | Applies to                   | Confirmation                              | Undo                       | Toast on success                |
|------------|------------------------------|-------------------------------------------|----------------------------|---------------------------------|
| Complete   | whole selection              | none                                      | **yes** — one batch toast  | `{{count}} tasks completed`     |
| Delete     | non-recurring subset         | **required, always**                      | **yes** — one batch toast  | `{{count}} tasks deleted` + Undo |
| Priority   | non-recurring subset         | none                                      | no                         | **none — silent**               |
| Move       | non-recurring subset         | **only when sources differ** (§4.4)       | no                         | **none — silent**               |

Priority and Move are edits, and the unified toast policy
(`tday-web/src/hooks/use-todo-action-toast.ts:29`, mirrored in three Android ViewModel
doc comments) says create and edit succeed silently. The rows visibly change and
selection mode closes — that *is* the feedback. This is a deliberate decision, not an
oversight; do not add a success toast.

**Exactly one toast per bulk action.** Android's `TdayApp` holds a single `activeToast`
and iOS's `SnackbarManager` a single `content`, so N toasts means the user can only
ever undo the last one while N independent commit timers run. One coordinator/scheduler
call must wrap the whole batch — the shape `MorningSweepViewModel.sweepAllToToday()` /
`MorningSweepScreen.sweepAllToToday()` / `MorningSweepSheet.sweepAllToToday()` already
use.

---

## 4. Per-action semantics

### 4.1 The recurring rule (read this once, apply it everywhere)

> **A recurring occurrence may be selected and bulk-completed as the occurrence it
> represents. It is never eligible for bulk Delete, bulk Priority, or bulk Move.**

Rationale: complete has a per-occurrence route on every platform and the clients
already send `instanceDate` for a single complete. The other three do not:
`DELETE /api/todo` removes the **whole series** (`DELETE /api/todo/instance` is the
per-occurrence route, used today only by the calendar, and web's single-task delete
does not use it), and priority/move go through the full-record `PATCH /api/todo`, which
also hits the series. Silently destroying or re-listing an entire recurring series from
a multi-select is the single worst failure mode this feature could ship, and Morning
Sweep already set the precedent of excluding recurring tasks from a batch operation.

Implementation consequence — define once per screen:

```
effectiveSet(action) = action == COMPLETE ? selection : selection.filter { !isRecurring }
```

- If `effectiveSet` is empty, the button is **disabled** (Delete / Priority / Move).
- If `effectiveSet` is smaller than the selection, the confirmation dialog or picker
  states the effective count with `bulkAppliesTo`:
  "Applies to {{count}} of {{total}} — repeating tasks are skipped." No separate toast.
- `isRecurring` is `rrule != null` (web: `Boolean(todo.rrule)`).

Bulk complete **must** send `instanceDate` for every recurring row (§1, backend trap).

### 4.2 Complete

1. Snapshot the effective set.
2. Remove those rows from the in-memory/query view only — **do not touch disk or the
   server yet**. This is exactly what single complete already does.
3. Show **one** undoable-complete toast for the batch:
   - web `showTodoCompletedToast` → `useUndoableDelete` (5 s),
   - Android `UndoableDeleteCoordinator.showUndoableComplete(...)` (8.5 s commit),
   - iOS `UndoableDeleteScheduler.schedule(...)` (8.5 s commit).
4. `onCommit` loops the existing per-item complete call (`completeTodo` /
   `completeFloater`), then calls `reminderScheduler.rescheduleAll()` **once** at the
   end, not per task.
5. `onUndo` restores the snapshot (web: invalidate; mobile: restore `previousItems` /
   `hydrateFromCache`). Nothing was sent, so undo is lossless.

No confirmation: complete is reversible from the undo toast and, after that, from the
Completed history screen.

### 4.3 Delete — the guarded one

Two layers, in this order, both mandatory:

**Layer 1 — confirmation dialog, always, no exceptions.**
Use the platform's existing house destructive dialog, not a stock alert:
Android `ListDeleteConfirmationDialog` (TodoListScreen.kt), iOS
`ListDeleteConfirmationOverlay` / `TdaySheetOverlayCard`, web a `Dialog` in the shape
of the `ListSidebarSection` bulk-delete dialog (but with **i18n keys**, unlike that
dialog's hardcoded English).

The dialog must state the **exact count** in both the title and the confirm button:

- Title: `Delete {{count}} tasks?` (one: `Delete this task?`)
- Body: `This removes them from every list, along with their completed history. You'll have a few seconds to undo.`
- Plus `bulkAppliesTo` when recurring rows were skipped.
- Confirm: `Delete {{count}} tasks` (one: `Delete task`), in the error/destructive colour.
- Cancel on the left, in the primary colour. Haptic on confirm where the surrounding
  code already uses one.

Never wire a one-tap bulk delete.

**Layer 2 — the existing undo, unchanged.**
Only *after* confirm: stage the whole batch (prune the local/query cache; on mobile
collect the `StagedTodoDeletion` / `StagedFloaterDeletion` records), then **one**
`showUndoableDelete` / `undoableDeleteScheduler.schedule` call for the batch.
`onCommit` loops the real per-item delete; `onUndo` restores every staged record and
calls `reminderScheduler.rescheduleAll()` once.

Because undo genuinely exists, the dialog body promises undo rather than claiming
permanence. Do **not** invent a second undo framework, and do not change the 5 s / 8.5 s
windows — Android's 8 500 ms commit is deliberately tuned against
`TOAST_AUTO_DISMISS_WITH_ACTION_MS = 8_000`.

Hazard to be aware of (pre-existing, documented in `docs/PLAN_UNDO_TOAST_DELETE.md`):
during the staged window a refetch or realtime pull can resurrect the pruned rows. With
N tasks staged that exposure is N× wider. This is not a regression introduced here and
must not be worked around with a bespoke mechanism; it is one more reason the cap
exists.

### 4.4 Priority

- Picker: the platform's existing single-choice priority selector — Android
  `TdayCenteredSelectorDialog`, iOS `TdayCenteredSelectorCard` + `TdayCenteredSelectorRow`
  (the `CreateTaskSheet.selectorOverlay(for: .priority)` presentation), web
  `PriorityDropdownMenu`. Options are the canonical `Low` / `Medium` / `High` values
  with the Normal / Important / Urgent labels.
- No confirmation, no toast on success.
- Prefer the **dedicated** prioritize path over a whole-record PATCH:
  - web: `usePrioritizeTodo` / `usePrioritizeListTodo` already exist, are already wired
    into `TodoMutationProvider`, and are currently consumed by no UI — use them.
  - iOS: `TodoRepository.setPriority` exists with zero call sites, but
    `updateSimpleTodoMutation` optimistically maps `nextState.todos` only and never
    `nextState.floaters`. Either fix that mapping or route floaters through
    `updateFloater`; do not ship the optimistic gap.
  - Android: `MutationKind.SET_PRIORITY` has a complete, unused replay implementation in
    `SyncManager`. Using it is the lighter path; `updateTodo` with a rebuilt payload is
    the acceptable fallback.
- On failure: one error toast (§6).

### 4.5 Move to another list

- Target picker: the platform's existing list picker — web `ListDropdownMenu` (already
  excludes `VIEWER` lists), Android/iOS `TdayCenteredSelectorDialog` /
  `TdayCenteredSelectorCard` built like the create/edit sheet's list selector, with the
  "No list" row (`listID: ""` clears the list; the backend maps blank to null).
- **Never offer a cross-silo target.** Scheduled tasks move between scheduled lists
  (`/api/list`); floaters move between floater lists (`/api/floaterList`). Crossing the
  two is promote/demote, a different operation. The screen's existing `lists` /
  `uiState.lists` is already the correct silo — use it, and filter out viewer lists
  exactly as `CreateTaskBottomSheet.kt:166` does.
- **Build each payload from the existing row and change only `listID`.** iOS
  `updateTodo` forces `normalizedDue = payload.due ?? todo.due ?? now+1h`, and web's
  `patchTodo` derives `dateChanged` / `rruleChanged` by comparing checksums — so web
  must pass `dateRangeChecksum = todo.due.toISOString()` and `rruleChecksum = todo.rrule`
  or the backend reads a spurious date/recurrence change.
- **Confirmation: only when the effective set's current list assignments are not all
  identical**, i.e. `effective.map { it.listId }.distinct().size > 1` (treat "no list"
  as its own value). In that case the original assignments are unrecoverable and the
  user must be told:
  - Title: `Move {{count}} tasks?`
  - Body: `These tasks come from different lists. Moving them can't be undone in one step — you'd have to put each one back by hand.`
  - Confirm: `Move {{count}} tasks`
  When every task comes from the same source list, moving is reversible by one more bulk
  move and needs no dialog.
- No toast on success. Invalidate/refresh per-list counts afterwards
  (web: `["listMetaData"]` plus the usual `["list"] / ["todo"] / ["todoTimeline"] / ["calendarTodo"]`).
- Moving into a shared list requires EDITOR; `TodoServiceImpl.update` returns
  400 `"list not found"` (field `listID`) otherwise. The target is the same for the
  whole batch, so this fails all-or-nothing — surface it as the §6 error toast.

---

## 5. Execution

- **Never abort the batch on the first failure.** Web: `Promise.allSettled`. Mobile:
  `runCatching` per item inside the loop, collecting outcomes.
- **Bounded concurrency ≤ 4** on web. Mobile may run sequentially — the pending-mutation
  queue is serial anyway.
- **One local cache write, not N, where the platform makes that cheap.** On Android
  every `TodoRepository` mutation runs a full `OfflineCacheManager.updateOfflineState`
  (Room load → transform → synchronised save) plus a widget-snapshot write, a
  `cacheDataVersion` bump and a widget refresh — and the ViewModel re-hydrates the whole
  list on every bump. On iOS every mutating repository method ends with its own
  `syncManager.syncCachedData(force: true, replayPendingMutations: true)`. Looping the
  existing single-item methods 100 times thrashes all of that. Preferred fix: add a
  batched repository method that applies the whole selection inside **one**
  `updateOfflineState` transform (enqueuing N `PendingMutationRecord`s in one write) and
  triggers **one** sync. Looping the existing methods is acceptable only for small
  selections and must not be the shipped shape for select-all.
- **ViewModels depend on repositories, never Retrofit/Room/URLSession.** Local Mode
  short-circuits inside the repositories, so bulk paths inherit it for free — but only
  if they go through the repository.

---

## 6. Partial failure

- Offline on Android/iOS is **not** a failure: the pending-mutation queue absorbs it and
  the connectivity toasts already announce offline/back-online separately. Only a real
  error after the repository has done its local work counts.
- On web there is no pending queue — a network failure **is** a failure.
- A 429 mid-batch counts as a failure for the remaining items. The cap exists to make
  that unlikely; report it like any other failure.
- After the batch, if `failed > 0`, show **exactly one** error toast:
  - `{{count}} tasks couldn't be deleted`
  - `{{count}} tasks couldn't be updated` (complete, priority, move)
  Route it through the platform's existing error funnel — Android
  `mutationFailureMessage(...)` → `snackbarManager.showError`, iOS
  `container.snackbarManager.show(..., kind: .error)`, web a destructive toast at the
  mutation. Never one toast per item. Never claim success.
- Then **invalidate / re-hydrate** so the UI resnaps to the server truth. Do not try to
  reconstruct which items succeeded from the responses — `update`, `prioritize` and
  `completeTodo` all return success even when zero rows matched (§1).
- Undo and partial failure can never interleave: undo only ever runs *before* commit,
  when nothing has been sent.

---

## 7. Per-surface obligations

Everything below is required for parity. Labels, counts, disabled states, empty states
and confirmation copy must match across all three (AGENTS.md Cross-Platform UX Rule).

### 7.1 Web (`tday-web`)

- Selection state in a provider (e.g. `src/providers/TaskSelectionProvider.tsx`) mounted
  beside `TodoMutationProvider` in `ListContainer.tsx`,
  `AllTasksTimelineContainer.tsx`. **Not** `NativeScheduledTaskHomeDashboard.tsx`: an
  earlier draft of this bullet listed it, but §8 puts the home feed's Today preview out
  of scope and §8 is the scope decision. (§8's stated reason — "its own row
  implementation on all three platforms" — is inaccurate for web specifically, where the
  preview renders the same `TodoItemCard` through `TodoGroup`; it is accurate for
  Android and iOS, so adding it on web alone would break parity in the other direction.)
- Entry: a Select button in the `trailingAction` cluster those containers already pass
  to `MobileSearchHeader`. Hidden when `readOnly` or the visible set is empty.
- Row: extend `TodoItemCard` (`src/components/todo/component/TodoItemContainer.tsx`),
  do not fork it. In selection mode render the square Radix
  `src/components/ui/checkbox.tsx` in place of the round `TodoCheckbox`, make the
  foreground row click toggle selection, and early-return out of `handleTouchStart`
  (swipe) and the hover cluster.
- Action bar: takes over the dock slot, with the same
  `fixed inset-x-0 bottom-[calc(18px+env(safe-area-inset-bottom))] z-40` metrics as
  `RootDock` / `TaskFloatingActionButton` so nothing overlaps.
- Cache pruning: on the list screen use `setQueriesData({queryKey: ["list"]})` **prefix**
  matching, not `["list", id]` — `ListTodoDto` carries `listID === null`, which is why
  `complete-list-todo.ts` and `delete-list-todo.ts` already do this. On the all-tasks
  screens prune `["todo"]` and `["todoTimeline"]`.
- Do not touch `["floaterList", id]` bulk-style: it holds `{list, floaters}`, and
  `delete-floater.ts` / `update-floater.ts` / `promote-floater.ts` already write it as a
  bare array. Fix that shape before extending bulk to floater lists (§8).
- Any new optimistic updater must be written `(old: T[] = []) => ...` or
  `(old) => old?.map(...)`. A non-optional array annotation hides `undefined` from tsc,
  throws in `onMutate`, and silently aborts the mutation.
- i18n: new keys in the `app` namespace of **all ten** `messages/*.json`
  (`tests/guardrails/i18n-parity.test.ts` requires identical flattened key sets).
  Use the i18next plural suffixes already used by `shareTaskCount_one/_few/_many/_other`.
- Do not restyle the Sonner Undo action — `tests/guardrails/toast-action-specificity.test.ts`
  pins it.
- Tests: assert the bulk commit actually calls the API mock N times (a passing status
  alone hides an aborted `onMutate` — see `create-todo-mutations.test.tsx`); a
  cold-cache test that the bulk updaters survive an empty `QueryClient`; and a test that
  bulk delete fires **no** request until the confirm is accepted **and** the toast settles.
- Verify: `npm run lint && npm run build && npm run test`.

### 7.2 Android (`android-compose`)

- Selection state in `TodoListScreen.kt` as
  `rememberSaveable(uiState.mode, uiState.listId) { mutableStateOf(emptySet<String>()) }`,
  hoisted exactly the way `openSwipeTaskId` already is. Not in the ViewModel — it
  re-hydrates `items` on every `cacheDataVersion` bump.
- Entry: a new `TodoTopBarAction` in the `topBarActions` list feeding `TdayHeroToolbar`.
  Do not take long-press on any mode that `TodoListMode.supportsTaskReschedule()`
  covers.
- Thread `selectionActive` / `selected` / `onToggleSelected` down through
  `TimelineTaskRow` → `AllTaskSwipeRow` / `TodayTaskSwipeRow` → `SwipeTaskRow`. Inside
  `SwipeTaskRow`: force `swipeRevealState.close()`, pass `dragEnabled = false`, and swap
  `CircularCheckToggleIcon`'s action for a selected/unselected circle using the
  `ic_lucide_circle` / `ic_lucide_circle_check_big` pair already in that composable.
- Selection top bar: reuse the `titleSuppressed`-style takeover on `TdayHeroToolbar`
  (count as the title, back = cancel, actions = Select all / Deselect all). Hide the FAB
  while selecting. Add `BackHandler(enabled = selectionActive)` **after** the existing
  exit-to-launcher handler — the back dispatcher runs the most recently added enabled
  callback, so "registered last" is what makes selection win (§2.5).
- ViewModel: four new methods taking `List<TodoItem>`, each making exactly **one**
  coordinator call for the whole batch, modelled on
  `MorningSweepViewModel.sweepAllToToday()`. Bind them in `TdayApp.TodoListRoute`
  alongside the existing `onComplete` / `onDelete` wiring.
- Destructive dialog: copy `ListDeleteConfirmationDialog`'s structure verbatim in style.
- Strings: `res/values/strings.xml` **plus all nine** sibling locales
  (`values-de/es/fr/it/ja/ms/pt/ru/zh`). The count strings should use `<plurals>` so
  "Delete 1 task?" reads correctly — the app has only one `<plurals>` today
  (`share_task_count`), so this is a justified extension of that convention rather than
  a new one.
- Icons: Lucide drawables only, never `Icons.*`.
- Tests: a pure `OfflineSyncState` transform test for whatever batch helper is added,
  in the shape of `TodoRepositoryDeleteCacheTest`.
- Verify: `cp /home/ohmz/StudioProjects/Tday/local.properties .` then
  `cd android-compose && ./gradlew :app:compileDebugKotlin` and `:app:testDebugUnitTest`.

### 7.3 iOS (`ios-swiftUI`)

- **Hand-roll the selection. Do not use `EditMode` + `List(selection:)`.** Every task
  row carries two UIKit gesture bridges — a `UIPanGestureRecognizer` on the row and a
  `UILongPressGestureRecognizer` on the enclosing `UIScrollView`, the same scroll view
  `EditMode` takes over — and the lists are heterogeneous (header rows, error-retry
  rows, drop placeholders, dividers, inline empty states, spacers). Untestable
  gesture-arbitration bugs are the worst possible trade on a build nobody can run.
- Cost of hand-rolling: `@State private var isSelecting = false` and
  `@State private var selectedTodoIDs: Set<String> = []`. Both gesture layers already
  expose the `enabled:` switch needed to stand down —
  `todoTrailingSwipeActions(enabled: !isSelecting && ...)` and
  `TodoInAppDragModifier(enabled: ... && !isSelecting)`.
- Entry: a `TimelineTopBarAction` in `heroTopBarActions`, gated on
  `!viewModel.items.isEmpty && !isViewerList`, exactly as `canOpenListSearch` gates the
  magnifier. While selecting, drive `TimelineTopBar` into a takeover the same way
  `searchActive` already takes the whole row.
- Rows: in `minimalTimelineRow`, replace the complete button with a checkbox and put a
  row-wide tap that toggles membership. Bottom bar: swap `floatingActionButtonDock`
  (already in `.safeAreaInset(edge: .bottom)`) for the four-action bar.
- ViewModel: `bulkComplete`, `bulkDelete`, `bulkSetPriority`, `bulkMove` in
  `TodoListViewModel.swift`. Batched staging in `TodoRepository.swift` —
  `StagedTodoDeletion` / `StagedFloaterDeletion` already hold arrays and
  `undoStagedTodo` / `undoStagedFloater` are already idempotent and array-shaped, so a
  combined staged snapshot restores unchanged.
- **Build this entirely inside existing Swift files. Do not add a new Swift file.**
  PR #88 (`feat/ios-testflight-pipeline`) is concurrently rewriting
  `ios-swiftUI/TdayApp.xcodeproj/project.pbxproj`; a new file needs four hand-added
  entries (`PBXBuildFile`, `PBXFileReference`, a `PBXGroup` child, and the app target's
  `PBXSourcesBuildPhase` `EE513C8DAF31C33B310B4E38`) and would collide. Everything this
  feature needs already lives in `TodoListScreen.swift`, `TodoListViewModel.swift`,
  `TodoRepository.swift`, `HapticManager.swift`, `Localizable.xcstrings` and
  `Assets.xcassets` — none of which require a pbxproj change. **XcodeGen is not
  installed and `ios-swiftUI/project.yml` must never be run**: it would clobber the
  hand-edited pbxproj and wipe `xcshareddata` including the shared scheme.
- Strings: `Resources/Localizable.xcstrings`, all nine non-English locales. `L(key)`
  falls back to the key, so English works untranslated but every other locale silently
  degrades to English — that is a parity failure, not an acceptable default.
- **iOS cannot be compiled, run, or tested on the maintainer's Linux box** (no
  `xcodebuild`, `swift`, `xcrun`, or Xcode) and there is no iOS job in CI. Every iOS
  claim in the PR must be marked unverified. Never imply an iOS build passed.

---

## 8. Scope of the first cut

**In:** the main task-list screen on each platform, across every list mode
(today / overdue / scheduled / all / priority / floater / list). That is
`TodoListScreen.kt`, `TodoListScreen.swift`, and web's `ListContainer` +
`AllTasksTimelineContainer` family.

**Out, deliberately, and to be said out loud in the PR so the parity check reads as a
decision rather than an oversight:**

- The scheduled-task home feed's Today preview card (its own row implementation on all
  three platforms).
- The calendar's day rows.
- The completed-history screen.
- Floater **lists** on web, until the `["floaterList", id]` cache-shape inconsistency is
  fixed (§7.1). Floater **tasks** inside the floater list mode of the main screen are in
  scope on mobile.
- The **floater root feed** (Floater/Anytime home, `mode = .floater` with no scoped
  list) on Android and iOS. Ruled out at integration, after both mobile surfaces
  independently landed on the same exclusion. That screen is a feed of *lists*, not of
  tasks, and it draws `RootFeedHeroHeader` — shared with the scheduled home feed —
  instead of the hero toolbar / `TimelineTopBar` that hosts the Select button
  everywhere else. Giving it selection means editing a component the scheduled home
  feed also renders, which is exactly the blast radius this section excluded the home
  feed's Today card for. Floater tasks stay covered inside floater **list detail**.
  In scope on mobile is therefore precisely "wherever the hero toolbar draws its action
  cluster": the five timeline scopes, list detail, and floater-list detail.

---

## 9. Copy (canonical English)

All three surfaces use these strings. Translate them; do not reword them.

| Meaning                    | English                                                                                        |
|----------------------------|------------------------------------------------------------------------------------------------|
| Enter selection            | `Select`                                                                                        |
| Count                      | `{{count}} selected`                                                                            |
| Count at the cap           | `{{count}} selected — the most one action can cover`                                            |
| Select all / clear         | `Select all` / `Deselect all`                                                                   |
| Cancel                     | `Cancel`                                                                                        |
| Actions                    | `Complete` · `Priority` · `Move` · `Delete`                                                     |
| Recurring skipped          | `Applies to {{count}} of {{total}} — repeating tasks are skipped.`                               |
| Complete toast             | one `Task completed` / other `{{count}} tasks completed`                                        |
| Delete dialog title        | one `Delete this task?` / other `Delete {{count}} tasks?`                                       |
| Delete dialog body         | `This removes them from every list, along with their completed history. You'll have a few seconds to undo.` |
| Delete confirm button      | one `Delete task` / other `Delete {{count}} tasks`                                              |
| Delete toast               | one `Task deleted` / other `{{count}} tasks deleted`                                            |
| Move dialog title          | `Move {{count}} tasks?`                                                                         |
| Move dialog body           | `These tasks come from different lists. Moving them can't be undone in one step — you'd have to put each one back by hand.` |
| Move confirm button        | `Move {{count}} tasks`                                                                          |
| Partial failure (delete)   | `{{count}} tasks couldn't be deleted`                                                           |
| Partial failure (other)    | `{{count}} tasks couldn't be updated`                                                           |
| Undo action                | reuse the existing `undo` / `action_undo` / `L("Undo")` string                                   |

Suggested key names, to keep the three from drifting:

- web `app` namespace: `bulkSelect`, `bulkSelected`, `bulkSelectedCapped`, `bulkSelectAll`,
  `bulkDeselectAll`, `bulkAppliesTo`, `bulkComplete`, `bulkPriority`, `bulkMove`,
  `bulkDelete`, `tasksCompleted_one|_other`, `bulkDeleteTitle_one|_other`,
  `bulkDeleteBody`, `bulkDeleteConfirm_one|_other`, `tasksDeleted_one|_other`,
  `bulkMoveTitle`, `bulkMoveBody`, `bulkMoveConfirm`, `bulkDeleteFailed`, `bulkUpdateFailed`.
- Android `strings.xml`: `bulk_select`, `bulk_selected`, `bulk_selected_capped`,
  `bulk_select_all`, `bulk_deselect_all`, `bulk_applies_to`, `bulk_action_complete`,
  `bulk_action_priority`, `bulk_action_move`, `bulk_action_delete`, plus `<plurals>`
  `bulk_tasks_completed`, `bulk_delete_title`, `bulk_delete_confirm`,
  `bulk_tasks_deleted`.
- iOS: the English string itself is the key, per the existing `L(...)` convention.

---

## 10. Telemetry

Breadcrumbs only, via the platform helper (`TdayTelemetry` / `sentry.ts`):

`task.bulk_complete`, `task.bulk_delete`, `task.bulk_priority`, `task.bulk_move`

Fields: the existing structural task data (mode / screen / scoped-list booleans) plus
`count` and, when non-zero, `failed`. **Never** task titles, descriptions, list names or
raw ids. `sweep.all` already sends `"count"` — follow it.

---

## 11. Guide content

Done on the foundation branch; surface implementers must not redo it.

- `GuideTopicIds.BULK_ACTIONS = "bulk-actions"`.
- `GuideCatalog.kt`: section `ORGANIZING`, icon `circle-check-big`, platforms
  WEB + ANDROID + IOS, badge `PRO_TIP`, `sinceVersion = "0.7.4"` (v0.7.3 is already
  tagged, so the next merge to master auto-bumps the patch — if the release turns out
  to be a minor instead, this literal moves with it).
- `guide.topics.bulk-actions.*` in all ten `tday-web/messages/*.json`.
- No `deepLink`, therefore no `shared/guide-content/routes.json` change: the mode is
  entered from a button on screens the guide already reaches, and every new route in
  that whitelist is a new thing that can rot.
- `circle-check-big` added to the web `GuideIcon` map; Android drawable and iOS imageset
  already exist.
- Regenerated artifacts committed; `./gradlew :shared:verifyGuideContent` passes.

If a surface changes user-visible behaviour beyond what this note describes, that PR
owns its own guide update.

---

## 12. Follow-ups this note deliberately does not take

1. **Backend guard for the recurring-complete trap** (§1). A client must not depend on
   a server fix self-hosters have not deployed; the client-side rule in §4.1 is the
   real mitigation.
2. **`["floaterList", id]` cache-shape inconsistency on web** (§7.1) — blocks bulk on
   web floater lists.
3. **iOS `TodoRepository.setPriority` never updates `nextState.floaters`** (§4.4).
4. **Batch `ids` on `DELETE /api/todo`** — only if the cap starts hurting (§1).
5. **"No list" only clears if the client sends `""`, never `null`.** `TodoRoutes` does
   `body.listID?.let { fields["listID"] = it.takeIf { it.isNotBlank() } }`, so a null
   `listID` never reaches `fields` at all and `TodoService.update`'s
   `if (fields.containsKey("listID"))` guard leaves the assignment untouched. The row
   clears optimistically and the next refetch puts the old list straight back. Only a
   blank string reaches `fields` (as null) and actually clears. Status per surface:
   - **Android**: correct for free. `SyncManager`'s `UPDATE_TODO` replay ends
     `resolvedListId ?: if (!remoteTodo?.listId.isNullOrBlank()) "" else null`, which
     turns a null mutation `listId` into `""` whenever the remote row still has a list.
   - **iOS**: correct by storing `listId: ""` in the pending mutation explicitly.
   - **Web**: was **wrong** and fixed during integration — web has no replay layer, so
     `patchTodo` put `listID: null` straight on the wire. Now `listID ?? ""`, pinned by
     *sends "" and not null when moving to No list* in `bulk-todo-actions.test.tsx`.

   Still open: iOS's single-task **edit sheet** clears a list through the same
   `.updateTodo` nil path and has no remote-snapshot fallback, so it retains the bug.
6. **`MutationKind.SET_PRIORITY` replay is todo-only on Android**, the exact mirror of
   the iOS gap in item 3. `SyncManager` routes it to `patchTodoByBody` /
   `prioritizeTodoByBody`, so a floater id sent down that path would patch the wrong
   table; bulk floater priority therefore goes through a whole-record `UPDATE_FLOATER`
   rebuilt from the row. §4.4's Android bullet ("using it is the lighter path") holds
   for scheduled tasks only. Related trap, pinned by a test on the Android side: the
   `UPDATE_FLOATER` replay clears any field the mutation leaves null, so a
   priority-only mutation that omitted title/notes would wipe the notes — the cached
   transform must always carry the whole row.

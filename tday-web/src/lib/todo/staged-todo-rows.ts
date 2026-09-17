import type { QueryClient } from "@tanstack/react-query";

/**
 * The rows a delayed-commit action has staged away, and the promise that no read
 * brings one back until it commits or is undone.
 *
 * Completing or deleting a task is delayed-commit (see
 * `features/todayTodos/query/complete-todo.ts`): the row is pruned out of the
 * active-task caches the instant it is tapped, and the request itself is only
 * sent when the undo toast closes five seconds later. For that whole window the
 * server still reports the row as live, and every reader in this app will
 * happily write it straight back into the cache it was pruned from — the query
 * functions, `src/lib/realtime.tsx`'s invalidator (every completion emits a
 * `todo` event back to the actor who caused it, so N completions fire N
 * refetches across their own commit window), a window-focus refetch, a sibling
 * mutation's `onSettled`. The row the user just ticked leaves, comes back, and
 * leaves again. That is the bug this module exists to close.
 *
 * Pruning alone can never hold it: a prune is a one-time write to two or three
 * cache entries, and the window is five seconds of arbitrary refetching. What
 * the window needs is an invariant — *a row with an action in flight is not in
 * any todo-list cache* — enforced wherever cache data lands, rather than at each
 * site that happens to write it today.
 *
 * So: the ids in flight, plus one `QueryCache` subscription per client that
 * re-applies the prune to every row-list entry the cache reports as changed.
 * The subscription is the same cache-boundary shape as `src/lib/realtime.tsx`'s
 * invalidator, for the same reason it is the right place there: a query
 * function this module has never seen, a key a screen invents later and a
 * `setQueryData` in a test all pass through it alike. Nothing has to remember
 * to consult it.
 *
 * It is deliberately NOT a second source of truth for the completion. Nothing
 * here says a row is completed; it says the row is *away*. Which is why the
 * apply step is a removal and never a `completed: true` flag: a row that comes
 * back — on undo, or because the request failed — has to come back exactly as it
 * was, and a flag here would bring it back ticked (and, on iOS, struck, since
 * that client's strike condition includes `todo.completed`).
 *
 * Keyed per `QueryClient` and not per module: the ids mean nothing to a client
 * that does not hold the caches they were pruned out of, and a set that outlives
 * the client it describes is a set nothing can ever clear.
 */

/** The ids currently staged away, per client. Absent until the first stage. */
const stagedIdsByClient = new WeakMap<QueryClient, Set<string>>();

/**
 * Roots of every query key whose cached value can hold a todo or floater row.
 *
 * The rule is "a read path that would refill a row from the server", not "the
 * keys a prune site happens to write": the guard exists for every reader in the
 * app, and a key this module does not know about is one whose refetch smuggles
 * the row back in. So the set is the row-list members of the `todo` and
 * `floater` event families `src/lib/realtime.tsx` invalidates — `["todo"]`,
 * `["todoTimeline"]`, `["overdueTodo"]`, `["calendarTodo"]`, `["list", …]`,
 * `["floater"]`, `["floaterList", …]` — minus the *completed* history caches
 * (`["completedTodo"]`, `["completedFloater"]`), where a completed row belongs,
 * and minus the metadata maps (`["listMetaData"]`, `["floaterListMetaData"]`).
 *
 * `["overdueTodo"]` is in the realtime todo-family set but has no reader today
 * (the overdue screen reads `["todoTimeline"]`) and no prune site writes it, so
 * membership there is inert; it is listed so the guard cannot be the reason a
 * key the invalidator treats as a row list goes undefended.
 *
 * The first element is what is matched, never a prefix — `["list"]` and
 * `["listMetaData"]` are different roots.
 */
export const ROW_LIST_KEY_ROOTS = [
  "todo",
  "todoTimeline",
  "overdueTodo",
  "calendarTodo",
  "list",
  "floater",
  "floaterList",
] as const;

const ROW_LIST_KEY_ROOT_SET: ReadonlySet<string> = new Set(ROW_LIST_KEY_ROOTS);

/** The cache id of a row, or null for anything that is not shaped like one. */
function rowIdOf(row: unknown): string | null {
  if (typeof row !== "object" || row === null) return null;
  const id = (row as { id?: unknown }).id;
  return typeof id === "string" ? id : null;
}

/** Whether this value is one of the rows the guard is holding out of the caches. */
function isStagedRow(row: unknown, staged: ReadonlySet<string>): boolean {
  const id = rowIdOf(row);
  return id !== null && staged.has(id);
}

/**
 * A row list with every staged row removed, by identity when nothing matched.
 *
 * The identity return is what makes the subscription terminate: the guard's own
 * `setQueryData` dispatches another `updated`, the guard sees a list with no
 * staged row left in it, and stops rather than looping. A value that is not a
 * row at all (a cache seeded with something else entirely) is not ours to drop,
 * so a row with no string `id` is kept.
 */
function withoutStagedRowsFromRows<T>(rows: T[], staged: ReadonlySet<string>): T[] {
  const kept = rows.filter((row) => !isStagedRow(row, staged));
  return kept.length === rows.length ? rows : kept;
}

/**
 * `data` with every staged row removed, whatever shape that root's cache holds.
 *
 * Most roots hold a bare array of rows. `["floaterList", id]` is the exception:
 * its value is `{ list, floaters }` (see `useFloaterList`), the same rows one
 * field down, so it needs its own branch rather than being skipped as
 * "not shaped like a list".
 */
function withoutStagedRows<T>(data: T, staged: ReadonlySet<string>): T {
  if (staged.size === 0) return data;

  if (Array.isArray(data)) {
    const kept = withoutStagedRowsFromRows(data, staged);
    return (kept === data ? data : kept) as T;
  }

  const holder = data as { floaters?: unknown } | null;
  if (
    typeof holder === "object" &&
    holder !== null &&
    Array.isArray(holder.floaters)
  ) {
    const kept = withoutStagedRowsFromRows(holder.floaters, staged);
    if (kept === holder.floaters) return data;
    return { ...holder, floaters: kept } as T;
  }

  return data;
}

function installGuard(queryClient: QueryClient, staged: Set<string>): void {
  queryClient.getQueryCache().subscribe((event) => {
    if (event.type !== "updated" && event.type !== "added") return;
    const { query } = event;
    if (!ROW_LIST_KEY_ROOT_SET.has(String(query.queryKey[0]))) return;
    const current = query.state.data;
    const next = withoutStagedRows(current, staged);
    if (next === current) return;
    // `query.state` already holds the new data by the time the cache notifies, so
    // this is a real correction of a value a refetch just wrote, not a racing
    // write. `manual: true` keeps it out of the fetch lifecycle.
    queryClient.setQueryData(query.queryKey, next);
  });
}

/**
 * Marks rows as staged away: pruned now, request deferred, and — from this call
 * until the matching `releaseTodoRows` — impossible for a refetch to put back.
 *
 * Called by every delayed-commit prune site, at the same moment as the prune.
 * The undo window is not affected: this says nothing about when the request
 * fires, only about what the read path is allowed to show while it has not.
 */
export function stageTodoRows(queryClient: QueryClient, ids: Iterable<string>): void {
  let staged = stagedIdsByClient.get(queryClient);
  if (!staged) {
    staged = new Set<string>();
    stagedIdsByClient.set(queryClient, staged);
    installGuard(queryClient, staged);
  }
  for (const id of ids) staged.add(id);
}

/**
 * Releases rows: they are the read path's business again, so the next refetch
 * brings them back.
 *
 * Both ends of the window owe this call. Undo, because the server still has the
 * row and the invalidate that follows is *meant* to restore it. And commit,
 * because that is the moment the server is told — releasing before the request
 * settles would let a sibling's refetch flash the row back in the gap between
 * the tap and the PATCH landing, and never releasing would strand a row whose
 * request failed.
 */
export function releaseTodoRows(queryClient: QueryClient, ids: Iterable<string>): void {
  const staged = stagedIdsByClient.get(queryClient);
  if (!staged) return;
  for (const id of ids) staged.delete(id);
}

/**
 * Tells every row-list cache to refetch, so a row the guard stripped out comes
 * back once its id is released.
 *
 * The counterpart to `stageTodoRows`, and it has to live here rather than at
 * each call site for the reason the two can drift apart: the guard holds a row
 * out of *every* root, not only the one the calling mutation pruned. A
 * completion on the Today screen that stripped a `["list", id]` cache (the
 * realtime `todo` event invalidates `["list"]` too) owes that cache a refetch
 * when Undo says the row is real after all — invalidating only `["todo"]` and
 * `["todoTimeline"]` restores the screens the mutation itself touched and leaves
 * the others one row short until something unrelated refetches them. Deriving
 * the set from `ROW_LIST_KEY_ROOTS` is what keeps the two ends of the window
 * describing the same caches.
 */
export function restoreTodoRowCaches(queryClient: QueryClient): void {
  for (const root of ROW_LIST_KEY_ROOTS) {
    void queryClient.invalidateQueries({ queryKey: [root] });
  }
}

/**
 * Both halves of the Undo end of the window, in the order they have to run.
 *
 * Release first: the invalidations below are the refetches that are *meant* to
 * put the row back, and they must not be filtered by the guard they are undoing.
 */
export function releaseAndRestoreTodoRows(
  queryClient: QueryClient,
  ids: Iterable<string>,
): void {
  releaseTodoRows(queryClient, ids);
  restoreTodoRowCaches(queryClient);
}

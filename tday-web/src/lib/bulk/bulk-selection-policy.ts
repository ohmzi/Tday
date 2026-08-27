/**
 * Web mirror of `shared/src/commonMain/kotlin/com/ohmz/tday/shared/bulk/BulkSelectionPolicy.kt`.
 *
 * The rules that have to be identical on web, Android and iOS are numbers, not
 * prose, so they live in one place per platform and the Kotlin file is the
 * source of truth. Android consumes that file directly; web and iOS restate the
 * literals with a pointer back to it, the same arrangement the rest of the
 * shared contract already lives with. See `docs/design/bulk-selection.md`.
 *
 * If the Kotlin values change, change these too — `bulk-selection-policy.test.ts`
 * pins the safety invariants but cannot see across the language boundary.
 */

/** The four actions a multi-select can apply. Nothing else belongs in the bar. */
export type BulkAction = "complete" | "delete" | "priority" | "move";

/**
 * Hardest cap on one bulk action, because every action is a fan-out of N
 * single-item requests. `api_global` allows 180 requests per 60s per user, and
 * each mutation additionally fans one realtime event to every collaborator, one
 * webhook delivery per subscription and one push poke per device — none of it
 * coalesced. Staying well under the limit is what keeps a large selection from
 * becoming a *partially applied* destructive action halfway through.
 */
export const BULK_MAX_SELECTION = 100;

/** Parallel in-flight requests per bulk action. */
export const BULK_MAX_CONCURRENCY = 4;

/**
 * A recurring occurrence may be selected and bulk-*completed* as the occurrence
 * it represents, but is never eligible for bulk delete, priority or move: those
 * three have no per-occurrence route and would silently act on the whole series
 * (`DELETE /api/todo` removes every future occurrence; priority and move go
 * through the full-record `PATCH /api/todo`). Morning Sweep already excludes
 * recurring tasks from its batch for the same reason.
 *
 * "As the occurrence it represents" is load-bearing — see
 * `effectiveBulkSelection`, which also requires the row to *have* an occurrence.
 */
export function bulkActionAppliesToRecurring(action: BulkAction): boolean {
  return action === "complete";
}

/**
 * Bulk delete always asks first — a confirmation stating the exact count, on top
 * of (not instead of) the existing delayed-commit undo toast.
 *
 * Bulk move asks only when the selection spans more than one source list,
 * because that is the case the user cannot put back: an edit has no undo, and
 * the original per-task assignments are gone. Complete is undoable and priority
 * is one tap to reverse, so neither asks.
 */
export function bulkActionRequiresConfirmation(
  action: BulkAction,
  distinctSourceLists: number,
): boolean {
  if (action === "delete") return true;
  if (action === "move") return distinctSourceLists > 1;
  return false;
}

/** True once the selection has reached the cap and further taps must be refused. */
export function isBulkSelectionAtCap(selectedCount: number): boolean {
  return selectedCount >= BULK_MAX_SELECTION;
}

/**
 * The rows `action` will actually touch, given the whole selection.
 * `isRecurring` is `Boolean(todo.rrule)`; `hasInstanceDate` is
 * `Boolean(todo.instanceDate)`. Select-all is capped in display order from the
 * top so the outcome is deterministic rather than whichever hundred the set
 * happened to hold.
 *
 * "complete" additionally drops a recurring row with no `instanceDate`.
 * `TodoService.completeTodo` branches `if (rrule == null) {...} else if
 * (instanceDate != null) {...}`, so completing a recurring row without one takes
 * neither branch: it writes a `CompletedTodos` history row, marks nothing
 * complete, and the task returns on the next refetch while the Completed list —
 * and the stats derived from it — keeps an entry for something that never
 * happened. `hasInstanceDate` defaults to "no occurrence" so an un-thought-about
 * caller gets the safe answer.
 */
export function effectiveBulkSelection<T>(
  action: BulkAction,
  selection: readonly T[],
  isRecurring: (row: T) => boolean,
  hasInstanceDate: (row: T) => boolean = () => false,
): T[] {
  const eligible = bulkActionAppliesToRecurring(action)
    ? selection.filter((row) => !isRecurring(row) || hasInstanceDate(row))
    : selection.filter((row) => !isRecurring(row));
  return eligible.slice(0, BULK_MAX_SELECTION);
}

/**
 * How many different lists a selection came from, counting "no list" as its own
 * value — the input to `bulkActionRequiresConfirmation` for a move.
 */
export function distinctSourceListCount(
  sources: readonly (string | null | undefined)[],
): number {
  return new Set(sources.map((listID) => listID ?? "")).size;
}

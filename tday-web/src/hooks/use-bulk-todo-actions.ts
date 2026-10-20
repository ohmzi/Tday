import { useCallback, useMemo } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { useUndoableDelete } from "@/hooks/use-undoable-delete";
import { canonicalTodoId } from "@/lib/todo/todo-id";
import { patchTodo } from "@/lib/todo/patch-todo";
import {
  pruneTodoRowCaches,
  releaseTodoRows,
  restoreTodoRowCaches,
  settleTodoRows,
  stageTodoRows,
} from "@/lib/todo/staged-todo-rows";
import {
  markCelebrationCancelled,
  markTaskCompleted,
  markTaskDeletedLocally,
} from "@/lib/task-completion-signal";
import { addDiagnosticBreadcrumb } from "@/lib/observability/sentry";
import {
  runBulkFanOut,
  type BulkFanOutResult,
} from "@/lib/bulk/run-bulk-fan-out";
import type { Priority } from "@/components/todo/component/TodoForm/labels";
import type { TodoItemType } from "@/types";

/**
 * The four bulk actions, each a fan-out over the single-item endpoints that
 * already exist. No batch route, no new Local Mode case, no deploy coupling —
 * see `docs/design/bulk-selection.md` §1 for why, and §12 for the trigger that
 * would make a batch `DELETE /api/todo` worth taking.
 *
 * Every action here takes the **effective** rows (recurring already filtered out
 * where the action cannot address an occurrence, cap already applied) and the
 * full row objects, never bare ids: a request needs `canonicalTodoId`, the
 * occurrence's `instanceDate`, and for a move the row's own checksums.
 *
 * Toast policy, unchanged from single-task actions: complete and delete get one
 * undoable toast for the whole batch; priority and move are edits and succeed
 * silently. Failures always surface as exactly one destructive toast (§6).
 */
export function useBulkTodoActions({
  scopeListId,
}: {
  /**
   * The list this screen is scoped to, when it is a list screen. Rows from
   * `/api/list/:id` carry `listID === null` — `ListTodoDto` has no such field —
   * so the screen's own id is the only thing that knows where they came from.
   */
  scopeListId?: string | null;
} = {}) {
  const queryClient = useQueryClient();
  const { toast } = useToast();
  const { t } = useTranslation("app");
  const showUndoableDelete = useUndoableDelete();

  const cancelActiveTodoQueries = useCallback(() => {
    void queryClient.cancelQueries({ queryKey: ["todo"] });
    void queryClient.cancelQueries({ queryKey: ["todoTimeline"] });
    // Prefix match, the same way complete-list-todo / delete-list-todo do it:
    // list rows carry no listID, so ["list", id] cannot be derived from a row.
    void queryClient.cancelQueries({ queryKey: ["list"] });
  }, [queryClient]);

  /**
   * Drop the staged rows out of every cache that shows them.
   *
   * One write per root, derived from the same `ROW_LIST_KEY_ROOTS` set the claim
   * defends and `restoreTodoRowCaches` refetches, so the three ends of the window
   * cannot disagree about which caches hold a row. It used to be spelled out
   * here as `["todo"]` + `["todoTimeline"]` + a `["list"]` prefix, which is three
   * roots of the seven the claim covers: a batch completed on the calendar left
   * its rows in `["calendarTodo"]` for the whole window and only lost them when
   * something else happened to write that cache.
   *
   * Written `(old) => old?.filter(...)`-shaped inside
   * `@/lib/todo/staged-todo-rows`, never with a non-optional array annotation:
   * that hides `undefined` from tsc, throws on a cold cache, and — inside a
   * mutation's `onMutate` — aborts the mutation silently, which looks exactly
   * like a network failure. A cache that has never been filled is left alone.
   */
  const pruneStagedRows = useCallback(
    (rowIds: ReadonlySet<string>) => {
      pruneTodoRowCaches(queryClient, rowIds);
    },
    [queryClient],
  );

  /**
   * Undo path: nothing was sent, so the server still has every row.
   *
   * The cancel is stamped HERE and not left to the screens' own arrival
   * backstop, for the reason `useArrivalCancel` states: these rows come back
   * through an invalidate and a refetch, and a celebration must not go on
   * flying for the length of a network round trip after the user has said they
   * did not mean it. The backstop still fires when the rows land; this is the
   * half that does not wait. The same stamp the single-row complete mutations
   * write from their own undo closures — a bulk complete opens the window with
   * `markTaskCompleted` exactly as they do (below), so it owes the window an
   * ending exactly as they do.
   *
   * Shared with the bulk DELETE's undo, which is correct rather than incidental:
   * a delete never opens a celebration (`markTaskDeletedLocally` is what keeps
   * the emptying quiet), so the stamp changes no answer there — and if some
   * later path ever let one open, rows arriving back would still be the end of
   * it.
   *
   * The refetch set comes from `restoreTodoRowCaches` rather than being spelled
   * out here, so it cannot drift from the set the guard holds rows out of — the
   * guard claims a batch's ids in every row-list cache, not only the three this
   * hook prunes.
   */
  const restoreStagedRows = useCallback(() => {
    markCelebrationCancelled();
    restoreTodoRowCaches(queryClient);
  }, [queryClient]);

  /**
   * Re-snap every view to the server after a batch settles. Deliberately not a
   * reconstruction of which rows succeeded: `update`, `prioritize` and
   * `completeTodo` all return success even when zero rows matched, so the only
   * honest source of truth is a refetch (§6).
   */
  const refreshTodoViews = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ["todo"] });
    void queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
    void queryClient.invalidateQueries({ queryKey: ["list"] });
    void queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
    void queryClient.invalidateQueries({ queryKey: ["overdueTodo"] });
    void queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
    // Per-list task counts in the sidebar / dashboard tiles.
    void queryClient.invalidateQueries({ queryKey: ["listMetaData"] });
  }, [queryClient]);

  const reportFailures = useCallback(
    (
      operation: string,
      { total, failed }: BulkFanOutResult,
      messageKey: "bulkDeleteFailed" | "bulkUpdateFailed",
    ) => {
      if (failed === 0) return;
      // Counts only, never a title, a list name or an id.
      addDiagnosticBreadcrumb(operation, { count: total, failed });
      toast({
        description: t(messageKey, { count: failed }),
        variant: "destructive",
      });
    },
    [t, toast],
  );

  const completeSelected = useCallback(
    (rows: readonly TodoItemType[]) => {
      if (rows.length === 0) return;
      addDiagnosticBreadcrumb("task.bulk_complete", {
        count: rows.length,
        scoped_list: Boolean(scopeListId),
      });
      // The empty state that follows the last row leaving reads this to tell a
      // list the user just finished from one that was never filled.
      markTaskCompleted();
      const rowIds = new Set(rows.map((row) => row.id));
      // Claim first, then cancel, then prune — the same order the single-row
      // complete takes and for the same reason: the claim is what the cache
      // boundary consults, so a refetch already in flight cannot slip in behind
      // the prune. Pruning first leaves a gap the guard is not yet watching.
      stageTodoRows(queryClient, rowIds);
      cancelActiveTodoQueries();
      pruneStagedRows(rowIds);

      // ONE toast for the batch. N toasts would mean N independent commit
      // timers with only the last one visible, so Undo would reach exactly one
      // of them.
      showUndoableDelete({
        message: t("tasksCompleted", { count: rows.length }),
        commit: () => {
          void runBulkFanOut(rows, (row) =>
            api.PATCH({
              url: "/api/todo/complete",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                id: canonicalTodoId(row.id),
                // Never omit this for a recurring row. `completeTodo` branches
                // `if (rrule == null) ... else if (instanceDate != null) ...`,
                // so a recurring complete without one writes a history row,
                // marks nothing complete, and leaves the task on screen.
                // Date, not epoch millis: the backend parses it as ISO-8601.
                instanceDate: row.rrule ? (row.instanceDate ?? null) : null,
              }),
            }),
          ).then((result) => {
            reportFailures(
              "task.bulk_complete_failed",
              result,
              "bulkUpdateFailed",
            );
            // Settled, not merely released — and settled as a whole, because the
            // batch is one window with one timer. A read the window started was
            // answered with the pre-commit truth, so it has to be dropped before
            // the ids come off the guard, or it writes every staged row back at
            // once: the reported "come back for a second, then leave again". See
            // `settleTodoRows`, which cancels those reads first, and the refresh
            // it authorises after.
            //
            // Settled even when rows failed: the batch is over, so whatever the
            // server did not accept is pending again and the refetch below has to
            // be allowed to say so.
            void settleTodoRows(queryClient, rowIds).then(refreshTodoViews);
          });
        },
        undo: () => {
          releaseTodoRows(queryClient, rowIds);
          restoreStagedRows();
        },
      });
    },
    [
      cancelActiveTodoQueries,
      pruneStagedRows,
      queryClient,
      refreshTodoViews,
      reportFailures,
      restoreStagedRows,
      scopeListId,
      showUndoableDelete,
      t,
    ],
  );

  /**
   * Layer 2 of the delete guard. Layer 1 is the confirmation dialog, which the
   * caller must have accepted before this is reached — nothing here re-asks, so
   * do not call it from anywhere but the confirmed path.
   */
  const deleteSelected = useCallback(
    (rows: readonly TodoItemType[]) => {
      if (rows.length === 0) return;
      addDiagnosticBreadcrumb("task.bulk_delete", {
        count: rows.length,
        scoped_list: Boolean(scopeListId),
      });
      // The empty state that follows the last rows leaving reads this to tell
      // a list a batch was deleted out of from one that was just finished.
      markTaskDeletedLocally();
      cancelActiveTodoQueries();
      const rowIds = new Set(rows.map((row) => row.id));
      pruneStagedRows(rowIds);
      // Same claim as the batch complete above: the delete has the identical
      // resurrection hole, so it takes the identical guard.
      stageTodoRows(queryClient, rowIds);

      showUndoableDelete({
        message: t("tasksDeleted", { count: rows.length }),
        commit: () => {
          void runBulkFanOut(rows, (row) =>
            api.DELETE({
              url: "/api/todo",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ id: canonicalTodoId(row.id) }),
            }),
          ).then((result) => {
            reportFailures(
              "task.bulk_delete_failed",
              result,
              "bulkDeleteFailed",
            );
            // The delete window has the identical commit hazard the complete one
            // has — see `settleTodoRows` — so it takes the identical settle.
            void settleTodoRows(queryClient, rowIds).then(refreshTodoViews);
          });
        },
        undo: () => {
          releaseTodoRows(queryClient, rowIds);
          restoreStagedRows();
        },
      });
    },
    [
      cancelActiveTodoQueries,
      pruneStagedRows,
      queryClient,
      refreshTodoViews,
      reportFailures,
      restoreStagedRows,
      scopeListId,
      showUndoableDelete,
      t,
    ],
  );

  const setPriorityForSelected = useCallback(
    (rows: readonly TodoItemType[], level: Priority) => {
      if (rows.length === 0) return;
      addDiagnosticBreadcrumb("task.bulk_priority", {
        count: rows.length,
        scoped_list: Boolean(scopeListId),
      });
      const rowIds = new Set(rows.map((row) => row.id));
      cancelActiveTodoQueries();

      const applyLevel = (old?: TodoItemType[]) =>
        old?.map((todo) =>
          rowIds.has(todo.id) ? { ...todo, priority: level } : todo,
        );
      queryClient.setQueryData<TodoItemType[]>(["todo"], applyLevel);
      queryClient.setQueryData<TodoItemType[]>(["todoTimeline"], applyLevel);
      queryClient.setQueriesData<TodoItemType[]>({ queryKey: ["list"] }, applyLevel);

      // The dedicated prioritize route rather than a whole-record PATCH, as the
      // design note asks. Driven from here rather than by looping
      // `usePrioritizeTodo` N times: that hook snapshots, invalidates and
      // toasts per call, which would give N error toasts and no way to bound
      // concurrency or count failures.
      void runBulkFanOut(rows, (row) =>
        api.PATCH({
          url: "/api/todo/prioritize",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            id: canonicalTodoId(row.id),
            priority: level,
            // Recurring rows are never in a bulk priority set (§4.1), so there
            // is no occurrence for this to scope to.
            instanceDate: null,
          }),
        }),
      ).then((result) => {
        reportFailures("task.bulk_priority_failed", result, "bulkUpdateFailed");
        refreshTodoViews();
      });
    },
    [
      cancelActiveTodoQueries,
      queryClient,
      refreshTodoViews,
      reportFailures,
      scopeListId,
    ],
  );

  const moveSelectedToList = useCallback(
    (rows: readonly TodoItemType[], listID: string | null) => {
      if (rows.length === 0) return;
      addDiagnosticBreadcrumb("task.bulk_move", {
        count: rows.length,
        scoped_list: Boolean(scopeListId),
        to_list: Boolean(listID),
      });
      const rowIds = new Set(rows.map((row) => row.id));
      // A row whose source is the destination is staying put; everything else
      // leaves whichever list screen is showing it.
      const leavingIds = new Set(
        rows
          .filter((row) => (row.listID ?? scopeListId ?? null) !== listID)
          .map((row) => row.id),
      );
      // A move can empty the source list screen exactly like a delete does —
      // same marker, so `useCelebrateEmptyTransition` doesn't read a batch
      // moved out of the last-viewed list as a payoff.
      if (leavingIds.size > 0) markTaskDeletedLocally();
      cancelActiveTodoQueries();

      const applyList = (old?: TodoItemType[]) =>
        old?.map((todo) => (rowIds.has(todo.id) ? { ...todo, listID } : todo));
      queryClient.setQueryData<TodoItemType[]>(["todo"], applyList);
      queryClient.setQueryData<TodoItemType[]>(["todoTimeline"], applyList);
      queryClient.setQueriesData<TodoItemType[]>({ queryKey: ["list"] }, (old) =>
        old?.flatMap((todo) => {
          if (leavingIds.has(todo.id)) return [];
          return rowIds.has(todo.id) ? [{ ...todo, listID }] : [todo];
        }),
      );

      void runBulkFanOut(rows, (row) =>
        patchTodo(
          {
            ...row,
            // `patchTodo` derives dateChanged / rruleChanged by comparing these
            // to the row's current values, so a move that leaves them alone has
            // to hand back what it was given — otherwise the backend reads a
            // date or recurrence change that never happened.
            dateRangeChecksum: row.due.toISOString(),
            rruleChecksum: row.rrule,
          },
          {
            // "No list" must go on the wire as "", never null. TodoRoutes does
            // `body.listID?.let { fields["listID"] = it.takeIf(isNotBlank) }`,
            // so a null listID never reaches `fields` at all and
            // `TodoService.update` leaves the assignment untouched — the row
            // would clear optimistically and the next refetch would put the old
            // list straight back. Blank is what the backend maps to null (§4.5).
            listID: listID ?? "",
            instanceDate: row.instanceDate ?? null,
          },
        ),
      ).then((result) => {
        reportFailures("task.bulk_move_failed", result, "bulkUpdateFailed");
        refreshTodoViews();
      });
    },
    [
      cancelActiveTodoQueries,
      queryClient,
      refreshTodoViews,
      reportFailures,
      scopeListId,
    ],
  );

  return useMemo(
    () => ({
      completeSelected,
      deleteSelected,
      setPriorityForSelected,
      moveSelectedToList,
    }),
    [completeSelected, deleteSelected, moveSelectedToList, setPriorityForSelected],
  );
}

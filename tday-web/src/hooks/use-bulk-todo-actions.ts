import { useCallback, useMemo } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { useUndoableDelete } from "@/hooks/use-undoable-delete";
import { canonicalTodoId } from "@/lib/todo/todo-id";
import { patchTodo } from "@/lib/todo/patch-todo";
import { markTaskCompleted } from "@/lib/task-completion-signal";
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
   * Written `(old) => old?.filter(...)`, never `(old: TodoItemType[] = []) => ...`:
   * a non-optional array annotation hides `undefined` from tsc, throws on a cold
   * cache, and — inside a mutation's `onMutate` — aborts the mutation silently,
   * which looks exactly like a network failure. Returning `undefined` here tells
   * react-query to leave a cache it has never filled alone.
   */
  const pruneStagedRows = useCallback(
    (rowIds: ReadonlySet<string>) => {
      const prune = (old?: TodoItemType[]) =>
        old?.filter((todo) => !rowIds.has(todo.id));
      queryClient.setQueryData<TodoItemType[]>(["todo"], prune);
      queryClient.setQueryData<TodoItemType[]>(["todoTimeline"], prune);
      queryClient.setQueriesData<TodoItemType[]>({ queryKey: ["list"] }, prune);
    },
    [queryClient],
  );

  /** Undo path: nothing was sent, so the server still has every row. */
  const restoreStagedRows = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: ["todo"] });
    void queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
    void queryClient.invalidateQueries({ queryKey: ["list"] });
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
      cancelActiveTodoQueries();
      pruneStagedRows(new Set(rows.map((row) => row.id)));

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
            refreshTodoViews();
          });
        },
        undo: restoreStagedRows,
      });
    },
    [
      cancelActiveTodoQueries,
      pruneStagedRows,
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
      cancelActiveTodoQueries();
      pruneStagedRows(new Set(rows.map((row) => row.id)));

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
            refreshTodoViews();
          });
        },
        undo: restoreStagedRows,
      });
    },
    [
      cancelActiveTodoQueries,
      pruneStagedRows,
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
          { listID, instanceDate: row.instanceDate ?? null },
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

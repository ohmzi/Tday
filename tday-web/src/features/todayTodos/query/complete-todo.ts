import { useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { canonicalTodoId } from "@/lib/todo/todo-id";
import { TodoItemType } from "@/types";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";
import {
  markCelebrationCancelled,
  markTaskCompleted,
} from "@/lib/task-completion-signal";
import {
  releaseAndRestoreTodoRows,
  stageTodoRows,
} from "@/lib/todo/staged-todo-rows";

// Delayed-commit complete: `completeMutateFn` only stages the completion (prunes
// the active-list caches and shows an undoable toast). The PATCH /complete fires
// when the toast closes without undo; undo just refetches since the server never
// saw it. Mirrors the delayed-commit delete flow.
//
// The prune is one write and the window is five seconds of refetching, so the
// row is also *claimed* — `stageTodoRows` below holds it out of every list cache
// until this mutation settles or the undo releases it. Without that, the "undo
// just refetches" shortcut works against the row instead of for it: the server
// has not been told yet, so any refetch in the window restores the row the user
// just ticked. See `@/lib/todo/staged-todo-rows`.
export const useCompleteTodo = () => {
  const { toast } = useToast();
  const { showTodoCompletedToast } = useTodoActionToast();
  const queryClient = useQueryClient();
  const { mutate: commitComplete, isPending: completePending } = useMutation({
    mutationFn: async (todoItem: TodoItemType) => {
      const todoId = canonicalTodoId(todoItem.id);
      await api.PATCH({
        url: "/api/todo/complete",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          id: todoId,
          // Date, not epoch millis: the backend parses instanceDate as ISO-8601.
          instanceDate: todoItem.rrule ? (todoItem.instanceDate ?? null) : null,
        }),
      });
    },
    mutationKey: ["todo"],
    onError: (error) => {
      // No cache rollback needed: onSettled's invalidations refetch the still
      // incomplete rows from the server.
      toast({ description: error.message, variant: "destructive" });
    },
    onSettled: (_data, _error, todoItem) => {
      // The server has been told, so the row is the read path's business again —
      // and if the request failed it is still pending and MUST come back through
      // the invalidations below. Released before them, not after: the refetch
      // they trigger is the first one allowed to answer for this row. Every
      // row-list root is refetched, not only the two this site pruned, because
      // the guard claimed the row in all of them.
      releaseAndRestoreTodoRows(queryClient, [todoItem.id]);
      queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
      // Refresh per-list task counts shown in the sidebar / dashboard.
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] });
    },
  });

  // Stage: prune the caches now, but DON'T send the PATCH yet — the undo toast
  // decides whether the request ever fires.
  const completeMutateFn = (todoItem: TodoItemType) => {
    // The empty state that follows the last row leaving reads this to tell a
    // list the user finished from one that was never filled.
    markTaskCompleted();
    // Claim the row before the prune: from here the cache boundary refuses it,
    // so a refetch that was already in flight cannot slip back in behind this.
    stageTodoRows(queryClient, [todoItem.id]);
    void queryClient.cancelQueries({ queryKey: ["todo"] });
    void queryClient.cancelQueries({ queryKey: ["todoTimeline"] });
    queryClient.setQueryData<TodoItemType[]>(["todo"], (oldTodos = []) =>
      oldTodos.filter((oldTodo) => oldTodo.id !== todoItem.id),
    );
    queryClient.setQueryData<TodoItemType[]>(["todoTimeline"], (oldTodos = []) =>
      oldTodos.filter((oldTodo) => oldTodo.id !== todoItem.id),
    );

    showTodoCompletedToast({
      commit: () => commitComplete(todoItem),
      undo: () => {
        // A row is coming BACK, so this list is not finished any more and the
        // celebration ends now rather than when its own window runs out.
        // Stamped here rather than left to the refetch below: that is a network
        // round trip, and `useArrivalCancel`'s count-rise backstop cannot see
        // the row until it lands.
        markCelebrationCancelled();
        // The server still has the row (incomplete) — a refetch restores it, so
        // the claim goes first: this is the refetch that is meant to win. All of
        // the caches the claim covered, not just the pruned pair; see
        // `releaseAndRestoreTodoRows`.
        releaseAndRestoreTodoRows(queryClient, [todoItem.id]);
      },
    });
  };

  return { completeMutateFn, completePending };
};

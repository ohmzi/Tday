import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "@/hooks/use-toast";
import { api } from "@/lib/api-client";
import { canonicalTodoId } from "@/lib/todo/todo-id";
import { TodoItemType } from "@/types";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";
import { markTaskDeletedLocally } from "@/lib/task-completion-signal";
import {
  releaseAndRestoreTodoRows,
  stageTodoRows,
} from "@/lib/todo/staged-todo-rows";

// Delayed-commit delete: `deleteMutateFn` only stages the delete (prunes the
// caches and shows an undoable toast). The DELETE request fires when the toast
// closes without undo; undo just refetches since the server never saw it.
//
// Same claim as the completion path and for the same reason — see
// `@/lib/todo/staged-todo-rows`. This verb has the identical resurrection hole
// (a refetch inside the window restores the pruned row), so it takes the
// identical guard rather than a second one.
export const useDeleteTodo = () => {
  const { toast } = useToast();
  const { showTodoDeletedToast } = useTodoActionToast();
  const queryClient = useQueryClient();
  const { mutate: commitDelete, isPending: deletePending } = useMutation({
    mutationFn: async (todo: TodoItemType) => {
      await api.DELETE({
        url: "/api/todo",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id: canonicalTodoId(todo.id) }),
      });
    },
    mutationKey: ["todo"],
    onError: (error) => {
      // No cache rollback needed: onSettled's invalidations refetch the still
      // existing rows from the server.
      toast({
        description:
          error.message === "Failed to fetch"
            ? "failed to connect to server"
            : error.message,
        variant: "destructive",
      });
    },
    onSettled: (_data, _error, todo) => {
      // Sent (or failed) — the read path may answer for this row again. Before
      // the invalidations, so the refetch they fire is not filtered. Every
      // row-list root, not only the pruned pair: the guard claimed the row in all
      // of them.
      releaseAndRestoreTodoRows(queryClient, [todo.id]);
      queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
      // Refresh per-list task counts shown in the sidebar / dashboard.
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] });
    },
  });

  // Stage: prune the caches now, but DON'T send the DELETE yet — the undo
  // toast decides whether the request ever fires.
  const deleteMutateFn = (todo: TodoItemType) => {
    // The empty state that follows the last row leaving reads this to tell a
    // list a task was deleted out of from one that was just finished.
    markTaskDeletedLocally();
    // Claim the row before the prune, so an already-in-flight refetch cannot
    // slip back in behind it.
    stageTodoRows(queryClient, [todo.id]);
    void queryClient.cancelQueries({ queryKey: ["todo"] });
    void queryClient.cancelQueries({ queryKey: ["todoTimeline"] });
    void queryClient.cancelQueries({ queryKey: ["calendarTodo"] });
    queryClient.setQueryData<TodoItemType[]>(["todo"], (oldTodos = []) => {
      return oldTodos.filter((oldTodo) => oldTodo.id != todo.id);
    });
    queryClient.setQueryData<TodoItemType[]>(
      ["todoTimeline"],
      (oldTodos = []) => {
        return oldTodos.filter((oldTodo) => oldTodo.id != todo.id);
      },
    );

    showTodoDeletedToast(todo, {
      commit: () => commitDelete(todo),
      undo: () => {
        // The server still has the row — a refetch restores the pruned caches,
        // so the claim goes first: this is the refetch that is meant to win, and
        // it covers every cache the claim reached; see
        // `releaseAndRestoreTodoRows`.
        releaseAndRestoreTodoRows(queryClient, [todo.id]);
      },
    });
  };

  return { deleteMutateFn, deletePending };
};

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "@/hooks/use-toast";
import { api } from "@/lib/api-client";
import { canonicalTodoId } from "@/lib/todo/todo-id";
import { TodoItemType } from "@/types";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";
import {
  releaseAndRestoreTodoRows,
  stageTodoRows,
} from "@/lib/todo/staged-todo-rows";

// Delayed-commit delete: `deleteMutate` only stages the delete (prunes the
// caches and shows an undoable toast). The DELETE request fires when the toast
// closes without undo; undo just refetches since the server never saw it.
//
// The row is claimed at the cache boundary as well as pruned — see
// `@/lib/todo/staged-todo-rows`; the calendar re-reads `["calendarTodo"]` on
// every `todo` realtime event, including the echo of this delete.
export const useDeleteCalendarTodo = () => {
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
    mutationKey: ["calendarTodo"],
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
      // Sent (or failed) — released before the invalidations, which are the
      // refetches allowed to answer for the row again.
      releaseAndRestoreTodoRows(queryClient, [todo.id]);
    },
  });

  // Stage: prune the caches now, but DON'T send the DELETE yet — the undo
  // toast decides whether the request ever fires.
  const deleteMutate = (todo: TodoItemType) => {
    // Claim the row before the prune, so a refetch already in flight cannot slip
    // back in behind it.
    stageTodoRows(queryClient, [todo.id]);
    void queryClient.cancelQueries({
      queryKey: ["calendarTodo"],
    });
    queryClient.setQueriesData<TodoItemType[]>(
      { queryKey: ["calendarTodo"] },
      (old) => old?.filter((oldTodo) => oldTodo.id !== todo.id),
    );

    showTodoDeletedToast(todo, {
      commit: () => commitDelete(todo),
      undo: () => {
        // The server still has the row — a refetch restores the cache, so the
        // claim goes first: this is the refetch that is meant to win.
        releaseAndRestoreTodoRows(queryClient, [todo.id]);
      },
    });
  };

  return { deleteMutate, deletePending };
};

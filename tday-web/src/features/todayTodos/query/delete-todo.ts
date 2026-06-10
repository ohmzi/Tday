import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "@/hooks/use-toast";
import { api } from "@/lib/api-client";
import { canonicalTodoId } from "@/lib/todo/todo-id";
import { TodoItemType } from "@/types";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";

// Delayed-commit delete: `deleteMutateFn` only stages the delete (prunes the
// caches and shows an undoable toast). The DELETE request fires when the toast
// closes without undo; undo just refetches since the server never saw it.
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
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["todo"] });
      queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
      queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
      queryClient.invalidateQueries({ queryKey: ["overdueTodo"] });
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      // Refresh per-list task counts shown in the sidebar / dashboard.
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] });
    },
  });

  // Stage: prune the caches now, but DON'T send the DELETE yet — the undo
  // toast decides whether the request ever fires.
  const deleteMutateFn = (todo: TodoItemType) => {
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
        // The server still has the row — a refetch restores the pruned caches.
        void queryClient.invalidateQueries({ queryKey: ["todo"] });
        void queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      },
    });
  };

  return { deleteMutateFn, deletePending };
};

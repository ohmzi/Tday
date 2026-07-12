import { useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { canonicalTodoId } from "@/lib/todo/todo-id";
import { TodoItemType } from "@/types";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";

// Delayed-commit complete: `completeMutateFn` only stages the completion (prunes
// the active-list caches and shows an undoable toast). The PATCH /complete fires
// when the toast closes without undo; undo just refetches since the server never
// saw it. Mirrors the delayed-commit delete flow.
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
          instanceDate: todoItem.rrule ? todoItem.instanceDate?.getTime() : null,
        }),
      });
    },
    mutationKey: ["todo"],
    onError: (error) => {
      // No cache rollback needed: onSettled's invalidations refetch the still
      // incomplete rows from the server.
      toast({ description: error.message, variant: "destructive" });
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
      queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      // Refresh per-list task counts shown in the sidebar / dashboard.
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] });
    },
  });

  // Stage: prune the caches now, but DON'T send the PATCH yet — the undo toast
  // decides whether the request ever fires.
  const completeMutateFn = (todoItem: TodoItemType) => {
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
        // The server still has the row (incomplete) — a refetch restores it.
        void queryClient.invalidateQueries({ queryKey: ["todo"] });
        void queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      },
    });
  };

  return { completeMutateFn, completePending };
};

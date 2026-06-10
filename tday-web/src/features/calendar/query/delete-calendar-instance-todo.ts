import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "@/hooks/use-toast";
import { api } from "@/lib/api-client";
import { canonicalTodoId } from "@/lib/todo/todo-id";
import { TodoItemType } from "@/types";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";

// Delayed-commit delete: `deleteInstanceMutate` only stages the delete (prunes
// the caches and shows an undoable toast). The DELETE request fires when the
// toast closes without undo; undo just refetches since the server never saw it.
export const useDeleteCalendarInstanceTodo = () => {
  const { toast } = useToast();
  const { showTodoDeletedToast } = useTodoActionToast();
  const queryClient = useQueryClient();
  const { mutate: commitDelete, isPending: deleteInstancePending } =
    useMutation({
      mutationFn: async (todo: TodoItemType) => {
        await api.DELETE({
          url: "/api/todo/instance",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            id: canonicalTodoId(todo.id),
            instanceDate: todo.instanceDate?.getTime() ?? null,
          }),
        });
      },
      mutationKey: ["calendarTodo"],
      onError: (error) => {
        // No cache rollback needed: onSettled's invalidations refetch the
        // still existing rows from the server.
        toast({
          description:
            error.message === "Failed to fetch"
              ? "failed to connect to server"
              : error.message,
          variant: "destructive",
        });
      },
      onSettled: () => {
        queryClient.invalidateQueries({
          queryKey: ["todo"],
        });
        queryClient.invalidateQueries({
          queryKey: ["calendarTodo"],
        });
        queryClient.invalidateQueries({
          queryKey: ["todoTimeline"],
        });
      },
    });

  // Stage: prune the caches now, but DON'T send the DELETE yet — the undo
  // toast decides whether the request ever fires.
  const deleteInstanceMutate = (todo: TodoItemType) => {
    void queryClient.cancelQueries({
      queryKey: ["calendarTodo"],
    });
    queryClient.setQueriesData<TodoItemType[]>(
      { queryKey: ["calendarTodo"] },
      (old) =>
        old?.filter(
          (oldTodo) => todo.instanceDate !== oldTodo.instanceDate,
        ),
    );

    showTodoDeletedToast(todo, {
      commit: () => commitDelete(todo),
      undo: () => {
        // The server still has the row — a refetch restores the cache.
        void queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
      },
    });
  };

  return { deleteInstanceMutate, deleteInstancePending };
};

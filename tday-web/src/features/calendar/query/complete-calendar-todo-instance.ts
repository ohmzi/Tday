import { useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { canonicalTodoId } from "@/lib/todo/todo-id";
import { TodoItemType } from "@/types";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";

// Delayed-commit complete (see complete-todo.ts): stage the removal of this
// recurring instance from the calendar cache, show an undoable toast, and only
// PATCH /complete once the toast closes without undo.
export const useCompleteCalendarTodoInstance = () => {
  const { toast } = useToast();
  const { showTodoCompletedToast } = useTodoActionToast();
  const queryClient = useQueryClient();

  const { mutate: commitComplete, isPending } = useMutation({
    mutationFn: async ({ todoItem }: { todoItem: TodoItemType }) => {
      const todoId = canonicalTodoId(todoItem.id);
      await api.PATCH({
        url: "/api/todo/complete",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          id: todoId,
          instanceDate: todoItem.instanceDate?.getTime() ?? null,
        }),
      });
    },
    onError: (error) => {
      toast({ description: error.message, variant: "destructive" });
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
      queryClient.invalidateQueries({ queryKey: ["todo"] });
      queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
    },
  });

  const mutateComplete = ({ todoItem }: { todoItem: TodoItemType }) => {
    const instanceDateMs = todoItem.instanceDate?.getTime();
    void queryClient.cancelQueries({ queryKey: ["calendarTodo"] });
    if (instanceDateMs != null) {
      queryClient.setQueriesData<TodoItemType[]>(
        { queryKey: ["calendarTodo"] },
        (old) =>
          old?.filter((todo) => todo.instanceDate?.getTime() !== instanceDateMs),
      );
    }

    showTodoCompletedToast({
      commit: () => commitComplete({ todoItem }),
      undo: () => {
        // The server still has the instance (incomplete) — a refetch restores it.
        void queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
      },
    });
  };

  return { mutateComplete, isPending };
};

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "@/hooks/use-toast";
import { api } from "@/lib/api-client";
import { TodoItemType } from "@/types";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";
export const useDeleteCalendarTodo = () => {
  const { toast } = useToast();
  const { showTodoDeletedToast } = useTodoActionToast();
  const queryClient = useQueryClient();
  const { mutate: deleteMutate, isPending: deletePending } = useMutation({
    mutationFn: async (todo: TodoItemType) => {
      await api.DELETE({
        url: "/api/todo",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id: todo.id.split(":")[0] }),
      });
    },
    onMutate: async (todo: TodoItemType) => {
      await queryClient.cancelQueries({
        queryKey: ["calendarTodo"],
      });
      const oldTodos = queryClient.getQueriesData({
        queryKey: ["calendarTodo"],
      });

      queryClient.setQueriesData<TodoItemType[]>(
        { queryKey: ["calendarTodo"] },
        (old) => old?.filter((oldTodo) => oldTodo.id !== todo.id),
      );
      return { oldTodos };
    },
    mutationKey: ["calendarTodo"],
    onError: (error, _, context) => {
      queryClient.setQueryData(["todo"], context?.oldTodos);
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
    onSuccess: (_data, deletedTodo) => {
      showTodoDeletedToast(deletedTodo);
    },
  });
  return { deleteMutate, deletePending };
};

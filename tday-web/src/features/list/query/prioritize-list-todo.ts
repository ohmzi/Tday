import { useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import {
  canonicalTodoId,
  todoInstanceTimestampFromId,
} from "@/lib/todo/todo-id";
import { TodoItemType } from "@/types";

export const usePrioritizeListTodo = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const { mutate: prioritizeMutateFn, isPending: prioritizePending } =
    useMutation({
      mutationFn: async ({
        id,
        level,
        isRecurring,
      }: {
        id: string;
        level: "Low" | "Medium" | "High";
        isRecurring: boolean;
      }) => {
        const instanceDate = todoInstanceTimestampFromId(id);

        await api.PATCH({
          url: "/api/todo/prioritize",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            id: canonicalTodoId(id),
            priority: level,
            instanceDate: isRecurring ? instanceDate : null,
          }),
        });
      },

      onMutate: async ({
        id,
        level,
      }: {
        id: string;
        level: "Low" | "Medium" | "High";
        isRecurring: boolean;
      }) => {
        await queryClient.cancelQueries({ queryKey: ["list"] });
        await queryClient.cancelQueries({ queryKey: ["todo"] });

        const oldListTodos = queryClient.getQueryData<TodoItemType[]>(["list"]);
        const oldTodos = queryClient.getQueryData<TodoItemType[]>(["todo"]);

        queryClient.setQueriesData<TodoItemType[]>(
          { queryKey: ["list"] },
          (old) => {
            return old?.map((todo) =>
              todo.id === id ? { ...todo, priority: level } : todo,
            );
          },
        );

        queryClient.setQueryData<TodoItemType[]>(["todo"], (old) => {
          return old?.map((todo) =>
            todo.id === id ? { ...todo, priority: level } : todo,
          );
        });

        return { oldListTodos, oldTodos };
      },

      mutationKey: ["list"],

      onError: (error, _, context) => {
        queryClient.setQueryData(["list"], context?.oldListTodos);
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
        queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
        queryClient.invalidateQueries({ queryKey: ["overdueTodo"] });
      },
    });

  return { prioritizeMutateFn, prioritizePending };
};

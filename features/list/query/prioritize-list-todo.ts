import { useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
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
        const todoId = id.split(":")[0];
        const instanceDate = Number(id.split(":")[1]);

        await api.PATCH({
          url: "/api/todo/prioritize",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            id: todoId,
            priority: level,
            instanceDate:
              isRecurring && Number.isFinite(instanceDate) ? instanceDate : null,
          }),
        });
      },

      onMutate: async ({
        id,
        level,
      }: {
        id: string;
        level: "Low" | "Medium" | "High";
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

import { useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { TodoItemType } from "@/types";
export const useCompleteCalendarTodoInstance = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const { mutate, isPending } = useMutation({
    mutationFn: async ({ todoItem }: { todoItem: TodoItemType }) => {
      const todoId = todoItem.id.split(":")[0];
      await api.PATCH({
        url: "/api/todo/complete",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          id: todoId,
          instanceDate: todoItem.instanceDate?.getTime() ?? null,
        }),
      });
    },

    onMutate: async ({ todoItem }: { todoItem: TodoItemType }) => {
      await queryClient.cancelQueries({ queryKey: ["calendarTodo"] });

      const oldTodos = queryClient.getQueryData<TodoItemType[]>([
        "calendarTodo",
      ]);

      if (todoItem.instanceDate) {
        queryClient.setQueriesData<TodoItemType[]>(
          {
            queryKey: ["calendarTodo"],
          },
          (old) =>
            old?.filter(
              (todo) =>
                todo.instanceDate?.getTime() !==
                todoItem.instanceDate!.getTime(),
            ),
        );
      }

      return { oldTodos };
    },

    onError: (error, _vars, context) => {
      toast({ description: error.message, variant: "destructive" });
      queryClient.setQueryData(["calendarTodo"], context?.oldTodos);
    },

    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
      queryClient.invalidateQueries({
        queryKey: ["todo"],
      });
      queryClient.invalidateQueries({
        queryKey: ["completedTodo"],
      });
    },
  });

  return { mutateComplete: mutate, isPending };
};

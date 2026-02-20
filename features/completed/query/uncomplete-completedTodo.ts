import { useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { CompletedTodoItemType } from "@/types";
export const useUnCompleteTodo = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { mutate: mutateUnComplete, isPending } = useMutation({
    mutationFn: async (todoItem: CompletedTodoItemType) => {
      await api.PATCH({
        url: "/api/todo/uncomplete",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          id: todoItem.originalTodoID,
          instanceDate: todoItem.rrule ? todoItem.instanceDate?.getTime() : null,
        }),
      });
    },
    onMutate: async (todoItem: CompletedTodoItemType) => {
      await queryClient.cancelQueries({ queryKey: ["completedTodo"] });
      const oldTodos = queryClient.getQueryData([
        "completedTodo",
      ]) as CompletedTodoItemType[];

      queryClient.setQueryData(
        ["completedTodo"],
        (oldTodos: CompletedTodoItemType[]) =>
          oldTodos.flatMap((oldTodo) => {
            if (oldTodo.id === todoItem.id) return [];
            return [oldTodo];
          }),
      );
      return { oldTodos };
    },
    onError: (error, newTodo, context) => {
      toast({ description: error.message, variant: "destructive" });
      queryClient.setQueryData(["completedTodo"], context?.oldTodos);
    },
    onSuccess: () => {},
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["list"] });
      queryClient.invalidateQueries({ queryKey: ["todo"] });
      queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
    },
  });

  return { mutateUnComplete, isPending };
};

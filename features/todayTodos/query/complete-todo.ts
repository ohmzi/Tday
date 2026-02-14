import { useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { TodoItemType } from "@/types";
export const useCompleteTodo = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { mutate: completeMutateFn, isPending: completePending } = useMutation({
    mutationFn: async (todoItem: TodoItemType) => {
      const todoId = todoItem.id.split(":")[0];
      if (todoItem.rrule) {
        await api.PATCH({
          url: `/api/todo/instance/${todoId}/complete`,
          body: JSON.stringify({
            ...todoItem,
            id: todoId,
            completed: !todoItem.completed,
          }),
        });
      } else {
        await api.PATCH({
          url: `/api/todo/${todoId}/complete`,
          body: JSON.stringify({
            ...todoItem,
            id: todoId,
            completed: !todoItem.completed,
          }),
        });
      }
    },
    onMutate: async (todoItem: TodoItemType) => {
      await queryClient.cancelQueries({ queryKey: ["todo"] });
      await queryClient.cancelQueries({ queryKey: ["todoTimeline"] });
      const oldTodos = queryClient.getQueryData(["todo"]) as TodoItemType[];
      const oldTimelineTodos = queryClient.getQueryData([
        "todoTimeline",
      ]) as TodoItemType[];
      queryClient.setQueryData(["todo"], (oldTodos: TodoItemType[]) =>
        oldTodos.flatMap((oldTodo) => {
          if (oldTodo.id === todoItem.id) return [];
          return [oldTodo];
        }),
      );
      queryClient.setQueryData(["todoTimeline"], (oldTodos: TodoItemType[] = []) =>
        oldTodos.flatMap((oldTodo) => {
          if (oldTodo.id === todoItem.id) return [];
          return [oldTodo];
        }),
      );
      return { oldTodos, oldTimelineTodos };
    },
    onError: (error, newTodo, context) => {
      toast({ description: error.message, variant: "destructive" });
      queryClient.setQueryData(["todo"], context?.oldTodos);
      queryClient.setQueryData(["todoTimeline"], context?.oldTimelineTodos);
    },
    onSuccess: () => {},
    onSettled: () => {
      //optimistically update calendar todos
      queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
      queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
    },
  });

  return { completeMutateFn, completePending };
};

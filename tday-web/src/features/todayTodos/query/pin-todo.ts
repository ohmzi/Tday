import { useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { TodoItemType } from "@/types";

export function usePinTodo() {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { mutate: pinMutateFn, isPending: pinPending } = useMutation({
    mutationFn: async (todoItem: TodoItemType) => {
      await api.PATCH({
        url: "/api/todo",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          id: todoItem.id.split(":")[0],
          pinned: !todoItem.pinned,
        }),
      });
    },

    onMutate: async (todoItem: TodoItemType) => {
      await queryClient.cancelQueries({ queryKey: ["todo"] });
      await queryClient.cancelQueries({ queryKey: ["todoTimeline"] });
      await queryClient.cancelQueries({ queryKey: ["calendarTodo"] });
      await queryClient.cancelQueries({ queryKey: ["overdueTodo"] });

      const oldTodos = queryClient.getQueryData(["todo"]);
      const oldTimelineTodos = queryClient.getQueryData(["todoTimeline"]);

      const togglePin = (todos: TodoItemType[] = []) =>
        todos.map((t) =>
          t.id === todoItem.id ? { ...t, pinned: !todoItem.pinned } : t,
        );

      queryClient.setQueryData<TodoItemType[]>(["todo"], togglePin);
      queryClient.setQueryData<TodoItemType[]>(["todoTimeline"], togglePin);

      return { oldTodos, oldTimelineTodos };
    },
    onError: (error, _, context) => {
      queryClient.setQueryData(["todo"], context?.oldTodos);
      queryClient.setQueryData(["todoTimeline"], context?.oldTimelineTodos);
      toast({ description: error.message, variant: "destructive" });
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["todo"] });
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
      queryClient.invalidateQueries({ queryKey: ["overdueTodo"] });
    },
  });

  return { pinMutateFn, pinPending };
}

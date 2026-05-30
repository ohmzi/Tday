import { useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import {
  canonicalTodoId,
  todoInstanceTimestampFromId,
} from "@/lib/todo/todo-id";
import { TodoItemType } from "@/types";

export const usePrioritizeTodo = () => {
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
        await queryClient.cancelQueries({ queryKey: ["todo"] });
        await queryClient.cancelQueries({ queryKey: ["todoTimeline"] });

        const oldTodos = queryClient.getQueryData<TodoItemType[]>(["todo"]);
        const oldTimelineTodos = queryClient.getQueryData<TodoItemType[]>([
          "todoTimeline",
        ]);
        queryClient.setQueryData(["todo"], (oldTodos: TodoItemType[]) =>
          oldTodos.map((oldTodo) => {
            if (oldTodo.id === id) {
              return {
                ...oldTodo,
                priority: level,
              };
            }
            return oldTodo;
          }),
        );
        queryClient.setQueryData(["todoTimeline"], (oldTodos: TodoItemType[] = []) =>
          oldTodos.map((oldTodo) => {
            if (oldTodo.id === id) {
              return {
                ...oldTodo,
                priority: level,
              };
            }
            return oldTodo;
          }),
        );

        return { oldTodos, oldTimelineTodos };
      },
      onSuccess: () => {
        // queryClient.invalidateQueries({ queryKey: ["todo"] });
      },
      onError: (error, _, context) => {
        queryClient.setQueryData(["todo"], context?.oldTodos);
        queryClient.setQueryData(["todoTimeline"], context?.oldTimelineTodos);
        toast({ description: error.message, variant: "destructive" });
      },
      onSettled() {
        //optimistically update calendar todos
        queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
        queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      },
    });

  return { prioritizeMutateFn, prioritizePending };
};

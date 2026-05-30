import { useToast } from "@/hooks/use-toast";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  patchTodo,
  type TodoItemTypeWithDateChecksum,
} from "@/lib/todo/patch-todo";
import { TodoItemType } from "@/types";
import { endOfDay } from "date-fns";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";

export const useEditListTodo = () => {
  const { toast } = useToast();
  const { showTodoUpdatedToast } = useTodoActionToast();
  const queryClient = useQueryClient();

  const { mutate: editTodoMutateFn, status: editTodoStatus } = useMutation({
    mutationFn: (params: TodoItemTypeWithDateChecksum) =>
      patchTodo(params, {
        instanceDate: params.instanceDate,
        listID: params.listID,
      }),

    onMutate: async (newTodo) => {
      await queryClient.cancelQueries({ queryKey: ["todo"] });
      await queryClient.cancelQueries({ queryKey: ["list"] });

      const oldTodos = queryClient.getQueryData<TodoItemType[]>(["todo"]);
      const oldListTodos = queryClient.getQueryData<TodoItemType[]>(["list"]);

      // update today/todo cache
      queryClient.setQueryData<TodoItemType[]>(["todo"], (oldTodos) =>
        oldTodos?.flatMap((oldTodo) => {
          if (oldTodo.id === newTodo.id) {
            if (newTodo.due > endOfDay(new Date())) {
              return [];
            }
            return {
              ...oldTodo,
              completed: newTodo.completed,
              order: newTodo.order,
              pinned: newTodo.pinned,
              userID: newTodo.userID,
              id: newTodo.id,
              title: newTodo.title,
              description: newTodo.description,
              priority: newTodo.priority,
              due: newTodo.due,
              rrule: newTodo.rrule,
              listID: newTodo.listID,
              createdAt: new Date(),
            };
          }
          return oldTodo;
        }),
      );

      // update list cache
      queryClient.setQueriesData<TodoItemType[]>(
        { queryKey: ["list"] },
        (oldTodos) =>
          oldTodos?.map((oldTodo) => {
            if (oldTodo.id === newTodo.id) {
              return {
                ...oldTodo,
                ...newTodo,
              };
            }
            return oldTodo;
          }),
      );

      return { oldTodos, oldListTodos };
    },

    onError: (error, _, context) => {
      queryClient.setQueryData(["todo"], context?.oldTodos);
      queryClient.setQueryData(["list"], context?.oldListTodos);

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
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
    },
    onSuccess: (_data, updatedTodo) => {
      showTodoUpdatedToast(updatedTodo);
    },
  });

  return { editTodoMutateFn, editTodoStatus };
};

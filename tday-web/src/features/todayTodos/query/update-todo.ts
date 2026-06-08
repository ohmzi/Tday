import { useToast } from "@/hooks/use-toast";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  patchTodo,
  type TodoItemTypeWithDateChecksum,
} from "@/lib/todo/patch-todo";
import { TodoItemType } from "@/types";
import { endOfDay } from "date-fns";

export type { TodoItemTypeWithDateChecksum } from "@/lib/todo/patch-todo";

export const useEditTodo = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const { mutate: editTodoMutateFn, status: editTodoStatus } = useMutation({
    mutationFn: (params: TodoItemTypeWithDateChecksum) =>
      patchTodo(params, {
        instanceDate: params.instanceDate,
        listID: params.listID ?? null,
      }),
    onMutate: async (newTodo) => {
      await queryClient.cancelQueries({ queryKey: ["todo"] });
      await queryClient.cancelQueries({ queryKey: ["todoTimeline"] });
      const oldTodos = queryClient.getQueryData<TodoItemType[]>(["todo"]);
      const oldTimelineTodos = queryClient.getQueryData<TodoItemType[]>([
        "todoTimeline",
      ]);

      queryClient.setQueryData(["todo"], (oldTodos: TodoItemType[]) =>
        oldTodos.flatMap((oldTodo) => {
          if (oldTodo.id === newTodo.id) {
            if (newTodo.due > endOfDay(new Date())) {
              return [];
            }
            return {
              ...oldTodo,
              completed: newTodo.completed,
              pinned: newTodo.pinned,
              title: newTodo.title,
              description: newTodo.description,
              priority: newTodo.priority,
              due: newTodo.due,
              rrule: newTodo.rrule,
              listID: newTodo.listID ?? null,
            };
          }
          return oldTodo;
        }),
      );
      queryClient.setQueryData(["todoTimeline"], (oldTodos: TodoItemType[] = []) =>
        oldTodos.flatMap((oldTodo) => {
          if (oldTodo.id === newTodo.id) {
            return {
              ...oldTodo,
              completed: newTodo.completed,
              pinned: newTodo.pinned,
              title: newTodo.title,
              description: newTodo.description,
              priority: newTodo.priority,
              due: newTodo.due,
              rrule: newTodo.rrule,
              listID: newTodo.listID ?? null,
            };
          }
          return oldTodo;
        }),
      );
      return { oldTodos, oldTimelineTodos };
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      // A task may have moved lists — refresh per-list counts.
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] });
    },
    onError: (error, _newTodo, context) => {
      queryClient.setQueryData(["todo"], context?.oldTodos);
      queryClient.setQueryData(["todoTimeline"], context?.oldTimelineTodos);
      toast({ description: error.message, variant: "destructive" });
    },
  });

  return { editTodoMutateFn, editTodoStatus };
};

import { useToast } from "@/hooks/use-toast";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { todoSchema } from "@/schema";
import { TodoItemType } from "@/types";
import { endOfDay } from "date-fns";

export interface TodoItemTypeWithDateChecksum extends TodoItemType {
  dateRangeChecksum: string;
  rruleChecksum: string | null;
}
async function patchTodo({ todo }: { todo: TodoItemTypeWithDateChecksum }) {
  if (!todo.id) {
    throw new Error("this todo is missing");
  }
  //validate input
  const parsedObj = todoSchema.safeParse({
    title: todo.title,
    description: todo.description,
    priority: todo.priority,
    dtstart: todo.dtstart,
    due: todo.due,
    rrule: todo.rrule,
    listID: todo.listID ?? null,
  });
  if (!parsedObj.success) {
    console.log(parsedObj.error.errors[0]);
    return;
  }
  const dateChanged =
    todo.dateRangeChecksum !==
    todo.dtstart.toISOString() + todo.due.toISOString();

  const rruleChanged = todo.rruleChecksum !== todo.rrule;

  const todoId = todo.id.split(":")[0];
  await api.PATCH({
    url: `/api/todo/${todoId}`,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      ...parsedObj.data,
      id: todoId,
      instanceDate: todo.instanceDate,
      dateChanged,
      rruleChanged,
      listID: todo.listID ?? null,
    }),
  });
}

export const useEditTodo = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const { mutate: editTodoMutateFn, status: editTodoStatus } = useMutation({
    mutationFn: (params: TodoItemTypeWithDateChecksum) =>
      patchTodo({ todo: params }),
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
            if (newTodo.dtstart > endOfDay(new Date())) {
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
              dtstart: newTodo.dtstart,
              rrule: newTodo.rrule,
              listID: newTodo.listID ?? null,
              durationMinutes: newTodo.durationMinutes,
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
              dtstart: newTodo.dtstart,
              rrule: newTodo.rrule,
              listID: newTodo.listID ?? null,
              durationMinutes: newTodo.durationMinutes,
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
    },
    onError: (error, newTodo, context) => {
      queryClient.setQueryData(["todo"], context?.oldTodos);
      queryClient.setQueryData(["todoTimeline"], context?.oldTimelineTodos);
      toast({ description: error.message, variant: "destructive" });
    },
  });

  return { editTodoMutateFn, editTodoStatus };
};

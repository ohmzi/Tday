import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { TodoApiItemType, TodoItemType } from "@/types";
import parseApiDateTime, { parseOptionalApiDateTime } from "@/lib/date/parseApiDateTime";

export const useList = ({ id }: { id: string }) => {
  const {
    data: listTodos = [],
    isLoading: listTodosLoading,
    isFetching,
    isPending,
  } = useQuery<TodoItemType[]>({
    queryKey: ["list", id],
    retry: 2,
    staleTime: 5 * 60 * 1000,
    queryFn: async ({ queryKey, signal }) => {
      const [, listId] = queryKey as [string, string];
      const { todos } = await api.GET({
        url: `/api/list/${listId}`,
        signal,
      });

      const todoWithFormattedDates = (todos as TodoApiItemType[]).filter((todo) => todo.due != null).map((todo) => {
        // id needs to be todo id + instance date, so that ghost todos of the same parent can have unique ids
        const todoInstanceDate = todo.instanceDate
          ? parseApiDateTime(todo.instanceDate)
          : null;
        const todoInstanceDateTime = todoInstanceDate?.getTime();
        const todoId = `${todo.id}:${todoInstanceDateTime}`;
        // `/api/list/:id` answers with `ListTodoDto`, which is a narrower shape
        // than the timeline's `TodoResponse`: before the release that added
        // them it carried no `rrule`, `listID`, `pinned`, `createdAt` or
        // `updatedAt` at all. Spreading it raw produced rows whose `rrule` was
        // `undefined`, and `todoSchema.rrule` is `.nullable()`, not
        // `.optional()` — so `patchTodo` rejected every bulk move on this
        // screen, warned to the console and returned without sending anything.
        // Normalising here, next to the `listID` normalisation that was already
        // doing this job, keeps every consumer on one row shape.
        const recurrenceUnknown = !("rrule" in todo);
        return {
          ...todo,
          id: todoId,
          pinned: todo.pinned ?? false,
          createdAt: parseOptionalApiDateTime(todo.createdAt) ?? new Date(0),
          updatedAt: parseOptionalApiDateTime(todo.updatedAt),
          due: parseApiDateTime(todo.due!),
          instanceDate: todoInstanceDate,
          rrule: todo.rrule ?? null,
          listID: todo.listID ?? listId ?? null,
          // A row from a server that never states recurrence is treated as
          // repeating, so the §4.1 guard keeps it out of delete/priority/move
          // instead of silently deciding nothing on this screen repeats.
          ...(recurrenceUnknown ? { recurrenceUnknown: true } : {}),
        };
      });
      return todoWithFormattedDates;
    },
  });

  return { listTodos, listTodosLoading, isFetching, isPending };
};

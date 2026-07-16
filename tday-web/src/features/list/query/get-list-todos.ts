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
      const [, id] = queryKey;
      const { todos } = await api.GET({
        url: `/api/list/${id}`,
        signal,
      });

      const todoWithFormattedDates = (todos as TodoApiItemType[]).filter((todo) => todo.due != null).map((todo) => {
        // id needs to be todo id + instance date, so that ghost todos of the same parent can have unique ids
        const todoInstanceDate = todo.instanceDate
          ? parseApiDateTime(todo.instanceDate)
          : null;
        const todoInstanceDateTime = todoInstanceDate?.getTime();
        const todoId = `${todo.id}:${todoInstanceDateTime}`;
        return {
          ...todo,
          id: todoId,
          createdAt: parseApiDateTime(todo.createdAt),
          updatedAt: parseOptionalApiDateTime(todo.updatedAt),
          due: parseApiDateTime(todo.due!),
          instanceDate: todoInstanceDate,
          listID: todo.listID ?? null,
        };
      });
      return todoWithFormattedDates;
    },
  });

  return { listTodos, listTodosLoading, isFetching, isPending };
};

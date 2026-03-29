import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { TodoItemType } from "@/types";
import { endOfToday, startOfToday } from "date-fns";

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
    queryFn: async ({ queryKey }) => {
      const [, id] = queryKey;
      const { todos } = await api.GET({ url: `/api/list/${id}?start=${startOfToday().getTime()}&end=${endOfToday().getTime()}` });

      const todoWithFormattedDates = todos.map((todo: TodoItemType) => {
        // id needs to be todo id + instance date, so that ghost todos of the same parent can have unique ids
        const todoInstanceDate = todo.instanceDate
          ? new Date(todo.instanceDate)
          : null;
        const todoInstanceDateTime = todoInstanceDate?.getTime();
        const todoId = `${todo.id}:${todoInstanceDateTime}`;
        return {
          ...todo,
          id: todoId,
          createdAt: new Date(todo.createdAt),
          dtstart: new Date(todo.dtstart),
          due: new Date(todo.due),
          instanceDate: todoInstanceDate,
          listID: todo.listID ?? null,
        };
      });
      return todoWithFormattedDates;
    },
  });

  return { listTodos, listTodosLoading, isFetching, isPending };
};

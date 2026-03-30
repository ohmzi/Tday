import { CompletedTodoItemType } from "@/types";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";

export const useCompletedTodo = () => {
  const {
    data: completedTodos = [],
    isLoading: todoLoading,
  } = useQuery<CompletedTodoItemType[]>({
    queryKey: ["completedTodo"],
    retry: 2,
    staleTime: 5 * 60 * 1000,
    queryFn: async () => {
      const data = await api.GET({ url: `/api/completedTodo` });
      const { completedTodos }: { completedTodos: CompletedTodoItemType[] } =
        data;
      if (!completedTodos) {
        throw new Error(
          data.message || `bad server response: Did not recieve todo`,
        );
      }

      const completedTodoWithFormattedDates = completedTodos.map((todo) => {
        const todoInstanceDate = todo.instanceDate
          ? new Date(todo.instanceDate)
          : null;

        const todoInstanceDateTime = todoInstanceDate?.getTime();
        const todoId = `${todo.id}:${todoInstanceDateTime}`;
        return {
          ...todo,
          id: todoId,
          instanceDate: todoInstanceDate,
          dtstart: new Date(todo.dtstart),
          createdAt: new Date(todo.createdAt),
          due: new Date(todo.due),
          completedAt: new Date(todo.completedAt),
        };
      });

      return completedTodoWithFormattedDates;
    },
  });

  return { completedTodos, todoLoading };
};

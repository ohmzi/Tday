import { TodoItemType } from "@/types";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { startOfToday, endOfToday } from "date-fns";

const getTodo = async () => {
  const data = await api.GET({
    url: `/api/todo?start=${startOfToday().getTime()}&end=${endOfToday().getTime()}`,
  });
  const { todos }: { todos: TodoItemType[] } = data;
  if (!todos) {
    throw new Error(
      data.message || `bad server response: Did not recieve todo`,
    );
  }
  const todoWithFormattedDates = todos.map((todo) => {
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
};

export const useTodo = () => {
  const {
    data: todos = [],
    isLoading: todoLoading,
  } = useQuery<TodoItemType[]>({
    queryKey: ["todo"],
    retry: 2,
    queryFn: getTodo,
  });

  return { todos, todoLoading };
};

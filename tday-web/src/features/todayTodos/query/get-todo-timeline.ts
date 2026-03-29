import { TodoItemType } from "@/types";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";

const getTodoTimeline = async () => {
  const data = await api.GET({
    url: "/api/todo?timeline=true",
  });
  const { todos }: { todos: TodoItemType[] } = data;

  if (!todos) {
    throw new Error(data.message || "bad server response: Did not recieve todo");
  }

  return todos.map((todo) => {
    const todoInstanceDate = todo.instanceDate ? new Date(todo.instanceDate) : null;
    const todoInstanceDateTime = todoInstanceDate?.getTime();
    const todoId = `${todo.id}:${todoInstanceDateTime}`;

    return {
      ...todo,
      id: todoId,
      createdAt: new Date(todo.createdAt),
      dtstart: new Date(todo.dtstart),
      due: new Date(todo.due),
      instanceDate: todoInstanceDate,
    };
  });
};

export const useTodoTimeline = () => {
  const {
    data: todos = [],
    isLoading: todoLoading,
  } = useQuery<TodoItemType[]>({
    queryKey: ["todoTimeline"],
    retry: 2,
    queryFn: getTodoTimeline,
  });

  return { todos, todoLoading };
};

import { TodoApiItemType, TodoItemType } from "@/types";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import parseApiDateTime, { parseOptionalApiDateTime } from "@/lib/date/parseApiDateTime";

const getTodoTimeline = async () => {
  const data = await api.GET({
    // `expand=true` asks the server to materialise recurring occurrences. The
    // web has no way to expand a series itself — `TodoDto` carries `rrule` but
    // not `exdates`/`instances`, so a cancelled or completed occurrence would be
    // invisible — and without expansion a "daily" task only ever appeared on its
    // anchor date. The native clients still take the one-template-per-series
    // shape they key off; this is opt-in.
    url: "/api/todo?timeline=true&recurringFutureDays=60&expand=true",
  });
  const { todos }: { todos: TodoApiItemType[] } = data;

  if (!todos) {
    throw new Error(data.message || "bad server response: Did not recieve todo");
  }

  return todos.filter((todo) => todo.due != null).map((todo) => {
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

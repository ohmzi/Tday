import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { TodoItemType } from "@/types";
import parseApiDateTime from "@/lib/date/parseApiDateTime";

export const useCalendarTodo = (calendarRange: { start: Date; end: Date }) => {
  const {
    data: todos = [],
    isLoading: todoLoading,
  } = useQuery<TodoItemType[]>({
    queryKey: [
      "calendarTodo",
      calendarRange.start.getTime(),
      calendarRange.end.getTime(),
    ],
    staleTime: 5 * 60 * 1000,
    retry: 2,
    queryFn: async () => {
      const data = await api.GET({
        url: `/api/todo?start=${calendarRange.start.getTime()}&end=${calendarRange.end.getTime()}`,
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
          ? parseApiDateTime(todo.instanceDate)
          : null;
        const todoInstanceDateTime = todoInstanceDate?.getTime();
        const todoId = `${todo.id}:${todoInstanceDateTime}`;
        return {
          ...todo,
          id: todoId,
          due: parseApiDateTime(todo.due),
          instanceDate: todoInstanceDate,
          listID: todo.listID ?? null,
          instances:
            todo.instances?.map((instance) => ({
              ...instance,
              instanceDate: parseApiDateTime(instance.instanceDate),
              overriddenDue: instance.overriddenDue
                ? parseApiDateTime(instance.overriddenDue)
                : null,
            })) || null,
        };
      });

      return todoWithFormattedDates;
    },
  });

  return { todos, todoLoading };
};

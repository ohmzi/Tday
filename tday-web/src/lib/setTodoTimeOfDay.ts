import { fromZonedTime, toZonedTime } from "date-fns-tz";
import { TodoItemType } from "@/types";

type TodoTimeMove = Pick<TodoItemType, "due">;

/**
 * Keep the task's calendar date, set its time-of-day to `hour`:00 in the user's
 * timezone. The inverse of `moveTodoToDay` (which keeps the time and changes the
 * date). Used by the Today screen's drag-between-time-buckets interaction.
 */
export function setTodoTimeOfDay(
  todo: Pick<TodoItemType, "due">,
  hour: number,
  timeZone?: string,
): TodoTimeMove {
  const resolvedTimeZone = timeZone || "UTC";
  const dueInTimeZone = toZonedTime(todo.due, resolvedTimeZone);

  const nextDue = fromZonedTime(
    new Date(
      dueInTimeZone.getFullYear(),
      dueInTimeZone.getMonth(),
      dueInTimeZone.getDate(),
      hour,
      0,
      0,
      0,
    ),
    resolvedTimeZone,
  );

  return { due: nextDue };
}

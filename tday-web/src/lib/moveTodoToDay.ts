import { fromZonedTime, toZonedTime } from "date-fns-tz";
import { TodoItemType } from "@/types";

type TodoDayMove = Pick<TodoItemType, "due">;

export function moveTodoToDay(
  todo: Pick<TodoItemType, "due">,
  targetDayKey: string,
  timeZone?: string,
): TodoDayMove {
  const resolvedTimeZone = timeZone || "UTC";
  const [year, month, day] = targetDayKey.split("-").map(Number);
  const dueInTimeZone = toZonedTime(todo.due, resolvedTimeZone);

  const nextDue = fromZonedTime(
    new Date(
      year,
      month - 1,
      day,
      dueInTimeZone.getHours(),
      dueInTimeZone.getMinutes(),
      dueInTimeZone.getSeconds(),
      dueInTimeZone.getMilliseconds(),
    ),
    resolvedTimeZone,
  );

  return { due: nextDue };
}

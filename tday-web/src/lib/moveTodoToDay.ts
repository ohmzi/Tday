import { fromZonedTime, toZonedTime } from "date-fns-tz";
import { TodoItemType } from "@/types";

type TodoDayMove = Pick<TodoItemType, "dtstart" | "due" | "durationMinutes">;

export function moveTodoToDay(
  todo: Pick<TodoItemType, "dtstart" | "due" | "durationMinutes">,
  targetDayKey: string,
  timeZone?: string,
): TodoDayMove {
  const resolvedTimeZone = timeZone || "UTC";
  const [year, month, day] = targetDayKey.split("-").map(Number);
  const dueInTimeZone = toZonedTime(todo.due, resolvedTimeZone);
  const safeDurationMinutes = Math.max(
    todo.durationMinutes,
    Math.round((todo.due.getTime() - todo.dtstart.getTime()) / (1000 * 60)),
  );

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
  const nextStart = new Date(
    nextDue.getTime() - safeDurationMinutes * 60 * 1000,
  );

  return {
    dtstart: nextStart,
    due: nextDue,
    durationMinutes: safeDurationMinutes,
  };
}

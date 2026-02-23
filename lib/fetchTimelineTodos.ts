import { prisma } from "@/lib/prisma/client";
import generateTodosFromRRule from "@/lib/generateTodosFromRRule";
import { getMovedInstances } from "@/lib/getMovedInstances";
import { overrideBy } from "@/lib/overrideBy";
import { recurringTodoItemType, TodoItemType } from "@/types";
import { add, endOfDay, startOfDay } from "date-fns";
import { fromZonedTime, toZonedTime } from "date-fns-tz";

type FetchTimelineTodosParams = {
  userId: string;
  timeZone: string;
  recurringFutureDays?: number;
  now?: Date;
};

function clampRecurringFutureDays(days: number): number {
  if (!Number.isFinite(days)) return 365;
  return Math.min(Math.max(Math.floor(days), 1), 3650);
}

export async function fetchTimelineTodosForUser({
  userId,
  timeZone,
  recurringFutureDays = 365,
  now = new Date(),
}: FetchTimelineTodosParams): Promise<TodoItemType[]> {
  const clampedFutureDays = clampRecurringFutureDays(recurringFutureDays);
  const userNow = toZonedTime(now, timeZone);

  const earliestRecurringParent = await prisma.todo.findFirst({
    where: {
      userID: userId,
      rrule: { not: null },
      completed: false,
    },
    orderBy: { dtstart: "asc" },
    select: { dtstart: true },
  });

  const recurringRangeStart = fromZonedTime(
    earliestRecurringParent
      ? toZonedTime(earliestRecurringParent.dtstart, timeZone)
      : startOfDay(userNow),
    timeZone,
  );
  const recurringRangeEnd = fromZonedTime(
    add(endOfDay(userNow), { days: clampedFutureDays }),
    timeZone,
  );

  const oneOffTodos = await prisma.todo.findMany({
    where: {
      userID: userId,
      rrule: null,
      completed: false,
    },
    orderBy: [{ due: "asc" }, { order: "asc" }, { createdAt: "asc" }],
  });

  const recurringParents = (await prisma.todo.findMany({
    where: {
      userID: userId,
      rrule: { not: null },
      dtstart: { lte: recurringRangeEnd },
      completed: false,
    },
    include: { instances: true },
  })) as recurringTodoItemType[];

  const ghostTodos = generateTodosFromRRule(recurringParents, timeZone, {
    dateRangeStart: recurringRangeStart,
    dateRangeEnd: recurringRangeEnd,
  });
  const mergedUsingRecurrId = overrideBy(ghostTodos, (inst) => inst.recurId);
  const movedTodos = getMovedInstances(mergedUsingRecurrId, recurringParents, {
    dateRangeStart: recurringRangeStart,
    dateRangeEnd: recurringRangeEnd,
  });

  const allGhosts = [...mergedUsingRecurrId, ...movedTodos].filter((todo) => {
    return todo.due >= recurringRangeStart && todo.completed === false;
  });

  const allTodos = [...oneOffTodos, ...allGhosts].sort((a, b) => {
    const dueDelta = a.due.getTime() - b.due.getTime();
    if (dueDelta !== 0) {
      return dueDelta;
    }
    if (a.pinned !== b.pinned) {
      return a.pinned ? -1 : 1;
    }
    const orderDelta = a.order - b.order;
    if (orderDelta !== 0) {
      return orderDelta;
    }
    return a.createdAt.getTime() - b.createdAt.getTime();
  });

  return allTodos;
}

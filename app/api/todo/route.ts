import { NextRequest, NextResponse } from "next/server";
import { Priority } from "@prisma/client";
import {
  BaseServerError,
  UnauthorizedError,
  BadRequestError,
  InternalError,
} from "@/lib/customError";
import { prisma } from "@/lib/prisma/client";
import { todoSchema } from "@/schema";
import { auth } from "@/app/auth";
import generateTodosFromRRule from "@/lib/generateTodosFromRRule";
import { resolveTimezone } from "@/lib/resolveTimeZone";
import { errorHandler } from "@/lib/errorHandler";
import { overrideBy } from "@/lib/overrideBy";
import { recurringTodoItemType } from "@/types";
import { getMovedInstances } from "@/lib/getMovedInstances";
import { add, endOfDay, startOfDay } from "date-fns";
import { fromZonedTime, toZonedTime } from "date-fns-tz";

export async function POST(req: NextRequest) {
  try {
    //throw new Error("expected error happened");
    const session = await auth();
    const user = session?.user;

    if (!user?.id)
      throw new UnauthorizedError("you must be logged in to do this");

    //validate req body
    let body = await req.json();

    body = {
      ...body,
      dtstart: new Date(body.dtstart),
      due: new Date(body.due),
    };

    const parsedObj = todoSchema.safeParse(body);
    if (!parsedObj.success) throw new BadRequestError();

    const { title, description, priority, dtstart, due, rrule, listID } =
      parsedObj.data;
    //create todo
    const todo = await prisma.todo.create({
      data: {
        userID: user.id,
        title,
        description,
        priority: priority as Priority,
        dtstart,
        due,
        rrule,
        listID,
        durationMinutes: (due?.getTime() - dtstart?.getTime()) / (1000 * 60),
      },
    });
    if (!todo) throw new InternalError("todo cannot be created at this time");
    // console.log(todo);

    return NextResponse.json(
      {
        message: "todo created",
        todo,
      },
      { status: 200 },
    );
  } catch (error) {
    console.log(error);

    //handle custom error
    if (error instanceof BaseServerError) {
      return NextResponse.json(
        { message: error.message },
        { status: error.status },
      );
    }

    //handle generic error
    return NextResponse.json(
      {
        message:
          error instanceof Error
            ? error.message.slice(0, 50)
            : "an unexpected error occured",
      },
      { status: 500 },
    );
  }
}

export async function GET(req: NextRequest) {
  try {
    const session = await auth();
    const user = session?.user;

    if (!user?.id) {
      throw new UnauthorizedError("You must be logged in to do this");
    }
    const timeZone = await resolveTimezone(user, req);
    const timelineMode = req.nextUrl.searchParams.get("timeline") === "true";

    if (timelineMode) {
      const recurringFutureDaysRaw = Number(
        req.nextUrl.searchParams.get("recurringFutureDays") ?? 365,
      );

      const recurringFutureDays = Number.isFinite(recurringFutureDaysRaw)
        ? Math.min(Math.max(Math.floor(recurringFutureDaysRaw), 1), 3650)
        : 365;

      const userNow = toZonedTime(new Date(), timeZone);
      const earliestRecurringParent = await prisma.todo.findFirst({
        where: {
          userID: user.id,
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
        add(endOfDay(userNow), { days: recurringFutureDays }),
        timeZone,
      );

      const oneOffTodos = await prisma.todo.findMany({
        where: {
          userID: user.id,
          rrule: null,
          completed: false,
        },
        orderBy: [{ due: "asc" }, { order: "asc" }, { createdAt: "asc" }],
      });

      const recurringParents = (await prisma.todo.findMany({
        where: {
          userID: user.id,
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

      return NextResponse.json(
        { todos: allTodos },
        {
          status: 200,
        },
      );
    }

    const start = req.nextUrl.searchParams.get("start");
    const end = req.nextUrl.searchParams.get("end");

    if (!start || !end)
      throw new BadRequestError("date range start or from not specified");
    const dateRangeStart = new Date(Number(start));
    const dateRangeEnd = new Date(Number(end));

    // Fetch One-Off Todos scheduled for today
    const oneOffTodos = await prisma.todo.findMany({
      where: {
        userID: user.id,
        rrule: null,
        completed: false,
        due: {
          gte: dateRangeStart,
        },
        dtstart: {
          lte: dateRangeEnd,
        },
      },
      orderBy: { createdAt: "desc" },
    });

    // Fetch all Recurring todos that have already started
    const recurringParents = (await prisma.todo.findMany({
      where: {
        userID: user.id,
        rrule: { not: null },
        dtstart: { lte: dateRangeEnd },
        completed: false,
      },
      include: { instances: true },
    })) as recurringTodoItemType[];

    // Expand RRULEs to generate occurrences happening "Today"
    const ghostTodos = generateTodosFromRRule(recurringParents, timeZone, {
      dateRangeStart,
      dateRangeEnd,
    });

    // // Apply overrides
    const mergedUsingRecurrId = overrideBy(ghostTodos, (inst) => inst.recurId);

    //find out of range overrides
    const movedTodos = getMovedInstances(
      mergedUsingRecurrId,
      recurringParents,
      { dateRangeStart, dateRangeEnd },
    );

    const allGhosts = [...mergedUsingRecurrId, ...movedTodos].filter((todo) => {
      return todo.due >= dateRangeStart && todo.completed === false;
    });
    // console.log("one off todos: : ", oneOffTodos);
    // console.log("recurring parents : ", recurringParents);
    // console.log("ghost: ", ghostTodos);
    // console.log("merged with reccur ID: ", mergedUsingRecurrId);
    // console.log("moved todos: ", movedTodos);
    const allTodos = [...oneOffTodos, ...allGhosts].sort(
      (a, b) => a.order - b.order,
    );

    return NextResponse.json(
      { todos: allTodos },
      {
        status: 200,
      },
    );
  } catch (error) {
    return errorHandler(error);
  }
}

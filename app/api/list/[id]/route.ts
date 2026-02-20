import { NextRequest, NextResponse } from "next/server";
import { UnauthorizedError, BadRequestError } from "@/lib/customError";
import { prisma } from "@/lib/prisma/client";
import { listPatchSchema } from "@/schema";
import { auth } from "@/app/auth";
import { errorHandler } from "@/lib/errorHandler";
import { Prisma } from "@prisma/client";
import generateTodosFromRRule from "@/lib/generateTodosFromRRule";
import { getMovedInstances } from "@/lib/getMovedInstances";
import { overrideBy } from "@/lib/overrideBy";
import { resolveTimezone } from "@/lib/resolveTimeZone";
import { recurringTodoItemType } from "@/types";

export async function PATCH(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    const session = await auth();
    const user = session?.user;
    if (!user?.id)
      throw new UnauthorizedError("you must be logged in to do this");
    const { id } = await params;
    if (!id) throw new BadRequestError("Invalid request, ID is required");

    const body = await req.json();
    const parsedObj = listPatchSchema.safeParse(body);

    if (!parsedObj.success) throw new BadRequestError();

    const { name, color, iconKey } = parsedObj.data;

    const data: Prisma.ListUpdateInput = {};

    if (name !== undefined) data.name = name;
    if (color !== undefined) data.color = color;
    if (iconKey !== undefined) data.iconKey = iconKey;

    if (Object.keys(data).length === 0) {
      throw new BadRequestError("No fields to update");
    }

    const list = await prisma.list.update({
      where: {
        id,
        userID: user.id,
      },
      data,
    });

    return NextResponse.json(
      { message: "list updated", list },
      { status: 200 },
    );
  } catch (error) {
    console.log(error);
    return errorHandler(error);
  }
}

export async function DELETE(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    const session = await auth();
    const user = session?.user;
    if (!user?.id)
      throw new UnauthorizedError("you must be logged in to do this");
    const { id } = await params;
    if (!id) throw new BadRequestError("Invalid request, ID is required");

    await prisma.list.delete({
      where: {
        id,
        userID: user.id,
      },
    });

    return NextResponse.json({ message: "list deleted" }, { status: 200 });
  } catch (error) {
    console.log(error);
    return errorHandler(error);
  }
}

export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    const session = await auth();
    const user = session?.user;

    if (!user?.id) {
      throw new UnauthorizedError("You must be logged in to do this");
    }
    const { id } = await params;
    if (!id) throw new BadRequestError("Invalid request, ID is required");

    const timeZone = await resolveTimezone(user, req);
    const start = req.nextUrl.searchParams.get("start");
    const end = req.nextUrl.searchParams.get("end");

    if (!start || !end)
      throw new BadRequestError("date range start or from not specified");
    const dateRangeStart = new Date(Number(start));
    const dateRangeEnd = new Date(Number(end));

    // Fetch all One-Off Todos for list
    const oneOffTodos = await prisma.todo.findMany({
      where: {
        userID: user.id,
        rrule: null,
        completed: false,
        listID: id,
      },
      orderBy: { createdAt: "desc" },
    });

    // Fetch all recurring Todos for list
    const recurringParents = (await prisma.todo.findMany({
      where: {
        userID: user.id,
        rrule: { not: null },
        completed: false,
        listID: id,
      },
      include: { instances: true },
    })) as recurringTodoItemType[];

    //Collect future reccuring todos
    const futureRecurringTodos = recurringParents.filter(({ dtstart }) => {
      return dtstart > dateRangeEnd;
    });
    //generate ghost todos from first instance occurence of future todo
    const ghostFutureTodos = futureRecurringTodos.map((todo) => {
      return { ...todo, instanceDate: todo.dtstart };
    });
    //Apply overrides
    const mergedFutureGhostTodos = overrideBy(
      ghostFutureTodos,
      (inst) => inst.recurId,
    ).sort((a, b) => {
      return a.dtstart.getTime() - b.dtstart.getTime();
    });

    // Expand RRULEs to generate occurrences happening "Today"
    const ghostTodos = generateTodosFromRRule(recurringParents, timeZone, {
      dateRangeStart,
      dateRangeEnd,
    });

    // // Apply overrides
    const mergedGhostTodos = overrideBy(ghostTodos, (inst) => inst.recurId);

    //find out of range overrides
    const movedTodos = getMovedInstances(mergedGhostTodos, recurringParents, {
      dateRangeStart,
      dateRangeEnd,
    });

    const allTodayGhosts = [...mergedGhostTodos, ...movedTodos].filter(
      (todo) => {
        return todo.due >= dateRangeStart && todo.completed === false;
      },
    );
    // console.log("one off todos: : ", oneOffTodos);
    // console.log("recurring parents : ", recurringParents);
    // console.log("ghost: ", ghostTodos);
    // console.log("merged with reccur ID: ", mergedUsingRecurrId);
    // console.log("moved todos: ", movedTodos);
    const allTodos = [
      ...oneOffTodos,
      ...mergedFutureGhostTodos,
      ...allTodayGhosts,
    ].sort((a, b) => a.order - b.order);

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

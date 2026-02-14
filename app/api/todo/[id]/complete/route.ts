import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/app/auth";
import {
  UnauthorizedError,
  BadRequestError,
  ForbiddenError,
} from "@/lib/customError";
import { prisma } from "@/lib/prisma/client";
import { Prisma } from "@prisma/client";
import { errorHandler } from "@/lib/errorHandler";

export async function PATCH(
  _req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    const session = await auth();
    const user = session?.user;
    if (!user?.id)
      throw new UnauthorizedError("you must be logged in to do this");

    const { id } = await params;
    if (!id) throw new BadRequestError("Invalid request, ID is required");

    const todo = await prisma.todo.findFirst({
      where: {
        id,
        userID: user.id,
      },
      select: {
        id: true,
        title: true,
        description: true,
        priority: true,
        dtstart: true,
        due: true,
        rrule: true,
      },
    });
    if (!todo) {
      throw new ForbiddenError("you are not allowed to modify this todo");
    }

    await prisma.todo.update({
      where: { id: todo.id, userID: user.id },
      data: { completed: true },
    });

    //insert a new completed todo record
    const currentTime = new Date();
    const completedOnTime = todo.due > currentTime;
    const daysToComplete =
      (Number(currentTime) - Number(todo.dtstart)) / (1000 * 60 * 60 * 24);

    await prisma.completedTodo.create({
      data: {
        originalTodoID: todo.id,
        title: todo.title,
        description: todo.description,
        priority: todo.priority,
        dtstart: todo.dtstart,
        due: todo.due,
        completedAt: new Date(),
        completedOnTime,
        daysToComplete: new Prisma.Decimal(daysToComplete),
        rrule: todo.rrule,
        userID: user.id,
      },
    });
    return NextResponse.json(
      { message: "todo completed successfully!" },
      { status: 200 },
    );
  } catch (error) {
    return errorHandler(error);
  }
}

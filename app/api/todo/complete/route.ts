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

function parseInstanceDate(value: unknown): Date | null {
  if (value == null) return null;
  const parsed =
    typeof value === "number" ? new Date(value) : new Date(String(value));
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

export async function PATCH(req: NextRequest) {
  try {
    const session = await auth();
    const user = session?.user;
    if (!user?.id) {
      throw new UnauthorizedError("you must be logged in to do this");
    }

    const body = (await req.json().catch(() => null)) as
      | { id?: string; instanceDate?: string | number | Date | null }
      | null;

    const id = body?.id?.trim();
    if (!id) {
      throw new BadRequestError("Invalid request, ID is required");
    }

    const instanceDate = parseInstanceDate(body?.instanceDate);
    if (body?.instanceDate != null && !instanceDate) {
      throw new BadRequestError(
        "invalid instance date recieved for this recurring todo",
      );
    }

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
        list: {
          select: {
            name: true,
            color: true,
          },
        },
      },
    });
    if (!todo) {
      throw new ForbiddenError("you are not allowed to modify this todo");
    }

    let upsertedTodoInstance = null;
    if (!todo.rrule) {
      await prisma.todo.update({
        where: { id: todo.id, userID: user.id },
        data: { completed: true },
      });
    } else {
      if (!instanceDate) {
        throw new BadRequestError(
          "invalid instance date recieved for this recurring todo",
        );
      }

      upsertedTodoInstance = await prisma.todoInstance.upsert({
        where: {
          todoId_instanceDate: {
            todoId: todo.id,
            instanceDate,
          },
        },
        update: { completedAt: new Date() },
        create: {
          todoId: todo.id,
          recurId: instanceDate.toISOString(),
          instanceDate,
          completedAt: new Date(),
        },
      });
    }

    const currentTime = new Date();
    let completedOnTime = todo.due > currentTime;
    let daysToComplete =
      (Number(currentTime) - Number(todo.dtstart)) / (1000 * 60 * 60 * 24);

    if (upsertedTodoInstance?.overriddenDue) {
      completedOnTime = upsertedTodoInstance.overriddenDue > currentTime;
    }
    if (upsertedTodoInstance?.overriddenDtstart) {
      daysToComplete =
        (Number(currentTime) - Number(upsertedTodoInstance.overriddenDtstart)) /
        (1000 * 60 * 60 * 24);
    }

    await prisma.completedTodo.create({
      data: {
        originalTodoID: todo.id,
        title: upsertedTodoInstance?.overriddenTitle || todo.title,
        description:
          upsertedTodoInstance?.overriddenDescription || todo.description,
        priority: upsertedTodoInstance?.overriddenPriority || todo.priority,
        dtstart: upsertedTodoInstance?.overriddenDtstart || todo.dtstart,
        due: upsertedTodoInstance?.overriddenDue || todo.due,
        completedAt: new Date(),
        completedOnTime,
        daysToComplete: new Prisma.Decimal(daysToComplete),
        rrule: todo.rrule,
        userID: user.id,
        instanceDate: instanceDate ?? null,
        listName: todo.list?.name ?? null,
        listColor: todo.list?.color ?? null,
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

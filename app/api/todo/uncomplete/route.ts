import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/app/auth";
import {
  UnauthorizedError,
  BadRequestError,
  ForbiddenError,
} from "@/lib/customError";
import { prisma } from "@/lib/prisma/client";
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
        rrule: true,
      },
    });
    if (!todo) {
      throw new ForbiddenError("you are not allowed to modify this todo");
    }

    if (!todo.rrule || !instanceDate) {
      await prisma.todo.update({
        where: { id: todo.id, userID: user.id },
        data: { completed: false },
      });

      await prisma.completedTodo.deleteMany({
        where: {
          originalTodoID: todo.id,
          userID: user.id,
        },
      });
    } else {
      await prisma.todoInstance.updateMany({
        where: {
          todoId: todo.id,
          instanceDate,
        },
        data: { completedAt: null },
      });

      await prisma.completedTodo.deleteMany({
        where: {
          originalTodoID: todo.id,
          userID: user.id,
          instanceDate,
        },
      });
    }

    return NextResponse.json(
      { message: "todo completed successfully!" },
      { status: 200 },
    );
  } catch (error) {
    return errorHandler(error);
  }
}

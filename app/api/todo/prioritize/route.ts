import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/app/auth";
import { UnauthorizedError, BadRequestError, ForbiddenError } from "@/lib/customError";
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
      throw new UnauthorizedError("You must be logged in to do this");
    }

    const body = (await req.json().catch(() => null)) as
      | {
          id?: string;
          priority?: "Low" | "Medium" | "High" | null;
          instanceDate?: string | number | Date | null;
        }
      | null;

    const id = body?.id?.trim();
    const priority = body?.priority;
    if (!id) {
      throw new BadRequestError("Invalid request, ID is required");
    }
    if (!priority || !["Low", "Medium", "High"].includes(priority)) {
      throw new BadRequestError("invalid priority value or instance date");
    }

    const instanceDate = parseInstanceDate(body?.instanceDate);
    if (body?.instanceDate != null && !instanceDate) {
      throw new BadRequestError("invalid priority value or instance date");
    }

    const ownedTodo = await prisma.todo.findFirst({
      where: {
        id,
        userID: user.id,
      },
      select: { id: true },
    });
    if (!ownedTodo) {
      throw new ForbiddenError("you are not allowed to modify this todo");
    }

    if (instanceDate) {
      await prisma.todoInstance.upsert({
        where: {
          todoId_instanceDate: {
            todoId: ownedTodo.id,
            instanceDate,
          },
        },
        update: {
          overriddenPriority: priority,
        },
        create: {
          todoId: ownedTodo.id,
          instanceDate,
          recurId: instanceDate.toISOString(),
          overriddenPriority: priority,
        },
      });
    } else {
      await prisma.todo.update({
        where: {
          id: ownedTodo.id,
          userID: user.id,
        },
        data: {
          priority,
        },
      });
    }

    return NextResponse.json({ message: "priority updated" }, { status: 200 });
  } catch (error) {
    return errorHandler(error);
  }
}

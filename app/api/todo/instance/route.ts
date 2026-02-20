import { BadRequestError, ForbiddenError, UnauthorizedError } from "@/lib/customError";
import { prisma } from "@/lib/prisma/client";
import { NextRequest, NextResponse } from "next/server";
import { Priority } from "@prisma/client";
import { todoSchema } from "@/schema";
import { auth } from "@/app/auth";
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

    const rawBody = (await req.json().catch(() => null)) as
      | Record<string, unknown>
      | null;
    const id = typeof rawBody?.id === "string" ? rawBody.id.trim() : "";
    if (!id) {
      throw new BadRequestError("Invalid request, ID is required");
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

    const instanceDate = parseInstanceDate(rawBody?.instanceDate);
    if (!instanceDate) {
      throw new BadRequestError(
        "instanceDate is required to update a TodoInstance",
      );
    }

    const normalized = {
      ...rawBody,
      dtstart: rawBody?.dtstart ? new Date(String(rawBody.dtstart)) : undefined,
      due: rawBody?.due ? new Date(String(rawBody.due)) : undefined,
      instanceDate,
    };

    const parsedObj = todoSchema.partial().safeParse(normalized);
    if (!parsedObj.success) {
      throw new BadRequestError("Invalid request body");
    }

    const { title, description, priority, dtstart, due } = parsedObj.data;
    if (!dtstart) {
      throw new BadRequestError("dtstart is required to update a TodoInstance");
    }

    await prisma.todoInstance.upsert({
      where: {
        todoId_instanceDate: {
          todoId: ownedTodo.id,
          instanceDate,
        },
      },
      update: {
        overriddenTitle: title,
        overriddenDescription: description,
        overriddenPriority: priority as Priority,
        overriddenDtstart: dtstart,
        overriddenDue: due,
        overriddenDurationMinutes:
          dtstart && due
            ? (due.getTime() - dtstart.getTime()) / (1000 * 60)
            : undefined,
      },
      create: {
        todoId: ownedTodo.id,
        recurId: instanceDate.toISOString(),
        instanceDate,
        overriddenTitle: title,
        overriddenDescription: description,
        overriddenPriority: priority,
        overriddenDtstart: dtstart,
        overriddenDue: due,
        overriddenDurationMinutes:
          dtstart && due
            ? (due.getTime() - dtstart.getTime()) / (1000 * 60)
            : undefined,
      },
    });

    return NextResponse.json({ message: "Todo updated" }, { status: 200 });
  } catch (error) {
    return errorHandler(error);
  }
}

export async function DELETE(req: NextRequest) {
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
    const instanceDate = parseInstanceDate(body?.instanceDate);

    if (!id || !instanceDate) {
      throw new BadRequestError(
        "Invalid request, ID or instanceDate is required to do instance delete!",
      );
    }

    const ownedTodo = await prisma.todo.findFirst({
      where: {
        id,
        userID: user.id,
      },
      select: { id: true, userID: true },
    });
    if (!ownedTodo) {
      throw new ForbiddenError("you are not allowed to modify this todo");
    }

    await prisma.todo.update({
      where: { id: ownedTodo.id, userID: ownedTodo.userID },
      data: { exdates: { push: [instanceDate] } },
    });

    return NextResponse.json({ message: "todo deleted" }, { status: 200 });
  } catch (error) {
    return errorHandler(error);
  }
}

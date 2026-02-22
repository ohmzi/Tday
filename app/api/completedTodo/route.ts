import { NextRequest, NextResponse } from "next/server";
import {
  BaseServerError,
  BadRequestError,
  UnauthorizedError,
  ForbiddenError,
} from "@/lib/customError";
import { prisma } from "@/lib/prisma/client";
import { auth } from "@/app/auth";
import { Priority } from "@prisma/client";
import { z } from "zod";

const completedPatchSchema = z.object({
  id: z
    .string({ message: "completed todo id is required" })
    .trim()
    .min(1, { message: "completed todo id is required" }),
  title: z.string().trim().min(1).optional(),
  description: z.string().nullable().optional(),
  priority: z.nativeEnum(Priority).optional(),
  dtstart: z.date().optional(),
  due: z.date().optional(),
  rrule: z.string().nullable().optional(),
  listID: z.string().trim().min(1).nullable().optional(),
});

const completedDeleteSchema = z.object({
  id: z
    .string({ message: "completed todo id is required" })
    .trim()
    .min(1, { message: "completed todo id is required" }),
});

export async function GET() {
  try {
    const session = await auth();
    const user = session?.user;

    if (!user?.id)
      throw new UnauthorizedError("you must be logged in to do this");

    //get completed todos
    const completedTodos = await prisma.completedTodo.findMany({
      where: { userID: user.id },
      orderBy: { dtstart: "desc" },
    });

    // Backfill list metadata for older completed rows where list snapshot
    // was not persisted at completion time.
    const missingOriginalTodoIds = [
      ...new Set(
        completedTodos
          .filter(
            (item) =>
              (!item.listName || !item.listColor) && !!item.originalTodoID,
          )
          .map((item) => item.originalTodoID),
      ),
    ];
    let mergedCompletedTodos = completedTodos;
    if (missingOriginalTodoIds.length > 0) {
      const originalTodos = await prisma.todo.findMany({
        where: {
          userID: user.id,
          id: { in: missingOriginalTodoIds },
        },
        select: {
          id: true,
          list: {
            select: {
              name: true,
              color: true,
            },
          },
        },
      });
      const originalTodoById = new Map(
        originalTodos.map((todo) => [todo.id, todo]),
      );
      mergedCompletedTodos = completedTodos.map((item) => {
        if (item.listName && item.listColor) return item;
        const originalTodo = item.originalTodoID
          ? originalTodoById.get(item.originalTodoID)
          : null;
        if (!originalTodo?.list) return item;
        return {
          ...item,
          listName: item.listName ?? originalTodo.list.name,
          listColor: item.listColor ?? originalTodo.list.color,
        };
      });
    }

    return NextResponse.json(
      { completedTodos: mergedCompletedTodos },
      {
        status: 200,
      },
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

export async function PATCH(req: NextRequest) {
  try {
    const session = await auth();
    const user = session?.user;
    if (!user?.id) {
      throw new UnauthorizedError("you must be logged in to do this");
    }

    const rawBody = await req.json().catch(() => ({}));
    const parsed = completedPatchSchema.safeParse({
      ...rawBody,
      dtstart: rawBody?.dtstart ? new Date(rawBody.dtstart) : undefined,
      due: rawBody?.due ? new Date(rawBody.due) : undefined,
    });
    if (!parsed.success) {
      throw new BadRequestError("Invalid request body");
    }

    const {
      id,
      title,
      description,
      priority,
      dtstart,
      due,
      rrule,
      listID,
    } = parsed.data;

    const completed = await prisma.completedTodo.findFirst({
      where: { id, userID: user.id },
      select: { id: true, originalTodoID: true },
    });
    if (!completed) {
      throw new ForbiddenError("you are not allowed to modify this todo");
    }

    let listName: string | null | undefined = undefined;
    let listColor: string | null | undefined = undefined;
    if (Object.prototype.hasOwnProperty.call(parsed.data, "listID")) {
      if (listID === null) {
        listName = null;
        listColor = null;
      } else if (listID) {
        const list = await prisma.list.findFirst({
          where: { id: listID, userID: user.id },
          select: { name: true, color: true },
        });
        if (!list) {
          throw new BadRequestError("List does not exist");
        }
        listName = list.name;
        listColor = list.color ?? null;
      }
    }

    await prisma.$transaction(async (tx) => {
      await tx.completedTodo.updateMany({
        where: { id: completed.id, userID: user.id },
        data: {
          title,
          description,
          priority,
          dtstart,
          due,
          rrule,
          listName,
          listColor,
        },
      });

      await tx.todo.updateMany({
        where: { id: completed.originalTodoID, userID: user.id },
        data: {
          title,
          description,
          priority,
          dtstart,
          due,
          rrule,
          listID:
            Object.prototype.hasOwnProperty.call(parsed.data, "listID")
              ? listID
              : undefined,
        },
      });
    });

    return NextResponse.json({ message: "completed todo updated" }, { status: 200 });
  } catch (error) {
    if (error instanceof BaseServerError) {
      return NextResponse.json(
        { message: error.message },
        { status: error.status },
      );
    }

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

export async function DELETE(req: NextRequest) {
  try {
    const session = await auth();
    const user = session?.user;
    if (!user?.id) {
      throw new UnauthorizedError("you must be logged in to do this");
    }

    const rawBody = await req.json().catch(() => ({}));
    const parsed = completedDeleteSchema.safeParse(rawBody);
    if (!parsed.success) {
      throw new BadRequestError("Invalid request body");
    }

    const { id } = parsed.data;
    const completed = await prisma.completedTodo.findFirst({
      where: { id, userID: user.id },
      select: { id: true, originalTodoID: true },
    });

    if (!completed) {
      return NextResponse.json(
        { message: "completed todo already deleted" },
        { status: 200 },
      );
    }

    await prisma.$transaction(async (tx) => {
      await tx.completedTodo.deleteMany({
        where: { id: completed.id, userID: user.id },
      });

      await tx.todo.deleteMany({
        where: { id: completed.originalTodoID, userID: user.id },
      });

      await tx.completedTodo.deleteMany({
        where: { originalTodoID: completed.originalTodoID, userID: user.id },
      });
    });

    return NextResponse.json({ message: "completed todo deleted" }, { status: 200 });
  } catch (error) {
    if (error instanceof BaseServerError) {
      return NextResponse.json(
        { message: error.message },
        { status: error.status },
      );
    }

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

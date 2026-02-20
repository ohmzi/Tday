import { NextRequest, NextResponse } from "next/server";
import {
  BaseServerError,
  UnauthorizedError,
  BadRequestError,
  InternalError,
} from "@/lib/customError";
import { prisma } from "@/lib/prisma/client";
import { listCreateSchema, listPatchSchema } from "@/schema";
import { auth } from "@/app/auth";
import { errorHandler } from "@/lib/errorHandler";
import { z } from "zod";

const listPatchByIdSchema = listPatchSchema.extend({
  id: z
    .string({ message: "id cannot be left empty" })
    .trim()
    .min(1, { message: "id cannot be left empty" }),
});

const listDeleteByIdSchema = z.object({
  id: z
    .string({ message: "id cannot be left empty" })
    .trim()
    .min(1, { message: "id cannot be left empty" }),
});

export async function POST(req: NextRequest) {
  try {
    const session = await auth();
    const user = session?.user;
    if (!user?.id)
      throw new UnauthorizedError("you must be logged in to do this");

    //validate req body
    const body = await req.json();
    const parsedObj = listCreateSchema.safeParse(body);
    if (!parsedObj.success) throw new BadRequestError();

    const { name, color, iconKey } = parsedObj.data;
    const list = await prisma.list.create({
      data: { name, color, iconKey, userID: user.id },
    });
    if (!list)
      throw new InternalError("note cannot be created at this time");

    return NextResponse.json(
      { message: "list created", list },
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

export async function GET() {
  try {
    const session = await auth();
    const user = session?.user;
    if (!user?.id)
      throw new UnauthorizedError("you must be logged in to do this");

    const listRows = await prisma.list.findMany({
      where: { userID: user.id },
      orderBy: { createdAt: "desc" },
      select: {
        id: true,
        name: true,
        createdAt: true,
        updatedAt: true,
        color: true,
        iconKey: true,
        _count: { select: { todos: true } },
      },
    });
    if (!listRows)
      throw new InternalError("list cannot be found at this time");

    const lists = listRows.map(({ _count, ...list }) => ({
      ...list,
      todoCount: _count.todos,
    }));

    return NextResponse.json(
      {
        lists,
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

export async function PATCH(req: NextRequest) {
  try {
    const session = await auth();
    const user = session?.user;
    if (!user?.id) {
      throw new UnauthorizedError("you must be logged in to do this");
    }

    const body = await req.json();
    const parsedObj = listPatchByIdSchema.safeParse(body);
    if (!parsedObj.success) {
      throw new BadRequestError("Invalid request body");
    }

    const { id, name, color, iconKey } = parsedObj.data;
    const list = await prisma.list.update({
      where: {
        id,
        userID: user.id,
      },
      data: {
        ...(name !== undefined ? { name } : {}),
        ...(color !== undefined ? { color } : {}),
        ...(iconKey !== undefined ? { iconKey } : {}),
      },
    });

    return NextResponse.json(
      { message: "list updated", list },
      { status: 200 },
    );
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

    const body = await req.json().catch(() => ({}));
    const parsedObj = listDeleteByIdSchema.safeParse(body);
    if (!parsedObj.success) {
      throw new BadRequestError("Invalid request body");
    }

    await prisma.list.delete({
      where: {
        id: parsedObj.data.id,
        userID: user.id,
      },
    });

    return NextResponse.json({ message: "list deleted" }, { status: 200 });
  } catch (error) {
    return errorHandler(error);
  }
}

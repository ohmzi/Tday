import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/app/auth";
import {
  UnauthorizedError,
  BadRequestError,
  ForbiddenError,
} from "@/lib/customError";
import { prisma } from "@/lib/prisma/client";
import { errorHandler } from "@/lib/errorHandler";

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

    const body = (await req.json()) as { instanceDate?: string | Date | null };
    const instanceDate = body.instanceDate ? new Date(body.instanceDate) : null;

    if (!instanceDate || Number.isNaN(instanceDate.getTime()))
      throw new BadRequestError(
        "invalid instance date recieved for this recurring todo",
      );

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

    //update the overriding instance with completedAt
    await prisma.todoInstance.update({
      where: {
        todoId_instanceDate: {
          todoId: ownedTodo.id,
          instanceDate,
        },
      },
      data: { completedAt: null },
    });

    await prisma.completedTodo.deleteMany({
      where: {
        originalTodoID: ownedTodo.id,
        userID: user.id,
        instanceDate,
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

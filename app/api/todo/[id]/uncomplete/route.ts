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
      select: { id: true },
    });
    if (!todo) {
      throw new ForbiddenError("you are not allowed to modify this todo");
    }

    await prisma.todo.update({
      where: { id: todo.id, userID: user.id },
      data: { completed: false },
    });

    //delete the completed todo record
    await prisma.completedTodo.deleteMany({
      where: {
        originalTodoID: todo.id,
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

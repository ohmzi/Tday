import { auth } from "@/app/auth";
import { UnauthorizedError, BaseServerError, BadRequestError } from "@/lib/customError";
import { prisma } from "@/lib/prisma/client";
import { NextRequest, NextResponse } from "next/server";

export async function PATCH(req: NextRequest) {
  try {
    const session = await auth();
    const user = session?.user;
    if (!user?.id) throw new UnauthorizedError("You must be logged in");

    const { name } = await req.json();

    if (typeof name !== "string") {
      throw new BadRequestError("Name must be a string");
    }

    const trimmed = name.trim();
    if (trimmed.length === 0 || trimmed.length > 100) {
      throw new BadRequestError("Name must be between 1 and 100 characters");
    }

    const updated = await prisma.user.update({
      where: { id: user.id },
      data: { name: trimmed },
      select: { id: true, name: true, email: true },
    });

    return NextResponse.json(updated, { status: 200 });
  } catch (error) {
    if (error instanceof BaseServerError) {
      return NextResponse.json({ message: error.message }, { status: error.status });
    }
    return NextResponse.json(
      { message: "An unexpected error occurred" },
      { status: 500 },
    );
  }
}

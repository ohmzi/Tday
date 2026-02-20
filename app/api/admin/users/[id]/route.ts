import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma/client";
import {
  BadRequestError,
  ForbiddenError,
  NotFoundError,
} from "@/lib/customError";
import { errorHandler } from "@/lib/errorHandler";
import { requireAdmin } from "@/lib/auth/requireAdmin";

export async function PATCH(
  _req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    const admin = await requireAdmin();
    const { id } = await params;

    if (!id) {
      throw new BadRequestError("user id is required");
    }

    const targetUser = await prisma.user.findUnique({
      where: { id },
      select: {
        id: true,
        approvalStatus: true,
      },
    });

    if (!targetUser) {
      throw new NotFoundError("user not found");
    }

    if (targetUser.approvalStatus === "APPROVED") {
      return NextResponse.json(
        { message: "user is already approved" },
        { status: 200 },
      );
    }

    await prisma.user.update({
      where: { id: targetUser.id },
      data: {
        approvalStatus: "APPROVED",
        approvedAt: new Date(),
        approvedById: admin.id,
      },
    });

    return NextResponse.json({ message: "user approved" }, { status: 200 });
  } catch (error) {
    return errorHandler(error);
  }
}

export async function DELETE(
  _req: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    const admin = await requireAdmin();
    const { id } = await params;

    if (!id) {
      throw new BadRequestError("user id is required");
    }

    if (id === admin.id) {
      throw new BadRequestError("you cannot delete your own account");
    }

    const targetUser = await prisma.user.findUnique({
      where: { id },
      select: {
        id: true,
        role: true,
      },
    });

    if (!targetUser) {
      throw new NotFoundError("user not found");
    }

    if (targetUser.role === "ADMIN") {
      const otherAdminCount = await prisma.user.count({
        where: {
          role: "ADMIN",
          id: { not: targetUser.id },
        },
      });

      if (otherAdminCount === 0) {
        throw new ForbiddenError("you cannot delete the last admin account");
      }
    }

    await prisma.$transaction(async (tx) => {
      await tx.completedTodo.deleteMany({ where: { userID: targetUser.id } });
      await tx.note.deleteMany({ where: { userID: targetUser.id } });
      await tx.file.deleteMany({ where: { userID: targetUser.id } });
      await tx.todo.deleteMany({ where: { userID: targetUser.id } });
      await tx.list.deleteMany({ where: { userID: targetUser.id } });
      await tx.userPreferences.deleteMany({ where: { userID: targetUser.id } });
      await tx.user.delete({ where: { id: targetUser.id } });
    });

    return NextResponse.json({ message: "user deleted" }, { status: 200 });
  } catch (error) {
    return errorHandler(error);
  }
}

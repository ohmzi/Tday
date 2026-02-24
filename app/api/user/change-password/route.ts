import { auth } from "@/app/auth";
import {
  UnauthorizedError,
  BaseServerError,
  BadRequestError,
} from "@/lib/customError";
import { prisma } from "@/lib/prisma/client";
import { NextRequest, NextResponse } from "next/server";
import { hashPassword, verifyPassword } from "@/lib/security/password";
import { revokeUserSessions } from "@/lib/security/sessionControl";

const STRONG_PASSWORD_REGEX = /^(?=.*[A-Z])(?=.*[\W_]).{8,}$/;

export async function POST(req: NextRequest) {
  try {
    const session = await auth();
    const user = session?.user;
    if (!user?.id) throw new UnauthorizedError("You must be logged in");

    const { currentPassword, newPassword } = await req.json();

    if (typeof currentPassword !== "string" || typeof newPassword !== "string") {
      throw new BadRequestError("Current and new passwords are required");
    }

    if (!STRONG_PASSWORD_REGEX.test(newPassword)) {
      throw new BadRequestError(
        "New password must be at least 8 characters and include one uppercase and one special character",
      );
    }

    // Fetch user with password
    const dbUser = await prisma.user.findUnique({
      where: { id: user.id },
      select: { password: true },
    });

    if (!dbUser?.password) {
      throw new BadRequestError("No password set for this account");
    }

    const verification = verifyPassword(currentPassword, dbUser.password);
    if (!verification.valid) {
      throw new BadRequestError("Current password is incorrect");
    }

    await prisma.user.update({
      where: { id: user.id },
      data: { password: hashPassword(newPassword) },
    });
    await revokeUserSessions(user.id);

    return NextResponse.json({ message: "Password changed successfully" }, { status: 200 });
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

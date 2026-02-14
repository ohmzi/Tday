import { auth } from "@/app/auth";
import {
  UnauthorizedError,
  BaseServerError,
  BadRequestError,
} from "@/lib/customError";
import { prisma } from "@/lib/prisma/client";
import { NextRequest, NextResponse } from "next/server";
import { sha256 } from "@noble/hashes/sha256";
import { pbkdf2 } from "@noble/hashes/pbkdf2";
import { hexToBytes, bytesToHex, randomBytes } from "@noble/hashes/utils";

export async function POST(req: NextRequest) {
  try {
    const session = await auth();
    const user = session?.user;
    if (!user?.id) throw new UnauthorizedError("You must be logged in");

    const { currentPassword, newPassword } = await req.json();

    if (!currentPassword || !newPassword) {
      throw new BadRequestError("Current and new passwords are required");
    }

    if (typeof newPassword !== "string" || newPassword.length < 8) {
      throw new BadRequestError("New password must be at least 8 characters");
    }

    // Fetch user with password
    const dbUser = await prisma.user.findUnique({
      where: { id: user.id },
      select: { password: true },
    });

    if (!dbUser?.password) {
      throw new BadRequestError("No password set for this account");
    }

    // Verify current password
    if (!dbUser.password.includes(":")) {
      throw new BadRequestError("Please reset your password to continue");
    }

    const [saltHex, storedHashHex] = dbUser.password.split(":");
    const salt = hexToBytes(saltHex);
    const calculatedHash = pbkdf2(sha256, currentPassword, salt, {
      c: 10000,
      dkLen: 32,
    });

    if (bytesToHex(calculatedHash) !== storedHashHex) {
      throw new BadRequestError("Current password is incorrect");
    }

    // Hash new password
    const newSalt = randomBytes(16);
    const newSaltHex = bytesToHex(newSalt);
    const newHash = pbkdf2(sha256, newPassword, newSalt, {
      c: 10000,
      dkLen: 32,
    });
    const newHashHex = bytesToHex(newHash);
    const hashedPassword = `${newSaltHex}:${newHashHex}`;

    await prisma.user.update({
      where: { id: user.id },
      data: { password: hashedPassword },
    });

    return NextResponse.json({ message: "Password changed successfully" }, { status: 200 });
  } catch (error) {
    if (error instanceof BaseServerError) {
      return NextResponse.json({ message: error.message }, { status: error.status });
    }
    return NextResponse.json(
      { message: error instanceof Error ? error.message : "An unexpected error occurred" },
      { status: 500 },
    );
  }
}

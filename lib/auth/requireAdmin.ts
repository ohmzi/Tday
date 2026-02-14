import { auth } from "@/app/auth";
import { ForbiddenError, UnauthorizedError } from "@/lib/customError";
import { prisma } from "@/lib/prisma/client";

export async function requireAdmin() {
  const session = await auth();
  const sessionUserId = session?.user?.id;

  if (!sessionUserId) {
    throw new UnauthorizedError("you must be logged in to do this");
  }

  const adminUser = await prisma.user.findUnique({
    where: { id: sessionUserId },
    select: {
      id: true,
      role: true,
      approvalStatus: true,
    },
  });

  if (!adminUser) {
    throw new UnauthorizedError("you must be logged in to do this");
  }

  if (adminUser.approvalStatus !== "APPROVED") {
    throw new ForbiddenError("your account is awaiting admin approval");
  }

  if (adminUser.role !== "ADMIN") {
    throw new ForbiddenError("admin access required");
  }

  return adminUser;
}

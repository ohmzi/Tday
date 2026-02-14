import { NextResponse } from "next/server";
import { prisma } from "@/lib/prisma/client";
import { errorHandler } from "@/lib/errorHandler";
import { requireAdmin } from "@/lib/auth/requireAdmin";

export async function GET() {
  try {
    await requireAdmin();

    const users = await prisma.user.findMany({
      select: {
        id: true,
        name: true,
        email: true,
        role: true,
        approvalStatus: true,
        createdAt: true,
        approvedAt: true,
      },
      orderBy: [{ approvalStatus: "desc" }, { createdAt: "desc" }],
    });

    return NextResponse.json({ users }, { status: 200 });
  } catch (error) {
    return errorHandler(error);
  }
}

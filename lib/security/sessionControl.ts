import { prisma } from "@/lib/prisma/client";

const DEFAULT_SESSION_MAX_AGE_SECONDS = 60 * 60 * 24;
const MIN_SESSION_MAX_AGE_SECONDS = 60 * 60;
const MAX_SESSION_MAX_AGE_SECONDS = 60 * 60 * 24 * 30;

export function sessionMaxAgeSeconds(): number {
  const parsed = Number.parseInt(
    process.env.AUTH_SESSION_MAX_AGE_SEC ?? "",
    10,
  );
  if (!Number.isFinite(parsed)) {
    return DEFAULT_SESSION_MAX_AGE_SECONDS;
  }
  return Math.min(
    MAX_SESSION_MAX_AGE_SECONDS,
    Math.max(MIN_SESSION_MAX_AGE_SECONDS, parsed),
  );
}

export async function revokeUserSessions(userId: string): Promise<void> {
  if (!userId) return;

  await prisma.user.update({
    where: { id: userId },
    data: {
      tokenVersion: {
        increment: 1,
      },
    },
  });
}

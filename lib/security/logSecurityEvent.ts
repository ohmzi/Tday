import { prisma } from "@/lib/prisma/client";

type SecurityEventDetails = Record<string, unknown>;

export async function logSecurityEvent(
  reasonCode: string,
  details: SecurityEventDetails = {},
) {
  const payload = {
    reasonCode,
    at: new Date().toISOString(),
    ...details,
  };

  console.warn("[security]", payload);

  const serialized = safeSerialize(payload);
  try {
    await prisma.eventLog.create({
      data: {
        eventName: reasonCode,
        capturedTime: new Date(),
        log: serialized.slice(0, 500),
      },
    });
  } catch (error) {
    console.warn("[security:eventlog_failed]", String(error));
  }
}

function safeSerialize(value: unknown): string {
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

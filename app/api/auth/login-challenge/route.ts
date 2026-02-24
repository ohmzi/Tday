import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma/client";
import {
  buildAuthThrottleResponse,
  enforceAuthRateLimit,
} from "@/lib/security/authThrottle";
import {
  issuePasswordProofChallenge,
  normalizePasswordProofEmail,
} from "@/lib/security/passwordProof";

export async function POST(request: NextRequest) {
  const email = await extractEmailFromRequest(request);
  if (!email) {
    return NextResponse.json(
      {
        message: "Email is required.",
      },
      {
        status: 400,
        headers: {
          "Cache-Control": "no-store",
        },
      },
    );
  }

  const limitResult = await enforceAuthRateLimit({
    action: "credentials",
    request,
    identifier: email,
  });
  if (!limitResult.allowed) {
    return buildAuthThrottleResponse(limitResult);
  }

  const user = await prisma.user.findUnique({
    where: { email },
    select: { password: true },
  });

  const payload = issuePasswordProofChallenge({
    email,
    storedPasswordHash: user?.password ?? null,
  });

  return NextResponse.json(payload, {
    status: 200,
    headers: {
      "Cache-Control": "no-store",
    },
  });
}

async function extractEmailFromRequest(
  request: NextRequest,
): Promise<string | null> {
  try {
    const body = (await request.json()) as { email?: unknown };
    return normalizePasswordProofEmail(
      typeof body.email === "string" ? body.email : null,
    );
  } catch {
    return null;
  }
}

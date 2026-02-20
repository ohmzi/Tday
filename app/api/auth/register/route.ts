import { NextRequest, NextResponse } from "next/server";
import { registrationSchema } from "@/schema";
import { prisma } from "@/lib/prisma/client";
import {
  BaseServerError,
  BadRequestError,
  InternalError,
} from "@/lib/customError";
import {
  buildAuthThrottleResponse,
  enforceAuthRateLimit,
} from "@/lib/security/authThrottle";
import { sha256 } from "@noble/hashes/sha256";
import { pbkdf2 } from "@noble/hashes/pbkdf2";
import { randomBytes } from "@noble/hashes/utils";
import { bytesToHex } from "@noble/hashes/utils";
import { Prisma } from "@prisma/client";

export async function POST(req: NextRequest) {
  try {
    let body: unknown;
    try {
      body = await req.json();
    } catch {
      throw new BadRequestError("invalid request body");
    }

    const throttleResult = await enforceAuthRateLimit({
      action: "register",
      request: req,
      identifier: extractEmailCandidate(body),
    });
    if (!throttleResult.allowed) {
      return buildAuthThrottleResponse(throttleResult);
    }

    // validate the body with zod
    const parsedObj = registrationSchema.safeParse(body);
    if (!parsedObj.success) {
      throw new BadRequestError();
    }
    const { fname, lname, email, password } = parsedObj.data;

    // CASE user registered before
    const userExists = await prisma.user.findUnique({
      where: { email: email },
    });
    if (userExists) {
      throw new BadRequestError("this email is taken");
    }

    // Generate salt and hash password
    const salt = randomBytes(16);
    const saltHex = bytesToHex(salt);

    // Use PBKDF2 for password hashing (more secure than single-pass SHA-256)
    const passwordHash = pbkdf2(sha256, password, salt, {
      c: 10000,
      dkLen: 32,
    });
    const passwordHex = bytesToHex(passwordHash);

    // Store salt:hash
    const hashedPassword = `${saltHex}:${passwordHex}`;

    const fullName = [fname.trim(), lname?.trim() || ""].filter(Boolean).join(" ");

    let createdWithinTransaction:
      | {
          user: {
            id: string;
            role: "ADMIN" | "USER";
            approvalStatus: "APPROVED" | "PENDING";
          };
          isBootstrapAdmin: boolean;
        }
      | null = null;

    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        createdWithinTransaction = await prisma.$transaction(
          async (tx) => {
            const userCount = await tx.user.count();
            const isFirstUser = userCount === 0;

            const createdUser = await tx.user.create({
              data: {
                name: fullName,
                email,
                password: hashedPassword,
                role: isFirstUser ? "ADMIN" : "USER",
                approvalStatus: isFirstUser ? "APPROVED" : "PENDING",
                approvedAt: isFirstUser ? new Date() : null,
              },
              select: {
                id: true,
                role: true,
                approvalStatus: true,
              },
            });

            return { user: createdUser, isBootstrapAdmin: isFirstUser };
          },
          { isolationLevel: Prisma.TransactionIsolationLevel.Serializable },
        );
        break;
      } catch (transactionError) {
        const shouldRetry =
          transactionError instanceof Prisma.PrismaClientKnownRequestError &&
          transactionError.code === "P2034";
        if (shouldRetry && attempt < 2) continue;
        throw transactionError;
      }
    }

    if (!createdWithinTransaction) {
      throw new InternalError("unable to create account at this time");
    }

    const { user, isBootstrapAdmin } = createdWithinTransaction;

    // CASE user not created
    if (!user) {
      throw new InternalError("user account not created");
    }

    const requiresApproval = user.approvalStatus === "PENDING";

    return NextResponse.json(
      {
        message: requiresApproval
          ? "Account registered. Waiting for admin approval."
          : "account created",
        requiresApproval,
        isBootstrapAdmin,
      },
      { status: 200 },
    );
  } catch (error) {
    console.log(error);

    if (
      error instanceof Prisma.PrismaClientKnownRequestError &&
      error.code === "P2002"
    ) {
      return NextResponse.json(
        { message: "this email is taken" },
        { status: 400 },
      );
    }

    // handle custom error
    if (error instanceof BaseServerError) {
      return NextResponse.json(
        { message: error.message },
        { status: error.status }
      );
    }

    // handle generic error
    return NextResponse.json(
      {
        message:
          error instanceof Error
            ? error.message.slice(0, 50)
            : "an unexpected error occured",
      },
      { status: 500 }
    );
  }
}

function extractEmailCandidate(body: unknown): string | null {
  if (!body || typeof body !== "object") return null;
  const candidate = (body as { email?: unknown }).email;
  if (typeof candidate !== "string") return null;
  return candidate;
}

import { Prisma } from "@prisma/client";
import { NextRequest, NextResponse } from "next/server";
import { prisma } from "@/lib/prisma/client";
import {
  getClientIp,
  getDeviceHint,
  hashSecurityValue,
  normalizeIdentifier,
} from "@/lib/security/clientSignals";
import { logSecurityEvent } from "@/lib/security/logSecurityEvent";

type AuthThrottleAction = "credentials" | "register" | "csrf";
type AuthThrottleReasonCode =
  | "auth_limit_ip"
  | "auth_limit_email"
  | "auth_lockout";

type AuthDimension = "ip" | "email" | "device";

type AuthThrottleResult =
  | { allowed: true }
  | {
      allowed: false;
      reasonCode: AuthThrottleReasonCode;
      retryAfterSeconds: number;
      dimension: AuthDimension;
    };

interface ThrottlePolicy {
  windowMs: number;
  maxRequests: number;
}

interface SubjectKey {
  scope: string;
  bucketKey: string;
  dimension: AuthDimension;
}

const POLICIES: Record<AuthThrottleAction, ThrottlePolicy> = {
  credentials: {
    windowMs: envSeconds("AUTH_LIMIT_CREDENTIALS_WINDOW_SEC", 300) * 1000,
    maxRequests: envInt("AUTH_LIMIT_CREDENTIALS_MAX", 12),
  },
  register: {
    windowMs: envSeconds("AUTH_LIMIT_REGISTER_WINDOW_SEC", 3600) * 1000,
    maxRequests: envInt("AUTH_LIMIT_REGISTER_MAX", 6),
  },
  csrf: {
    windowMs: envSeconds("AUTH_LIMIT_CSRF_WINDOW_SEC", 60) * 1000,
    maxRequests: envInt("AUTH_LIMIT_CSRF_MAX", 40),
  },
};

const LOCKOUT_FAIL_THRESHOLD = envInt("AUTH_LOCKOUT_FAIL_THRESHOLD", 5);
const LOCKOUT_BASE_MS = envSeconds("AUTH_LOCKOUT_BASE_SEC", 30) * 1000;
const LOCKOUT_MAX_MS = envSeconds("AUTH_LOCKOUT_MAX_SEC", 1800) * 1000;
const LOCKOUT_RESET_MS = envSeconds("AUTH_LOCKOUT_RESET_SEC", 86400) * 1000;

export async function enforceAuthRateLimit(params: {
  action: AuthThrottleAction;
  request: NextRequest;
  identifier?: string | null;
}): Promise<AuthThrottleResult> {
  const { action, request, identifier = null } = params;
  const subjects = buildSubjects(action, request, identifier);
  const policy = POLICIES[action];

  let blocked: Extract<AuthThrottleResult, { allowed: false }> | null = null;

  for (const subject of subjects) {
    const verdict = await consumeRequestQuota({
      policy,
      subject,
    });

    if (!verdict.allowed) {
      blocked = pickStrongerBlock(blocked, verdict);
    }
  }

  if (blocked) {
    await logSecurityEvent(blocked.reasonCode, {
      action,
      retryAfterSeconds: blocked.retryAfterSeconds,
      dimension: blocked.dimension,
    });
    return blocked;
  }

  return { allowed: true };
}

export async function recordCredentialFailure(params: {
  request: NextRequest;
  identifier?: string | null;
}) {
  const { request, identifier = null } = params;
  const subjects = buildSubjects("credentials", request, identifier);

  let longestLockSeconds = 0;
  for (const subject of subjects) {
    const lockSeconds = await incrementFailureCounter(subject);
    longestLockSeconds = Math.max(longestLockSeconds, lockSeconds);
  }

  if (longestLockSeconds > 0) {
    await logSecurityEvent("auth_lockout", {
      action: "credentials",
      retryAfterSeconds: longestLockSeconds,
    });
  }
}

export async function clearCredentialFailures(params: {
  request: NextRequest;
  identifier?: string | null;
}) {
  const { request, identifier = null } = params;
  const subjects = buildSubjects("credentials", request, identifier);

  for (const subject of subjects) {
    await prisma.authThrottle.updateMany({
      where: {
        scope: subject.scope,
        bucketKey: subject.bucketKey,
      },
      data: {
        failureCount: 0,
        lockUntil: null,
        lastFailureAt: null,
      },
    });
  }
}

export function buildAuthThrottleResponse(
  blocked: Extract<AuthThrottleResult, { allowed: false }>,
): NextResponse {
  const message =
    blocked.reasonCode === "auth_lockout"
      ? "Too many failed sign-in attempts. Try again after the cooldown."
      : "Too many authentication requests. Please slow down and try again.";

  return NextResponse.json(
    {
      message,
      reason: blocked.reasonCode,
    },
    {
      status: 429,
      headers: {
        "Retry-After": String(blocked.retryAfterSeconds),
        "Cache-Control": "no-store",
      },
    },
  );
}

async function consumeRequestQuota(params: {
  policy: ThrottlePolicy;
  subject: SubjectKey;
}): Promise<AuthThrottleResult> {
  const { policy, subject } = params;
  const now = new Date();

  return runSerializable(async (tx) => {
    const current = await tx.authThrottle.findUnique({
      where: {
        scope_bucketKey: {
          scope: subject.scope,
          bucketKey: subject.bucketKey,
        },
      },
    });

    if (!current) {
      await tx.authThrottle.create({
        data: {
          scope: subject.scope,
          bucketKey: subject.bucketKey,
          windowStart: now,
          requestCount: 1,
        },
      });

      return { allowed: true };
    }

    if (current.lockUntil && current.lockUntil.getTime() > now.getTime()) {
      return {
        allowed: false,
        reasonCode: "auth_lockout",
        retryAfterSeconds: retryAfterFromDate(current.lockUntil, now),
        dimension: subject.dimension,
      };
    }

    const windowResetNeeded =
      now.getTime() - current.windowStart.getTime() >= policy.windowMs;
    const nextWindowStart = windowResetNeeded ? now : current.windowStart;
    const nextRequestCount = windowResetNeeded ? 1 : current.requestCount + 1;

    await tx.authThrottle.update({
      where: {
        id: current.id,
      },
      data: {
        windowStart: nextWindowStart,
        requestCount: nextRequestCount,
      },
    });

    if (nextRequestCount <= policy.maxRequests) {
      return { allowed: true };
    }

    const windowEndsAt = new Date(nextWindowStart.getTime() + policy.windowMs);
    return {
      allowed: false,
      reasonCode:
        subject.dimension === "email" ? "auth_limit_email" : "auth_limit_ip",
      retryAfterSeconds: retryAfterFromDate(windowEndsAt, now),
      dimension: subject.dimension,
    };
  });
}

async function incrementFailureCounter(subject: SubjectKey): Promise<number> {
  const now = new Date();

  return runSerializable(async (tx) => {
    const current = await tx.authThrottle.findUnique({
      where: {
        scope_bucketKey: {
          scope: subject.scope,
          bucketKey: subject.bucketKey,
        },
      },
    });

    const baseFailureCount =
      current &&
      current.lastFailureAt &&
      now.getTime() - current.lastFailureAt.getTime() <= LOCKOUT_RESET_MS
        ? current.failureCount
        : 0;

    const nextFailureCount = baseFailureCount + 1;
    const computedLockUntil = lockUntilFromFailures(nextFailureCount, now);
    const activeExistingLockUntil =
      current?.lockUntil && current.lockUntil.getTime() > now.getTime()
        ? current.lockUntil
        : null;

    const lockUntil = laterDate(computedLockUntil, activeExistingLockUntil);

    if (!current) {
      await tx.authThrottle.create({
        data: {
          scope: subject.scope,
          bucketKey: subject.bucketKey,
          windowStart: now,
          requestCount: 0,
          failureCount: nextFailureCount,
          lastFailureAt: now,
          lockUntil,
        },
      });
    } else {
      await tx.authThrottle.update({
        where: { id: current.id },
        data: {
          failureCount: nextFailureCount,
          lastFailureAt: now,
          lockUntil,
        },
      });
    }

    return lockUntil ? retryAfterFromDate(lockUntil, now) : 0;
  });
}

function buildSubjects(
  action: AuthThrottleAction,
  request: NextRequest,
  identifier: string | null,
): SubjectKey[] {
  const subjects: SubjectKey[] = [];

  const ip = getClientIp(request);
  subjects.push(makeSubject(action, "ip", ip));

  const deviceHint = getDeviceHint(request);
  if (deviceHint) {
    subjects.push(makeSubject(action, "device", deviceHint));
  }

  if (action !== "csrf") {
    const normalizedIdentifier = normalizeIdentifier(identifier);
    if (normalizedIdentifier) {
      subjects.push(makeSubject(action, "email", normalizedIdentifier));
    }
  }

  return subjects;
}

function makeSubject(
  action: AuthThrottleAction,
  dimension: AuthDimension,
  value: string,
): SubjectKey {
  return {
    scope: `${action}:${dimension}`,
    bucketKey: hashSecurityValue(`${dimension}:${value}`),
    dimension,
  };
}

function pickStrongerBlock(
  current: Extract<AuthThrottleResult, { allowed: false }> | null,
  candidate: Extract<AuthThrottleResult, { allowed: false }>,
) {
  if (!current) return candidate;
  if (candidate.reasonCode === "auth_lockout" && current.reasonCode !== "auth_lockout") {
    return candidate;
  }
  if (candidate.retryAfterSeconds > current.retryAfterSeconds) {
    return candidate;
  }
  return current;
}

function lockUntilFromFailures(
  failureCount: number,
  now: Date,
): Date | null {
  if (failureCount < LOCKOUT_FAIL_THRESHOLD) return null;

  const exponent = Math.max(0, failureCount - LOCKOUT_FAIL_THRESHOLD);
  const lockMs = Math.min(
    LOCKOUT_MAX_MS,
    LOCKOUT_BASE_MS * Math.pow(2, exponent),
  );
  return new Date(now.getTime() + lockMs);
}

function laterDate(a: Date | null, b: Date | null): Date | null {
  if (!a) return b;
  if (!b) return a;
  return a.getTime() >= b.getTime() ? a : b;
}

function retryAfterFromDate(target: Date, now: Date): number {
  const seconds = Math.ceil((target.getTime() - now.getTime()) / 1000);
  return Math.max(1, seconds);
}

async function runSerializable<T>(
  operation: (tx: Prisma.TransactionClient) => Promise<T>,
): Promise<T> {
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      return await prisma.$transaction(
        (tx) => operation(tx),
        { isolationLevel: Prisma.TransactionIsolationLevel.Serializable },
      );
    } catch (error) {
      const shouldRetry =
        error instanceof Prisma.PrismaClientKnownRequestError &&
        error.code === "P2034";
      if (shouldRetry && attempt < 2) {
        continue;
      }
      throw error;
    }
  }

  throw new Error("Unable to complete auth throttle transaction");
}

function envInt(name: string, fallback: number): number {
  const parsed = Number(process.env[name]);
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
  return Math.floor(parsed);
}

function envSeconds(name: string, fallback: number): number {
  return envInt(name, fallback);
}

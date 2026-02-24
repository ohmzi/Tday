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
const CAPTCHA_TRIGGER_FAILURE_COUNT = envInt(
  "AUTH_CAPTCHA_TRIGGER_FAILURES",
  3,
);
const ALERT_IP_FAILURE_THRESHOLD = envInt(
  "AUTH_ALERT_IP_FAILURE_THRESHOLD",
  12,
);
const ALERT_LOCKOUT_BURST_SECONDS = envInt(
  "AUTH_ALERT_LOCKOUT_BURST_SEC",
  900,
);
const AUTH_SIGNAL_ANOMALY_WINDOW_MS =
  envSeconds("AUTH_SIGNAL_ANOMALY_WINDOW_SEC", 86400) * 1000;

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

    if (
      action === "credentials" &&
      blocked.reasonCode === "auth_limit_ip" &&
      blocked.retryAfterSeconds >= 60
    ) {
      await logSecurityEvent("auth_alert_ip_concentration", {
        action,
        retryAfterSeconds: blocked.retryAfterSeconds,
      });
    }
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
  let highestIpFailureCount = 0;
  for (const subject of subjects) {
    const verdict = await incrementFailureCounter(subject);
    longestLockSeconds = Math.max(longestLockSeconds, verdict.lockSeconds);
    if (subject.dimension === "ip") {
      highestIpFailureCount = Math.max(
        highestIpFailureCount,
        verdict.failureCount,
      );
    }
  }

  if (longestLockSeconds > 0) {
    await logSecurityEvent("auth_lockout", {
      action: "credentials",
      retryAfterSeconds: longestLockSeconds,
    });

    if (longestLockSeconds >= ALERT_LOCKOUT_BURST_SECONDS) {
      await logSecurityEvent("auth_alert_lockout_burst", {
        action: "credentials",
        retryAfterSeconds: longestLockSeconds,
      });
    }
  }

  if (highestIpFailureCount >= ALERT_IP_FAILURE_THRESHOLD) {
    await logSecurityEvent("auth_alert_ip_concentration", {
      action: "credentials",
      ipFailureCount: highestIpFailureCount,
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

export async function requiresCaptchaChallenge(params: {
  action: "credentials" | "register";
  request: NextRequest;
  identifier?: string | null;
}): Promise<boolean> {
  const { action, request, identifier = null } = params;
  const subjects = buildSubjects(action, request, identifier);
  const now = Date.now();

  for (const subject of subjects) {
    const current = await prisma.authThrottle.findUnique({
      where: {
        scope_bucketKey: {
          scope: subject.scope,
          bucketKey: subject.bucketKey,
        },
      },
      select: {
        failureCount: true,
        requestCount: true,
        lastFailureAt: true,
      },
    });

    if (!current) continue;

    const lastFailureAt = safeDate(current.lastFailureAt);
    const activeFailureCount =
      lastFailureAt &&
      now - lastFailureAt.getTime() <= LOCKOUT_RESET_MS
        ? current.failureCount
        : 0;

    if (activeFailureCount >= CAPTCHA_TRIGGER_FAILURE_COUNT) {
      return true;
    }

    if (
      action === "register" &&
      current.requestCount >= Math.max(3, Math.floor(POLICIES.register.maxRequests / 2))
    ) {
      return true;
    }
  }

  return false;
}

export async function recordCredentialSuccessSignal(params: {
  request: NextRequest;
  identifier?: string | null;
}) {
  const identifier = normalizeIdentifier(params.identifier);
  if (!identifier) return;

  const identifierHash = hashSecurityValue(`email:${identifier}`);
  const ipHash = hashSecurityValue(`ip:${getClientIp(params.request)}`);
  const deviceHint = getDeviceHint(params.request);
  const deviceHash = deviceHint
    ? hashSecurityValue(`device:${deviceHint}`)
    : null;
  const now = new Date();

  const previous = await prisma.authSignal.findUnique({
    where: { identifierHash },
    select: {
      lastIpHash: true,
      lastDeviceHash: true,
      lastSeenAt: true,
    },
  });

  const previousSeenAt = safeDate(previous?.lastSeenAt);
  if (
    previous &&
    previousSeenAt &&
    now.getTime() - previousSeenAt.getTime() <= AUTH_SIGNAL_ANOMALY_WINDOW_MS &&
    previous.lastIpHash &&
    previous.lastIpHash !== ipHash &&
    previous.lastDeviceHash &&
    deviceHash &&
    previous.lastDeviceHash !== deviceHash
  ) {
    await logSecurityEvent("auth_signal_anomaly", {
      reason: "ip_and_device_changed",
      identifierHash,
    });
  }

  await prisma.authSignal.upsert({
    where: { identifierHash },
    create: {
      identifierHash,
      lastIpHash: ipHash,
      lastDeviceHash: deviceHash,
      lastSeenAt: now,
    },
    update: {
      lastIpHash: ipHash,
      lastDeviceHash: deviceHash,
      lastSeenAt: now,
    },
  });
}

export function buildAuthThrottleResponse(
  blocked: Extract<AuthThrottleResult, { allowed: false }>,
): NextResponse {
  const retryAfterSeconds = Math.max(1, Math.floor(blocked.retryAfterSeconds));
  const retryAtIso = new Date(Date.now() + retryAfterSeconds * 1000).toISOString();
  const waitLabel = formatRetryWaitLabel(retryAfterSeconds);
  const message =
    blocked.reasonCode === "auth_lockout"
      ? `Too many failed sign-in attempts. Try again in ${waitLabel}.`
      : `Too many authentication requests. Try again in ${waitLabel}.`;

  return NextResponse.json(
    {
      message,
      reason: blocked.reasonCode,
      retryAfterSeconds,
      retryAt: retryAtIso,
    },
    {
      status: 429,
      headers: {
        "Retry-After": String(retryAfterSeconds),
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

    const currentLockUntil = safeDate(current.lockUntil);
    if (currentLockUntil && currentLockUntil.getTime() > now.getTime()) {
      return {
        allowed: false,
        reasonCode: "auth_lockout",
        retryAfterSeconds: retryAfterFromDate(currentLockUntil, now),
        dimension: subject.dimension,
      };
    }

    const currentWindowStart = safeDate(current.windowStart) ?? now;
    const windowResetNeeded =
      now.getTime() - currentWindowStart.getTime() >= policy.windowMs;
    const nextWindowStart = windowResetNeeded ? now : currentWindowStart;
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

async function incrementFailureCounter(subject: SubjectKey): Promise<{
  lockSeconds: number;
  failureCount: number;
}> {
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

    const lastFailureAt = safeDate(current?.lastFailureAt);
    const baseFailureCount =
      current &&
      lastFailureAt &&
      now.getTime() - lastFailureAt.getTime() <= LOCKOUT_RESET_MS
        ? current.failureCount
        : 0;

    const nextFailureCount = baseFailureCount + 1;
    const computedLockUntil = lockUntilFromFailures(nextFailureCount, now);
    const currentLockUntil = safeDate(current?.lockUntil);
    const activeExistingLockUntil =
      currentLockUntil && currentLockUntil.getTime() > now.getTime()
        ? currentLockUntil
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

    return {
      lockSeconds: lockUntil ? retryAfterFromDate(lockUntil, now) : 0,
      failureCount: nextFailureCount,
    };
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

function formatRetryWaitLabel(retryAfterSeconds: number): string {
  if (retryAfterSeconds < 60) {
    return `${retryAfterSeconds}s`;
  }

  const minutes = Math.floor(retryAfterSeconds / 60);
  const seconds = retryAfterSeconds % 60;
  if (seconds === 0) {
    return `${minutes}m`;
  }
  return `${minutes}m ${seconds}s`;
}

function safeDate(value: Date | string | null | undefined): Date | null {
  if (!value) return null;
  if (value instanceof Date) {
    return Number.isFinite(value.getTime()) ? value : null;
  }

  const parsed = new Date(value);
  return Number.isFinite(parsed.getTime()) ? parsed : null;
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

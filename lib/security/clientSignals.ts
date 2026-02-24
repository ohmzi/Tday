import { createHash, createHmac } from "crypto";
import { NextRequest } from "next/server";
import { getSecretValue } from "@/lib/security/secretSource";

const HASH_FALLBACK_SECRET = "tday-auth-signal-fallback";
let cachedHashSecret: string | null = null;

export function getClientIp(request: NextRequest): string {
  const cloudflareIp = request.headers.get("cf-connecting-ip")?.trim();
  if (cloudflareIp) return cloudflareIp;

  const forwardedFor = request.headers.get("x-forwarded-for");
  if (forwardedFor) {
    const firstIp = forwardedFor
      .split(",")
      .map((entry) => entry.trim())
      .find((entry) => entry.length > 0);
    if (firstIp) return firstIp;
  }

  const realIp = request.headers.get("x-real-ip")?.trim();
  if (realIp) return realIp;

  const fallbackIp = (request as unknown as { ip?: string }).ip?.trim();
  if (fallbackIp) return fallbackIp;

  return "unknown";
}

export function getDeviceHint(request: NextRequest): string | null {
  const deviceId = request.headers.get("x-tday-device-id")?.trim();
  if (!deviceId) return null;
  return deviceId.slice(0, 128);
}

export function normalizeIdentifier(value: string | null | undefined): string | null {
  const normalized = value?.trim().toLowerCase();
  if (!normalized) return null;
  return normalized;
}

export function hashSecurityValue(raw: string): string {
  return createHmac("sha256", hashSecret()).update(raw).digest("hex");
}

function hashSecret(): string {
  if (cachedHashSecret) return cachedHashSecret;

  const configured = getSecretValue({
    envVar: "AUTH_SECRET",
    fileEnvVar: "AUTH_SECRET_FILE",
  });

  if (configured && configured.length >= 16) {
    cachedHashSecret = configured;
    return cachedHashSecret;
  }

  // Keep deterministic fallback for development/test environments.
  cachedHashSecret = createHash("sha256")
    .update(HASH_FALLBACK_SECRET)
    .digest("hex");
  return cachedHashSecret;
}

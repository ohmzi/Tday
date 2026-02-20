import { createHash } from "crypto";
import { NextRequest } from "next/server";

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
  return createHash("sha256").update(raw).digest("hex");
}

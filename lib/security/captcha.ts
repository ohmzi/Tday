import { getSecretValue } from "@/lib/security/secretSource";
import { getClientIp } from "@/lib/security/clientSignals";
import { NextRequest } from "next/server";

type CaptchaVerificationResult =
  | { ok: true }
  | { ok: false; reason: string };

const TURNSTILE_VERIFY_URL =
  "https://challenges.cloudflare.com/turnstile/v0/siteverify";

export function captchaConfigured(): boolean {
  return Boolean(getCaptchaSecret());
}

export async function verifyCaptchaToken(params: {
  token: string | null;
  request: NextRequest;
  action: "credentials" | "register";
}): Promise<CaptchaVerificationResult> {
  if (!captchaConfigured()) {
    return { ok: true };
  }

  const token = params.token?.trim();
  if (!token) {
    return { ok: false, reason: "missing_captcha_token" };
  }

  const secret = getCaptchaSecret();
  if (!secret) {
    return { ok: false, reason: "captcha_secret_missing" };
  }

  try {
    const formData = new URLSearchParams();
    formData.set("secret", secret);
    formData.set("response", token);
    formData.set("remoteip", getClientIp(params.request));

    const response = await fetch(TURNSTILE_VERIFY_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: formData.toString(),
      cache: "no-store",
    });
    if (!response.ok) {
      return { ok: false, reason: `captcha_http_${response.status}` };
    }

    const payload = (await response.json()) as {
      success?: boolean;
      "error-codes"?: string[];
    };

    if (payload.success === true) {
      return { ok: true };
    }

    const errorCode = payload["error-codes"]?.[0] ?? "captcha_invalid";
    return { ok: false, reason: errorCode };
  } catch (error) {
    return {
      ok: false,
      reason: error instanceof Error ? error.message : "captcha_verification_error",
    };
  }
}

export function extractCaptchaTokenFromObject(payload: unknown): string | null {
  if (!payload || typeof payload !== "object") return null;
  const record = payload as Record<string, unknown>;
  const directToken = record.captchaToken;
  if (typeof directToken === "string" && directToken.trim()) {
    return directToken.trim();
  }
  const turnstileToken = record["cf-turnstile-response"];
  if (typeof turnstileToken === "string" && turnstileToken.trim()) {
    return turnstileToken.trim();
  }
  return null;
}

function getCaptchaSecret(): string | undefined {
  return getSecretValue({
    envVar: "AUTH_CAPTCHA_SECRET",
    fileEnvVar: "AUTH_CAPTCHA_SECRET_FILE",
  });
}

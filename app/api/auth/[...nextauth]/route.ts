import { NextRequest, NextResponse } from "next/server";
import { handlers } from "@/app/auth";
import {
  buildAuthThrottleResponse,
  clearCredentialFailures,
  enforceAuthRateLimit,
  recordCredentialSuccessSignal,
  recordCredentialFailure,
  requiresCaptchaChallenge,
} from "@/lib/security/authThrottle";
import { verifyCaptchaToken } from "@/lib/security/captcha";
import { logSecurityEvent } from "@/lib/security/logSecurityEvent";

const CSRF_PATH = "/api/auth/csrf";
const CREDENTIALS_CALLBACK_PATH = "/api/auth/callback/credentials";

export async function GET(request: NextRequest) {
  if (isCsrfRequest(request)) {
    const limitResult = await enforceAuthRateLimit({
      action: "csrf",
      request,
    });
    if (!limitResult.allowed) {
      return buildAuthThrottleResponse(limitResult);
    }
  }

  return handlers.GET(request);
}

export async function POST(request: NextRequest) {
  const credentialsRequest = isCredentialsCallbackRequest(request);
  const identifier = credentialsRequest
    ? await getFormField(request, "email")
    : null;

  if (credentialsRequest) {
    const captchaRequired = await requiresCaptchaChallenge({
      action: "credentials",
      request,
      identifier,
    });
    if (captchaRequired) {
      const captchaToken = await getCaptchaTokenFromForm(request);
      const captchaResult = await verifyCaptchaToken({
        token: captchaToken,
        request,
        action: "credentials",
      });
      if (!captchaResult.ok) {
        await logSecurityEvent("auth_captcha_failed", {
          action: "credentials",
          reason: captchaResult.reason,
        });
        return NextResponse.json(
          {
            message: "Additional verification required.",
            reason: "captcha_required",
          },
          {
            status: 403,
            headers: {
              "Cache-Control": "no-store",
            },
          },
        );
      }
    }

    const limitResult = await enforceAuthRateLimit({
      action: "credentials",
      request,
      identifier,
    });
    if (!limitResult.allowed) {
      return buildAuthThrottleResponse(limitResult);
    }
  }

  const response = await handlers.POST(request);

  if (!credentialsRequest) {
    return response;
  }

  const outcome = await parseCredentialsCallbackOutcome(request, response);
  if (outcome === "success" || outcome === "pending_approval") {
    await clearCredentialFailures({
      request,
      identifier,
    });
    await recordCredentialSuccessSignal({
      request,
      identifier,
    });
    return response;
  }

  if (outcome === "invalid_credentials") {
    await recordCredentialFailure({
      request,
      identifier,
    });
  }

  return response;
}

function isCsrfRequest(request: NextRequest): boolean {
  return request.nextUrl.pathname.endsWith(CSRF_PATH);
}

function isCredentialsCallbackRequest(request: NextRequest): boolean {
  return request.nextUrl.pathname.endsWith(CREDENTIALS_CALLBACK_PATH);
}

async function getFormField(
  request: NextRequest,
  field: string,
): Promise<string | null> {
  try {
    const formData = await request.clone().formData();
    const value = formData.get(field);
    return typeof value === "string" ? value : null;
  } catch {
    return null;
  }
}

async function getCaptchaTokenFromForm(
  request: NextRequest,
): Promise<string | null> {
  const fromCaptchaToken = await getFormField(request, "captchaToken");
  if (fromCaptchaToken) return fromCaptchaToken;
  return getFormField(request, "cf-turnstile-response");
}

type CredentialsOutcome =
  | "success"
  | "pending_approval"
  | "invalid_credentials"
  | "unknown_failure";

async function parseCredentialsCallbackOutcome(
  request: NextRequest,
  response: Response,
): Promise<CredentialsOutcome> {
  const callbackUrl = await extractCallbackUrl(response);
  if (!callbackUrl) {
    return response.ok ? "success" : "unknown_failure";
  }

  try {
    const parsedUrl = new URL(callbackUrl, request.url);
    const error = parsedUrl.searchParams.get("error");
    const code = parsedUrl.searchParams.get("code");

    if (!error) {
      return "success";
    }

    if (code === "pending_approval") {
      return "pending_approval";
    }

    if (error.toLowerCase().includes("credential")) {
      return "invalid_credentials";
    }

    return "unknown_failure";
  } catch {
    return response.ok ? "success" : "unknown_failure";
  }
}

async function extractCallbackUrl(response: Response): Promise<string | null> {
  const location = response.headers.get("location");
  if (location) return location;

  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.toLowerCase().includes("application/json")) return null;

  try {
    const body = (await response.clone().json()) as unknown;
    if (
      body &&
      typeof body === "object" &&
      "url" in body &&
      typeof (body as { url?: unknown }).url === "string"
    ) {
      return (body as { url: string }).url;
    }
  } catch {
    return null;
  }

  return null;
}

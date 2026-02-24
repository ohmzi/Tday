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
  const authRequest = normalizeAuthRequestOrigin(request);

  if (isCsrfRequest(authRequest)) {
    const limitResult = await enforceAuthRateLimit({
      action: "csrf",
      request: authRequest,
    });
    if (!limitResult.allowed) {
      return buildAuthThrottleResponse(limitResult);
    }
  }

  return handlers.GET(authRequest);
}

export async function POST(request: NextRequest) {
  const authRequest = normalizeAuthRequestOrigin(request);
  const credentialsRequest = isCredentialsCallbackRequest(authRequest);
  const identifier = credentialsRequest
    ? await getFormField(authRequest, "email")
    : null;

  if (credentialsRequest) {
    const captchaRequired = await requiresCaptchaChallenge({
      action: "credentials",
      request: authRequest,
      identifier,
    });
    if (captchaRequired) {
      const captchaToken = await getCaptchaTokenFromForm(authRequest);
      const captchaResult = await verifyCaptchaToken({
        token: captchaToken,
        request: authRequest,
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
      request: authRequest,
      identifier,
    });
    if (!limitResult.allowed) {
      return buildAuthThrottleResponse(limitResult);
    }
  }

  const response = await handlers.POST(authRequest);

  if (!credentialsRequest) {
    return response;
  }

  const outcome = await parseCredentialsCallbackOutcome(authRequest, response);
  if (outcome === "success" || outcome === "pending_approval") {
    await clearCredentialFailures({
      request: authRequest,
      identifier,
    });
    await recordCredentialSuccessSignal({
      request: authRequest,
      identifier,
    });
    return response;
  }

  if (outcome === "invalid_credentials") {
    await recordCredentialFailure({
      request: authRequest,
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

function normalizeAuthRequestOrigin(request: NextRequest): NextRequest {
  const forwardedHost = extractForwardedHeaderValue(
    request.headers.get("x-forwarded-host"),
  );
  const host = forwardedHost ?? extractForwardedHeaderValue(request.headers.get("host"));
  if (!host) {
    return request;
  }

  const parsedHost = parseHostHeader(host);
  if (!parsedHost) {
    return request;
  }

  const protocol = detectForwardedProtocol(request);
  const normalizedUrl = request.nextUrl.clone();

  try {
    normalizedUrl.hostname = parsedHost.hostname;
    normalizedUrl.port = parsedHost.port ?? "";
    normalizedUrl.protocol = `${protocol}:`;
  } catch {
    return request;
  }

  if (normalizedUrl.toString() === request.url) {
    return request;
  }

  return new NextRequest(normalizedUrl, request);
}

function extractForwardedHeaderValue(value: string | null): string | null {
  if (!value) return null;
  const first = value.split(",")[0]?.trim();
  return first ? first : null;
}

function parseHostHeader(host: string): { hostname: string; port?: string } | null {
  try {
    const parsed = new URL(`http://${host}`);
    const hostname = parsed.hostname.trim();
    if (!hostname) {
      return null;
    }

    const port = parsed.port.trim();
    return port ? { hostname, port } : { hostname };
  } catch {
    return null;
  }
}

function detectForwardedProtocol(request: NextRequest): "http" | "https" {
  const cfVisitor = request.headers.get("cf-visitor");
  if (cfVisitor) {
    try {
      const parsed = JSON.parse(cfVisitor) as { scheme?: unknown };
      if (parsed.scheme === "http" || parsed.scheme === "https") {
        return parsed.scheme;
      }
      if (typeof parsed.scheme === "string") {
        const lowered = parsed.scheme.toLowerCase();
        if (lowered === "http" || lowered === "https") {
          return lowered;
        }
      }
    } catch {
      // Ignore malformed proxy headers and fall back to standard forwarding headers.
    }
  }

  const forwardedProto = extractForwardedHeaderValue(
    request.headers.get("x-forwarded-proto"),
  )?.toLowerCase();
  if (forwardedProto === "http" || forwardedProto === "https") {
    return forwardedProto;
  }

  const protocol = request.nextUrl.protocol.replace(":", "").toLowerCase();
  return protocol === "http" ? "http" : "https";
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

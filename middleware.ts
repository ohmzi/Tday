import createMiddleware from "next-intl/middleware";
import { NextRequest, NextResponse } from "next/server";
import { routing } from "./i18n/routing";
import { getToken } from "next-auth/jwt";

const intlMiddleware = createMiddleware(routing);
const localeSet: ReadonlySet<string> = new Set(routing.locales);
const PUBLIC_API_PREFIXES = ["/api/auth", "/api/mobile/probe"];
const APPROVED_STATUS = "APPROVED";

export default async function middleware(req: NextRequest) {
  const secureResponse = enforceSecureTransport(req);
  if (secureResponse) {
    return applySecurityHeaders(secureResponse);
  }

  const pathname = req.nextUrl.pathname;
  const normalizedPath = stripLocalePrefix(pathname);
  const token = await getToken({
    req,
    secret: process.env.AUTH_SECRET,
  });
  const user = token as
    | { id?: string; approvalStatus?: string | null }
    | undefined;
  const protectedAppPath = isProtectedAppPath(normalizedPath);

  if (pathname.startsWith("/api/")) {
    if (!isPublicApiPath(pathname) && !user?.id) {
      return applySecurityHeaders(
        NextResponse.json(
          { message: "Authentication required" },
          {
            status: 401,
            headers: {
              "Cache-Control": "no-store, no-cache, must-revalidate",
              Pragma: "no-cache",
              Expires: "0",
            },
          },
        ),
      );
    }

    if (
      !isPublicApiPath(pathname) &&
      user?.approvalStatus &&
      user.approvalStatus !== APPROVED_STATUS
    ) {
      return applySecurityHeaders(
        NextResponse.json(
          { message: "Account approval required" },
          {
            status: 403,
            headers: {
              "Cache-Control": "no-store, no-cache, must-revalidate",
              Pragma: "no-cache",
              Expires: "0",
            },
          },
        ),
      );
    }

    return applySecurityHeaders(NextResponse.next());
  }

  if (protectedAppPath && !user?.id) {
    const loginUrl = req.nextUrl.clone();
    loginUrl.pathname = localizedPath(req, "/login");
    loginUrl.search = "";
    return applySecurityHeaders(NextResponse.redirect(loginUrl));
  }

  if (
    protectedAppPath &&
    user?.approvalStatus &&
    user.approvalStatus !== APPROVED_STATUS
  ) {
    const pendingUrl = req.nextUrl.clone();
    pendingUrl.pathname = localizedPath(req, "/login");
    pendingUrl.search = "pending=1";
    return applySecurityHeaders(NextResponse.redirect(pendingUrl));
  }

  const response = intlMiddleware(req);
  if (protectedAppPath) {
    response.headers.set("Cache-Control", "no-store, no-cache, must-revalidate");
    response.headers.set("Pragma", "no-cache");
    response.headers.set("Expires", "0");
  }

  return applySecurityHeaders(response);
}

export const config = {
  matcher: ["/((?!api|trpc|_next|_vercel|.*\\..*).*)", "/api/:path*"],
};

function enforceSecureTransport(req: NextRequest): NextResponse | null {
  if (process.env.NODE_ENV !== "production") {
    return null;
  }

  const host = resolveRequestHost(req);
  const parsedHost = host ? parseHostHeader(host) : null;
  if (!parsedHost || isLocalHostname(parsedHost.hostname)) {
    return null;
  }

  const protocol = resolveRequestProtocol(req);

  if (protocol === "https") {
    return null;
  }

  const redirectUrl = req.nextUrl.clone();
  if (parsedHost) {
    redirectUrl.hostname = parsedHost.hostname;
    redirectUrl.port = parsedHost.port ?? "";
  }
  redirectUrl.protocol = "https:";
  return NextResponse.redirect(redirectUrl, 308);
}

function resolveRequestHost(req: NextRequest): string | null {
  const hostHeader =
    req.headers.get("x-forwarded-host") ??
    req.headers.get("host") ??
    req.nextUrl.host;
  const host = hostHeader.split(",")[0]?.trim();
  return host ? host : null;
}

function parseHostHeader(
  host: string,
): { hostname: string; port?: string } | null {
  try {
    const parsed = new URL(`http://${host}`);
    const hostname = parsed.hostname.toLowerCase();
    if (!hostname) {
      return null;
    }

    const port = parsed.port.trim();
    return port ? { hostname, port } : { hostname };
  } catch {
    return null;
  }
}

function resolveRequestProtocol(req: NextRequest): "http" | "https" {
  const cfVisitor = req.headers.get("cf-visitor");
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

  const forwardedProto = req.headers.get("x-forwarded-proto");
  const firstForwardedProto = forwardedProto?.split(",")[0]?.trim().toLowerCase();
  if (firstForwardedProto === "http" || firstForwardedProto === "https") {
    return firstForwardedProto;
  }

  const protocol = req.nextUrl.protocol.replace(":", "").toLowerCase();
  return protocol === "http" ? "http" : "https";
}

function applySecurityHeaders(response: NextResponse): NextResponse {
  response.headers.set("X-Content-Type-Options", "nosniff");
  response.headers.set("X-Frame-Options", "DENY");
  response.headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
  response.headers.set("Cross-Origin-Resource-Policy", "same-origin");
  response.headers.set(
    "Permissions-Policy",
    "camera=(), microphone=(), geolocation=()",
  );
  response.headers.set("Cross-Origin-Opener-Policy", "same-origin");
  response.headers.set("Content-Security-Policy", contentSecurityPolicyValue());

  if (process.env.NODE_ENV === "production") {
    response.headers.set(
      "Strict-Transport-Security",
      "max-age=63072000; includeSubDomains; preload",
    );
  }

  return response;
}

function isLocalHostname(hostname: string): boolean {
  if (hostname === "localhost" || hostname === "10.0.2.2") return true;
  if (hostname.endsWith(".local")) return true;
  if (hostname === "127.0.0.1") return true;
  if (hostname.startsWith("127.")) return true;
  if (hostname.startsWith("10.")) return true;
  if (hostname.startsWith("192.168.")) return true;
  return /^172\.(1[6-9]|2\d|3[0-1])\./.test(hostname);
}

function isPublicApiPath(pathname: string): boolean {
  return PUBLIC_API_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`),
  );
}

function isProtectedAppPath(pathname: string): boolean {
  return pathname === "/app" || pathname.startsWith("/app/");
}

function stripLocalePrefix(pathname: string): string {
  const segments = pathname.split("/");
  const maybeLocale = segments[1];
  if (!maybeLocale || !localeSet.has(maybeLocale)) {
    return pathname;
  }

  const stripped = `/${segments.slice(2).join("/")}`.replace(/\/+/g, "/");
  if (!stripped || stripped === "/") return "/";
  return stripped.endsWith("/") ? stripped.slice(0, -1) : stripped;
}

function localizedPath(req: NextRequest, targetPath: string): string {
  const maybeLocale = req.nextUrl.pathname.split("/")[1];
  if (!maybeLocale || !localeSet.has(maybeLocale)) {
    return targetPath;
  }
  return `/${maybeLocale}${targetPath}`;
}

function contentSecurityPolicyValue(): string {
  const scriptSrc =
    process.env.NODE_ENV === "production"
      ? "script-src 'self' 'unsafe-inline'"
      : "script-src 'self' 'unsafe-inline' 'unsafe-eval'";
  const connectSrc =
    process.env.NODE_ENV === "production"
      ? "connect-src 'self' https: wss:"
      : "connect-src 'self' http: https: ws: wss:";

  return [
    "default-src 'self'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
    "object-src 'none'",
    "img-src 'self' data: blob: https://lh3.googleusercontent.com",
    "font-src 'self' data:",
    "style-src 'self' 'unsafe-inline'",
    scriptSrc,
    connectSrc,
  ].join("; ");
}

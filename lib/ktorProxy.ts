import { NextRequest, NextResponse } from "next/server";

const REQUEST_HEADERS_TO_STRIP = new Set([
  "connection",
  "content-length",
  "host",
  "transfer-encoding",
]);

const RESPONSE_HEADERS_TO_STRIP = new Set([
  "connection",
  "content-length",
  "content-encoding",
  "keep-alive",
  "transfer-encoding",
]);

function getBackendBaseUrl(): string | null {
  const raw = process.env.KTOR_BACKEND_URL?.trim();
  return raw ? raw.replace(/\/+$/, "") : null;
}

function copyRequestHeaders(req: NextRequest): Headers {
  const headers = new Headers(req.headers);
  const forwardedTimezone = req.headers.get("x-user-timezone");

  for (const header of REQUEST_HEADERS_TO_STRIP) {
    headers.delete(header);
  }

  if (forwardedTimezone && !headers.has("x-timezone")) {
    headers.set("x-timezone", forwardedTimezone);
  }

  headers.set("x-forwarded-host", req.headers.get("host") ?? "");
  headers.set(
    "x-forwarded-proto",
    req.headers.get("x-forwarded-proto") ?? req.nextUrl.protocol.replace(":", ""),
  );

  return headers;
}

function copyResponseHeaders(headers: Headers): Headers {
  const nextHeaders = new Headers(headers);

  for (const header of RESPONSE_HEADERS_TO_STRIP) {
    nextHeaders.delete(header);
  }

  return nextHeaders;
}

async function proxyToKtor(req: NextRequest): Promise<NextResponse> {
  const backendBaseUrl = getBackendBaseUrl();

  if (!backendBaseUrl) {
    return NextResponse.json(
      { message: "KTOR_BACKEND_URL is not configured" },
      { status: 503 },
    );
  }

  const targetUrl = new URL(`${req.nextUrl.pathname}${req.nextUrl.search}`, backendBaseUrl);
  const headers = copyRequestHeaders(req);
  const init: RequestInit = {
    method: req.method,
    headers,
    cache: "no-store",
    redirect: "manual",
  };

  if (req.method !== "GET" && req.method !== "HEAD") {
    const body = await req.arrayBuffer();
    if (body.byteLength > 0) {
      init.body = body;
    }
  }

  try {
    const response = await fetch(targetUrl, init);

    return new NextResponse(response.body, {
      status: response.status,
      headers: copyResponseHeaders(response.headers),
    });
  } catch (error) {
    console.error("Ktor proxy request failed:", error);

    return NextResponse.json(
      { message: "Ktor backend is unavailable" },
      { status: 503 },
    );
  }
}

export const proxyDelete = (req: NextRequest) => proxyToKtor(req);
export const proxyGet = (req: NextRequest) => proxyToKtor(req);
export const proxyPatch = (req: NextRequest) => proxyToKtor(req);
export const proxyPost = (req: NextRequest) => proxyToKtor(req);

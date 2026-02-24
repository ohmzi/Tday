import { NextRequest } from "next/server";

const mockHandlersGet = jest.fn(async (request: Request) => {
  return new Response(request.url, { status: 200 });
});

const mockHandlersPost = jest.fn(async (request: Request) => {
  return new Response(request.url, { status: 200 });
});

jest.mock("@/app/auth", () => ({
  handlers: {
    GET: (...args: unknown[]) => mockHandlersGet(...args),
    POST: (...args: unknown[]) => mockHandlersPost(...args),
  },
}));

jest.mock("@/lib/security/authThrottle", () => ({
  buildAuthThrottleResponse: jest.fn(() => new Response(null, { status: 429 })),
  clearCredentialFailures: jest.fn(async () => {}),
  enforceAuthRateLimit: jest.fn(async () => ({ allowed: true })),
  recordCredentialSuccessSignal: jest.fn(async () => {}),
  recordCredentialFailure: jest.fn(async () => {}),
  requiresCaptchaChallenge: jest.fn(async () => false),
}));

jest.mock("@/lib/security/captcha", () => ({
  verifyCaptchaToken: jest.fn(async () => ({ ok: true })),
}));

jest.mock("@/lib/security/logSecurityEvent", () => ({
  logSecurityEvent: jest.fn(async () => {}),
}));

import { GET } from "@/app/api/auth/[...nextauth]/route";

describe("auth route origin normalization", () => {
  beforeEach(() => {
    mockHandlersGet.mockClear();
    mockHandlersPost.mockClear();
  });

  test("uses forwarded host/proto for csrf requests", async () => {
    const request = new NextRequest("http://localhost:3000/api/auth/csrf", {
      headers: {
        host: "localhost:3000",
        "x-forwarded-host": "tday.ohmz.cloud",
        "x-forwarded-proto": "https",
      },
    });

    const response = await GET(request);
    const body = await response.text();

    expect(mockHandlersGet).toHaveBeenCalledTimes(1);
    expect(body).toBe("https://tday.ohmz.cloud/api/auth/csrf");
  });

  test("prefers Cloudflare visitor scheme when x-forwarded-proto is downgraded", async () => {
    const request = new NextRequest("http://localhost:3000/api/auth/csrf", {
      headers: {
        host: "localhost:3000",
        "x-forwarded-host": "tday.ohmz.cloud",
        "x-forwarded-proto": "http",
        "cf-visitor": "{\"scheme\":\"https\"}",
      },
    });

    const response = await GET(request);
    const body = await response.text();

    expect(mockHandlersGet).toHaveBeenCalledTimes(1);
    expect(body).toBe("https://tday.ohmz.cloud/api/auth/csrf");
  });
});

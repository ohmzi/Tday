import { NextRequest } from "next/server";
import { resetPasswordProofChallengesForTests } from "@/lib/security/passwordProof";

const mockFindUnique = jest.fn();
const mockEnforceAuthRateLimit = jest.fn(async () => ({ allowed: true }));
const mockBuildAuthThrottleResponse = jest.fn(
  () => new Response("rate_limited", { status: 429 }),
);

jest.mock("@/lib/prisma/client", () => ({
  prisma: {
    user: {
      findUnique: (...args: unknown[]) => mockFindUnique(...args),
    },
  },
}));

jest.mock("@/lib/security/authThrottle", () => ({
  enforceAuthRateLimit: (...args: unknown[]) => mockEnforceAuthRateLimit(...args),
  buildAuthThrottleResponse: (...args: unknown[]) =>
    mockBuildAuthThrottleResponse(...args),
}));

import { POST } from "@/app/api/auth/login-challenge/route";
import { hashPassword } from "@/lib/security/password";

describe("auth password proof challenge route", () => {
  beforeEach(() => {
    resetPasswordProofChallengesForTests();
    mockFindUnique.mockReset();
    mockEnforceAuthRateLimit.mockReset();
    mockBuildAuthThrottleResponse.mockReset();
    mockBuildAuthThrottleResponse.mockReturnValue(
      new Response("rate_limited", { status: 429 }),
    );
    mockEnforceAuthRateLimit.mockResolvedValue({ allowed: true });
  });

  test("returns challenge payload for a known user", async () => {
    mockFindUnique.mockResolvedValue({
      password: hashPassword("ChallengeTest#1"),
    });

    const request = new NextRequest("http://localhost:3000/api/auth/login-challenge", {
      method: "POST",
      headers: {
        "content-type": "application/json",
      },
      body: JSON.stringify({
        email: "  CHALLENGE.USER@example.com ",
      }),
    });

    const response = await POST(request);
    expect(response.status).toBe(200);

    const body = (await response.json()) as Record<string, unknown>;
    expect(body.version).toBe("1");
    expect(body.algorithm).toBe("pbkdf2_sha256+hmac_sha256");
    expect(typeof body.challengeId).toBe("string");
    expect(typeof body.saltHex).toBe("string");
    expect(typeof body.iterations).toBe("number");
    expect(response.headers.get("cache-control")).toBe("no-store");
  });

  test("returns 400 when email is missing", async () => {
    const request = new NextRequest("http://localhost:3000/api/auth/login-challenge", {
      method: "POST",
      headers: {
        "content-type": "application/json",
      },
      body: JSON.stringify({}),
    });

    const response = await POST(request);
    expect(response.status).toBe(400);
  });

  test("returns throttled response when auth rate limiter blocks", async () => {
    mockEnforceAuthRateLimit.mockResolvedValue({
      allowed: false,
      reasonCode: "auth_limit_email",
      retryAfterSeconds: 120,
      dimension: "email",
    });

    const request = new NextRequest("http://localhost:3000/api/auth/login-challenge", {
      method: "POST",
      headers: {
        "content-type": "application/json",
      },
      body: JSON.stringify({
        email: "throttle.user@example.com",
      }),
    });

    const response = await POST(request);
    expect(response.status).toBe(429);
    expect(mockBuildAuthThrottleResponse).toHaveBeenCalledTimes(1);
  });
});

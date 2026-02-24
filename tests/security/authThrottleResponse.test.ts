import { buildAuthThrottleResponse } from "@/lib/security/authThrottle";

describe("auth throttle responses", () => {
  afterEach(() => {
    jest.useRealTimers();
  });

  test("includes retry metadata for auth_limit_email responses", async () => {
    jest.useFakeTimers().setSystemTime(new Date("2026-02-24T06:20:00.000Z"));

    const response = buildAuthThrottleResponse({
      allowed: false,
      reasonCode: "auth_limit_email",
      retryAfterSeconds: 125,
      dimension: "email",
    });

    expect(response.status).toBe(429);
    expect(response.headers.get("Retry-After")).toBe("125");

    const body = (await response.json()) as {
      message: string;
      reason: string;
      retryAfterSeconds: number;
      retryAt: string;
    };

    expect(body.reason).toBe("auth_limit_email");
    expect(body.retryAfterSeconds).toBe(125);
    expect(body.retryAt).toBe("2026-02-24T06:22:05.000Z");
    expect(body.message).toContain("2m 5s");
    expect(body.message).toContain("Try again in");
  });

  test("includes retry metadata for auth_lockout responses", async () => {
    jest.useFakeTimers().setSystemTime(new Date("2026-02-24T06:20:00.000Z"));

    const response = buildAuthThrottleResponse({
      allowed: false,
      reasonCode: "auth_lockout",
      retryAfterSeconds: 3600,
      dimension: "email",
    });

    expect(response.status).toBe(429);
    expect(response.headers.get("Retry-After")).toBe("3600");

    const body = (await response.json()) as {
      message: string;
      reason: string;
      retryAfterSeconds: number;
      retryAt: string;
    };

    expect(body.reason).toBe("auth_lockout");
    expect(body.retryAfterSeconds).toBe(3600);
    expect(body.retryAt).toBe("2026-02-24T07:20:00.000Z");
    expect(body.message).toContain("60m");
    expect(body.message).toContain("Try again in");
  });
});

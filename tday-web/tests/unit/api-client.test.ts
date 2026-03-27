import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api-client";

describe("api client", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("throws an ApiError with server details when a request fails", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: "pending_approval",
            message: "Account approval required",
          }),
          {
            status: 403,
            statusText: "Forbidden",
            headers: {
              "Content-Type": "application/json",
            },
          },
        ),
      ),
    );

    let thrownError: unknown;

    try {
      await api.GET({ url: "/api/auth/session" });
    } catch (error) {
      thrownError = error;
    }

    expect(thrownError).toBeInstanceOf(ApiError);
    expect(thrownError).toMatchObject({
      code: "pending_approval",
      message: "Account approval required",
      status: 403,
    });
  });

  it("returns null for 204 responses", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(null, {
          status: 204,
        }),
      ),
    );

    await expect(api.DELETE({ url: "/api/todo/123" })).resolves.toBeNull();
  });
});

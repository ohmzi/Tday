// @vitest-environment jsdom

import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { AuthProvider, useAuth } from "@/providers/AuthProvider";

function createWrapper() {
  const queryClient = new QueryClient();

  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <AuthProvider>{children}</AuthProvider>
      </QueryClientProvider>
    );
  };
}

describe("AuthProvider", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("loads the session on mount and exposes the authenticated user", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          user: {
            id: "user-1",
            name: "Taylor",
            email: "taylor@example.com",
            role: "USER",
            approvalStatus: "APPROVED",
            timeZone: "UTC",
          },
        }),
        {
          status: 200,
          headers: {
            "Content-Type": "application/json",
          },
        },
      ),
    );

    vi.stubGlobal("fetch", fetchMock);

    const { result } = renderHook(() => useAuth(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(fetchMock).toHaveBeenCalledWith("/api/auth/session", {
      cache: "no-store",
      credentials: "same-origin",
    });
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user?.email).toBe("taylor@example.com");
  });
});

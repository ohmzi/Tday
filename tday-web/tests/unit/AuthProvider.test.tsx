// @vitest-environment jsdom

import type { ReactNode } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider, useAuth } from "@/providers/AuthProvider";
import { RETURNING_BROWSER_STORAGE_KEY } from "@/lib/security/returningBrowser";
import { HAPTICS_STORAGE_KEY } from "@/lib/feedbackPreferences";
import {
  TELEMETRY_CONSENT_AT_STORAGE_KEY,
  TELEMETRY_CONSENT_STORAGE_KEY,
} from "@/lib/privacy/telemetryConsent";

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
    window.localStorage.clear();
    window.sessionStorage.clear();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  function mockResponse(status: number, body: unknown, contentType = "application/json") {
    return new Response(JSON.stringify(body), {
      status,
      headers: {
        "Content-Type": contentType,
      },
    });
  }

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

    expect(fetchMock).toHaveBeenCalledWith("/api/auth/session", expect.objectContaining({
      method: "GET",
      cache: "no-store",
      credentials: "same-origin",
    }));
    expect(result.current.authState).toBe("authenticated");
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user?.email).toBe("taylor@example.com");
    expect(window.localStorage.getItem(RETURNING_BROWSER_STORAGE_KEY)).toBe("1");
  });

  it("treats a 401 session response as unauthenticated", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockResponse(401, {
          message: "Not authenticated",
        }),
      ),
    );

    const { result } = renderHook(() => useAuth(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.authState).toBe("unauthenticated");
    });

    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
  });

  it("treats transient session failures as unavailable without logging the user out", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        mockResponse(500, {
          message: "Server unavailable",
        }),
      ),
    );

    const { result } = renderHook(() => useAuth(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.authState).toBe("unavailable");
    });

    expect(result.current.isLoading).toBe(false);
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
  });

  it("preserves the returning-browser marker when a prior session is invalidated", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        mockResponse(200, {
          user: {
            id: "user-1",
            name: "Taylor",
            email: "taylor@example.com",
            role: "USER",
            approvalStatus: "APPROVED",
            timeZone: "UTC",
          },
        }),
      )
      .mockResolvedValueOnce(
        mockResponse(401, {
          message: "Not authenticated",
        }),
      );

    vi.stubGlobal("fetch", fetchMock);

    const { result } = renderHook(() => useAuth(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.authState).toBe("authenticated");
    });

    window.localStorage.setItem("menu-state", "open");
    window.sessionStorage.setItem("draft", "cached");

    await act(async () => {
      await result.current.refreshSession();
    });

    await waitFor(() => {
      expect(result.current.authState).toBe("unauthenticated");
    });

    expect(window.localStorage.getItem(RETURNING_BROWSER_STORAGE_KEY)).toBe("1");
    expect(window.localStorage.getItem("menu-state")).toBeNull();
    expect(window.sessionStorage.getItem("draft")).toBeNull();
  });

  it("preserves the returning-browser marker on logout while clearing other browser data", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        mockResponse(200, {
          user: {
            id: "user-1",
            name: "Taylor",
            email: "taylor@example.com",
            role: "USER",
            approvalStatus: "APPROVED",
            timeZone: "UTC",
          },
        }),
      )
      .mockResolvedValueOnce(
        mockResponse(200, {
          message: "logged_out",
        }),
      );

    vi.stubGlobal("fetch", fetchMock);

    const { result } = renderHook(() => useAuth(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.authState).toBe("authenticated");
    });

    window.localStorage.setItem("menu-state", "open");
    // An accessibility choice, not session state: signing out is not a request to
    // start buzzing again.
    window.localStorage.setItem(HAPTICS_STORAGE_KEY, "0");
    window.sessionStorage.setItem("draft", "cached");

    await act(async () => {
      await result.current.logout();
    });

    expect(window.localStorage.getItem(RETURNING_BROWSER_STORAGE_KEY)).toBe("1");
    expect(window.localStorage.getItem(HAPTICS_STORAGE_KEY)).toBe("0");
    expect(window.localStorage.getItem("menu-state")).toBeNull();
    expect(window.sessionStorage.getItem("draft")).toBeNull();
    expect(result.current.authState).toBe("unauthenticated");
  });

  it("keeps the crash-report answer through a logout and through an expired session", async () => {
    const signedIn = () =>
      mockResponse(200, {
        user: {
          id: "user-1",
          name: "Taylor",
          email: "taylor@example.com",
          role: "USER",
          approvalStatus: "APPROVED",
          timeZone: "UTC",
        },
      });
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(signedIn())
      .mockResolvedValueOnce(mockResponse(200, { message: "logged_out" }))
      .mockResolvedValueOnce(signedIn())
      .mockResolvedValueOnce(mockResponse(401, { message: "Not authenticated" }));

    vi.stubGlobal("fetch", fetchMock);

    const { result } = renderHook(() => useAuth(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.authState).toBe("authenticated");
    });

    // The answer belongs to this browser, not to the account. Losing it on a sign-out would put
    // the consent card back in front of someone who already said no, and losing a "yes" would
    // switch reports off without their having asked.
    window.localStorage.setItem(TELEMETRY_CONSENT_STORAGE_KEY, "granted");
    window.localStorage.setItem(TELEMETRY_CONSENT_AT_STORAGE_KEY, "1759320000000");

    await act(async () => {
      await result.current.logout();
    });

    expect(window.localStorage.getItem(TELEMETRY_CONSENT_STORAGE_KEY)).toBe("granted");
    expect(window.localStorage.getItem(TELEMETRY_CONSENT_AT_STORAGE_KEY)).toBe("1759320000000");

    await act(async () => {
      await result.current.refreshSession();
    });
    await waitFor(() => {
      expect(result.current.authState).toBe("authenticated");
    });

    await act(async () => {
      await result.current.refreshSession();
    });
    await waitFor(() => {
      expect(result.current.authState).toBe("unauthenticated");
    });

    expect(window.localStorage.getItem(TELEMETRY_CONSENT_STORAGE_KEY)).toBe("granted");
    expect(window.localStorage.getItem(TELEMETRY_CONSENT_AT_STORAGE_KEY)).toBe("1759320000000");
  });

  it("does not clear auth state locally when the logout request fails", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(
        mockResponse(200, {
          user: {
            id: "user-1",
            name: "Taylor",
            email: "taylor@example.com",
            role: "USER",
            approvalStatus: "APPROVED",
            timeZone: "UTC",
          },
        }),
      )
      .mockResolvedValueOnce(
        mockResponse(500, {
          message: "Logout failed",
        }),
      );

    vi.stubGlobal("fetch", fetchMock);

    const { result } = renderHook(() => useAuth(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.authState).toBe("authenticated");
    });

    window.localStorage.setItem("menu-state", "open");
    window.sessionStorage.setItem("draft", "cached");

    await expect(result.current.logout()).rejects.toThrow("Logout failed");

    expect(result.current.authState).toBe("authenticated");
    expect(result.current.user?.email).toBe("taylor@example.com");
    expect(window.localStorage.getItem(RETURNING_BROWSER_STORAGE_KEY)).toBe("1");
    expect(window.localStorage.getItem("menu-state")).toBe("open");
    expect(window.sessionStorage.getItem("draft")).toBe("cached");
  });
});

// @vitest-environment jsdom

import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@/i18n";
import { CrashReportsRow, ServerTelemetryRow } from "@/components/settings/PrivacyRows";
import { useServerTelemetry } from "@/features/serverTelemetry/query/get-server-telemetry";
import type { ServerTelemetryResponse } from "@/features/serverTelemetry/query/get-server-telemetry";
import { ApiError } from "@/lib/api-client";
import { getTelemetryConsent, setTelemetryConsent } from "@/lib/privacy/telemetryConsent";

const api = vi.hoisted(() => ({ GET: vi.fn(), PATCH: vi.fn() }));
vi.mock("@/lib/api-client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api-client")>()),
  api,
}));

let authUser: { role: string | null } | null = { role: "ADMIN" };
vi.mock("@/providers/AuthProvider", () => ({
  useAuth: () => ({ user: authUser }),
}));

let localMode = false;
vi.mock("@/hooks/useAppMode", () => ({
  useIsLocalMode: () => localMode,
}));

const toast = vi.fn();
vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast }),
}));

const OFFERED: ServerTelemetryResponse = { dsnConfigured: true, enabled: false, updatedAt: null };

function wrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={["/en/app/settings"]}>
          <Routes>
            <Route path="/:locale/app/settings" element={children} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    );
  };
}

beforeEach(() => {
  api.GET.mockReset();
  api.PATCH.mockReset();
  toast.mockReset();
  authUser = { role: "ADMIN" };
  localMode = false;
  window.localStorage.clear();
  window.dispatchEvent(new StorageEvent("storage", { key: null }));
});

afterEach(cleanup);

describe("CrashReportsRow", () => {
  it("shows the switch off for a browser that has not answered", () => {
    render(<CrashReportsRow />, { wrapper: wrapper() });

    const toggle = screen.getByRole("switch", { name: "Send crash & problem reports when something fails" });
    expect(toggle.getAttribute("aria-checked")).toBe("false");
    expect(screen.getByText("Crash & problem reports")).toBeTruthy();
  });

  it("links the row to its guide topic with a name that says what it is about", () => {
    render(<CrashReportsRow />, { wrapper: wrapper() });

    const help = screen.getByRole("link", { name: "About crash & problem reports" });
    expect(help.getAttribute("href")).toBe("/en/app/guide/crash-reports");
  });

  it("turns reports on, and counts as answering the question", () => {
    render(<CrashReportsRow />, { wrapper: wrapper() });

    fireEvent.click(screen.getByRole("switch"));

    expect(getTelemetryConsent()).toBe("granted");
    expect(screen.getByRole("switch").getAttribute("aria-checked")).toBe("true");
  });

  it("turns reports off again", () => {
    setTelemetryConsent(true);
    render(<CrashReportsRow />, { wrapper: wrapper() });
    expect(screen.getByRole("switch").getAttribute("aria-checked")).toBe("true");

    fireEvent.click(screen.getByRole("switch"));

    expect(getTelemetryConsent()).toBe("denied");
    expect(screen.getByRole("switch").getAttribute("aria-checked")).toBe("false");
  });

  it("follows an answer given somewhere else, such as the consent card", () => {
    render(<CrashReportsRow />, { wrapper: wrapper() });

    act(() => setTelemetryConsent(true));

    expect(screen.getByRole("switch").getAttribute("aria-checked")).toBe("true");
  });
});

describe("useServerTelemetry", () => {
  it("offers the setting to an admin of a server whose DSN is set", async () => {
    api.GET.mockResolvedValue(OFFERED);

    const { result } = renderHook(() => useServerTelemetry(), { wrapper: wrapper() });

    await waitFor(() => expect(result.current).toEqual(OFFERED));
    expect(api.GET).toHaveBeenCalledWith({ url: "/api/admin/telemetry" });
  });

  it("does not offer it, or even ask, when the viewer is not an admin", async () => {
    authUser = { role: "USER" };

    const { result } = renderHook(() => useServerTelemetry(), { wrapper: wrapper() });

    await act(async () => {});
    expect(result.current).toBeNull();
    expect(api.GET).not.toHaveBeenCalled();
  });

  it("stops offering it once the viewer is no longer an admin, though the answer is still cached", async () => {
    api.GET.mockResolvedValue(OFFERED);
    const { result, rerender } = renderHook(() => useServerTelemetry(), { wrapper: wrapper() });
    await waitFor(() => expect(result.current).toEqual(OFFERED));

    authUser = { role: "USER" };
    rerender();

    expect(result.current).toBeNull();
  });

  it("does not offer it, or even ask, in Local Mode", async () => {
    localMode = true;

    const { result } = renderHook(() => useServerTelemetry(), { wrapper: wrapper() });

    await act(async () => {});
    expect(result.current).toBeNull();
    expect(api.GET).not.toHaveBeenCalled();
  });

  it("does not offer it when the server has no DSN", async () => {
    api.GET.mockResolvedValue({ dsnConfigured: false, enabled: false, updatedAt: null });

    const { result } = renderHook(() => useServerTelemetry(), { wrapper: wrapper() });

    await waitFor(() => expect(api.GET).toHaveBeenCalled());
    await act(async () => {});
    expect(result.current).toBeNull();
  });

  it("does not offer it while signed out", async () => {
    authUser = null;

    const { result } = renderHook(() => useServerTelemetry(), { wrapper: wrapper() });

    await act(async () => {});
    expect(result.current).toBeNull();
    expect(api.GET).not.toHaveBeenCalled();
  });

  it("does not offer it when the request fails", async () => {
    api.GET.mockRejectedValue(new ApiError("Forbidden", 403));

    const { result } = renderHook(() => useServerTelemetry(), { wrapper: wrapper() });

    await waitFor(() => expect(api.GET).toHaveBeenCalled());
    await act(async () => {});
    expect(result.current).toBeNull();
  });
});

describe("ServerTelemetryRow", () => {
  it("explains what the switch does and shows the server's current state", () => {
    render(<ServerTelemetryRow telemetry={{ ...OFFERED, enabled: true }} />, { wrapper: wrapper() });

    expect(screen.getByText("Server error reports")).toBeTruthy();
    expect(screen.getByText(/Sends this server's own errors to the Sentry project set in SENTRY_DSN/)).toBeTruthy();
    expect(
      screen.getByRole("switch", { name: "Send this server's error reports to its operator's Sentry" }).getAttribute("aria-checked"),
    ).toBe("true");
  });

  it("sends the new state through the shared API client and refreshes the query", async () => {
    api.PATCH.mockResolvedValue({ ...OFFERED, enabled: true });
    api.GET.mockResolvedValue({ ...OFFERED, enabled: true });
    render(<ServerTelemetryRow telemetry={OFFERED} />, { wrapper: wrapper() });

    fireEvent.click(screen.getByRole("switch"));

    await waitFor(() => expect(api.PATCH).toHaveBeenCalledTimes(1));
    expect(api.PATCH).toHaveBeenCalledWith({
      url: "/api/admin/telemetry",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled: true }),
    });
    expect(toast).not.toHaveBeenCalled();
  });

  it("shows the requested state while the change is in flight, and locks the switch", async () => {
    let resolve: (value: ServerTelemetryResponse) => void = () => {};
    api.PATCH.mockReturnValue(new Promise<ServerTelemetryResponse>((r) => (resolve = r)));
    render(<ServerTelemetryRow telemetry={OFFERED} />, { wrapper: wrapper() });

    fireEvent.click(screen.getByRole("switch"));

    await waitFor(() => expect(screen.getByRole("switch").getAttribute("aria-checked")).toBe("true"));
    expect((screen.getByRole("switch") as HTMLButtonElement).disabled).toBe(true);
    await act(async () => resolve({ ...OFFERED, enabled: true }));
  });

  it("says so, and falls back to the server's state, when the change fails", async () => {
    api.PATCH.mockRejectedValue(new ApiError("Forbidden", 403));
    render(<ServerTelemetryRow telemetry={OFFERED} />, { wrapper: wrapper() });

    fireEvent.click(screen.getByRole("switch"));

    await waitFor(() =>
      expect(toast).toHaveBeenCalledWith({
        description: "Couldn't update the server setting. Try again.",
        variant: "destructive",
      }),
    );
    await waitFor(() => expect(screen.getByRole("switch").getAttribute("aria-checked")).toBe("false"));
  });
});

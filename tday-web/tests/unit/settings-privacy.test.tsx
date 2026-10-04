// @vitest-environment jsdom

import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@/i18n";
import { ServerTelemetryRow, ServerTelemetryStateRow } from "@/components/settings/PrivacyRows";
import {
  useServerTelemetry,
  type ServerTelemetryResponse,
} from "@/features/serverTelemetry/query/get-server-telemetry";
import { useInstanceTelemetry } from "@/features/serverTelemetry/query/get-instance-telemetry";
import { ApiError } from "@/lib/api-client";
import { answerFrom, applyInstanceTelemetry } from "@/lib/privacy/instanceTelemetry";

/**
 * The Privacy card has one setting on the web: the instance-wide error-report answer an admin
 * gives. The admin gets the switch; everyone else gets the same answer as information, because a
 * control the server would refuse is worse than no control.
 */

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
const INSTANCE_OFF = { enabled: false, updatedAt: "2026-09-01T00:00:00.000Z" };

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
  applyInstanceTelemetry(answerFrom(false, null));
});

afterEach(cleanup);

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

describe("useInstanceTelemetry", () => {
  it("reads the public answer when the caller asks for it", async () => {
    api.GET.mockResolvedValue(INSTANCE_OFF);

    const { result } = renderHook(() => useInstanceTelemetry(true), { wrapper: wrapper() });

    await waitFor(() => expect(result.current).toEqual(INSTANCE_OFF));
    expect(api.GET).toHaveBeenCalledWith({ url: "/api/instance/telemetry" });
  });

  it("does not ask at all when the caller has no reason to show it", async () => {
    const { result } = renderHook(() => useInstanceTelemetry(false), { wrapper: wrapper() });

    await act(async () => {});
    expect(result.current).toBeNull();
    expect(api.GET).not.toHaveBeenCalled();
  });

  it("does not ask, or answer, in Local Mode", async () => {
    localMode = true;

    const { result } = renderHook(() => useInstanceTelemetry(true), { wrapper: wrapper() });

    await act(async () => {});
    expect(result.current).toBeNull();
    expect(api.GET).not.toHaveBeenCalled();
  });
});

describe("ServerTelemetryRow", () => {
  it("explains that the same switch covers the web app, and shows the current state", () => {
    render(<ServerTelemetryRow telemetry={{ ...OFFERED, enabled: true }} />, { wrapper: wrapper() });

    expect(screen.getByText("Server error reports")).toBeTruthy();
    expect(
      screen.getByText(
        /The web app obeys the same switch\. Never includes tasks, lists or account details\./,
      ),
    ).toBeTruthy();
    expect(
      screen
        .getByRole("switch", { name: "Send error reports for this server and the web app" })
        .getAttribute("aria-checked"),
    ).toBe("true");
  });

  it("links the row to its guide topic with a name that says what it is about", () => {
    render(<ServerTelemetryRow telemetry={OFFERED} />, { wrapper: wrapper() });

    const help = screen.getByRole("link", { name: "About error reports" });
    expect(help.getAttribute("href")).toBe("/en/app/guide/crash-reports");
  });

  it("sends the new state through the shared API client and refreshes the query", async () => {
    api.PATCH.mockResolvedValue({ ...OFFERED, enabled: true, updatedAt: "2026-10-01T12:00:00.000Z" });
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

describe("ServerTelemetryStateRow", () => {
  it("shows the answer as information, with no control to press", () => {
    render(<ServerTelemetryStateRow telemetry={INSTANCE_OFF} />, { wrapper: wrapper() });

    expect(screen.getByText("Server error reports")).toBeTruthy();
    expect(screen.getByText("An admin decides this for the whole server.")).toBeTruthy();
    expect(screen.getByText("Off")).toBeTruthy();
    expect(screen.queryByRole("switch")).toBeNull();
  });

  it("shows a yes as plainly as a no", () => {
    render(
      <ServerTelemetryStateRow telemetry={{ ...INSTANCE_OFF, enabled: true }} />,
      { wrapper: wrapper() },
    );

    expect(screen.getByText("On")).toBeTruthy();
    expect(screen.queryByRole("switch")).toBeNull();
  });

  it("still links to the guide topic", () => {
    render(<ServerTelemetryStateRow telemetry={INSTANCE_OFF} />, { wrapper: wrapper() });

    expect(screen.getByRole("link", { name: "About error reports" }).getAttribute("href")).toBe(
      "/en/app/guide/crash-reports",
    );
  });
});

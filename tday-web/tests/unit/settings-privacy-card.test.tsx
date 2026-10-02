// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@/i18n";
import SettingsPage from "@/components/settings/SettingsPage";

/**
 * The Settings page around the two Privacy rows: when the card exists at all. The rows themselves
 * are covered in `settings-privacy.test.tsx`; what is checked here is the page's own decision —
 * this browser's row needs a build with a DSN, the server's row needs an admin, a server
 * workspace and a server that has a DSN of its own, and the card is there when either row is.
 */

const api = vi.hoisted(() => ({ GET: vi.fn(), PATCH: vi.fn() }));
vi.mock("@/lib/api-client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api-client")>()),
  api,
}));

let authUser: Record<string, unknown> | null = null;
vi.mock("@/providers/AuthProvider", () => ({
  useAuth: () => ({ user: authUser, refreshSession: vi.fn(), logout: vi.fn() }),
}));

vi.mock("@/providers/UserPreferencesProvider", () => ({
  useUserPreferences: () => ({ preferences: {}, updatePreferences: vi.fn() }),
}));

let localMode = false;
vi.mock("@/hooks/useAppMode", () => ({ useIsLocalMode: () => localMode }));

const CLIENT_DSN = "https://key@o1.ingest.example.invalid/2";
const SERVER_OFFERED = { dsnConfigured: true, enabled: false, updatedAt: null };

async function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/en/app/settings"]}>
        <Routes>
          <Route path="/:locale/app/settings" element={<SettingsPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
  await act(async () => {});
}

const privacyHeading = () => screen.queryByRole("heading", { name: "Privacy" });
const deviceRow = () => screen.queryByRole("switch", { name: "Send crash & problem reports when something fails" });
const serverRow = () =>
  screen.queryByRole("switch", { name: "Send this server's error reports to its operator's Sentry" });
const askedForServerSetting = () =>
  api.GET.mock.calls.some(([request]) => request?.url === "/api/admin/telemetry");

beforeEach(() => {
  vi.stubEnv("VITE_SENTRY_DSN", CLIENT_DSN);
  authUser = { id: "u1", name: "Taylor", role: "USER" };
  localMode = false;
  api.GET.mockReset();
  api.GET.mockImplementation(({ url }: { url: string }) =>
    Promise.resolve(url === "/api/admin/telemetry" ? SERVER_OFFERED : {}),
  );
  window.localStorage.clear();
  window.dispatchEvent(new StorageEvent("storage", { key: null }));
});

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
});

describe("the Privacy card on the Settings page", () => {
  it("holds only this browser's row for someone who is not an admin", async () => {
    await renderPage();

    expect(privacyHeading()).not.toBeNull();
    expect(deviceRow()).not.toBeNull();
    expect(serverRow()).toBeNull();
    expect(askedForServerSetting()).toBe(false);
  });

  it("holds both rows for an admin of a server that has its own DSN", async () => {
    authUser = { id: "u1", name: "Taylor", role: "ADMIN" };

    await renderPage();

    expect(privacyHeading()).not.toBeNull();
    expect(deviceRow()).not.toBeNull();
    expect(serverRow()).not.toBeNull();
  });

  it("leaves out the server's row when the server has no DSN", async () => {
    authUser = { id: "u1", name: "Taylor", role: "ADMIN" };
    api.GET.mockImplementation(({ url }: { url: string }) =>
      Promise.resolve(url === "/api/admin/telemetry" ? { ...SERVER_OFFERED, dsnConfigured: false } : {}),
    );

    await renderPage();

    expect(deviceRow()).not.toBeNull();
    expect(serverRow()).toBeNull();
  });

  it("leaves out the server's row, and never asks about it, in Local Mode", async () => {
    authUser = { id: "u1", name: "Taylor", role: "ADMIN" };
    localMode = true;

    await renderPage();

    expect(deviceRow()).not.toBeNull();
    expect(serverRow()).toBeNull();
    expect(askedForServerSetting()).toBe(false);
  });

  it("leaves out this browser's row, and keeps the card for the admin, when the build has no DSN", async () => {
    vi.stubEnv("VITE_SENTRY_DSN", "");
    authUser = { id: "u1", name: "Taylor", role: "ADMIN" };

    await renderPage();

    expect(privacyHeading()).not.toBeNull();
    expect(deviceRow()).toBeNull();
    expect(serverRow()).not.toBeNull();
  });

  it("is not on the page at all when there is nothing to switch", async () => {
    vi.stubEnv("VITE_SENTRY_DSN", "");

    await renderPage();

    expect(privacyHeading()).toBeNull();
    expect(deviceRow()).toBeNull();
    expect(serverRow()).toBeNull();
  });
});

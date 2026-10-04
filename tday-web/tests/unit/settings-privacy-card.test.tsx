// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@/i18n";
import SettingsPage from "@/components/settings/SettingsPage";

/**
 * The Settings page around the Privacy card. There is one setting on the web now: the admin's
 * instance-wide error-report answer. The admin gets the switch, a user gets the same answer as
 * read-only information, and a build that could not report at all gets neither — the card is on the
 * page exactly when there is something to say. The rows themselves are covered in
 * `settings-privacy.test.tsx`.
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
/** The viewer and the two endpoints this page asks about. */
const USER_ID = "u1";
const USER_NAME = "Taylor";
const ADMIN_ROLE = "ADMIN";
const ADMIN_TELEMETRY_URL = "/api/admin/telemetry";
const SENTRY_DSN_ENV = "VITE_SENTRY_DSN";
const SERVER_OFFERED = { dsnConfigured: true, enabled: false, updatedAt: null };
const SERVER_NO_DSN = { dsnConfigured: false, enabled: false, updatedAt: null };
const INSTANCE_ANSWER = { enabled: false, updatedAt: "2026-09-01T00:00:00.000Z" };

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
const serverSwitch = () =>
  screen.queryByRole("switch", { name: "Send error reports for this server and the web app" });
const stateOff = () => screen.queryByText("Off");
const askedForAdminSetting = () =>
  api.GET.mock.calls.some(([request]) => request?.url === ADMIN_TELEMETRY_URL);
const askedForInstanceAnswer = () =>
  api.GET.mock.calls.some(([request]) => request?.url === "/api/instance/telemetry");

beforeEach(() => {
  vi.stubEnv(SENTRY_DSN_ENV, CLIENT_DSN);
  authUser = { id: USER_ID, name: USER_NAME, role: "USER" };
  localMode = false;
  api.GET.mockReset();
  api.GET.mockImplementation(({ url }: { url: string }) =>
    Promise.resolve(
      url === ADMIN_TELEMETRY_URL
        ? SERVER_OFFERED
        : url === "/api/instance/telemetry"
          ? INSTANCE_ANSWER
          : {},
    ),
  );
  window.localStorage.clear();
});

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
});

describe("the Privacy card on the Settings page", () => {
  it("gives a non-admin the instance answer as information, and asks the public endpoint", async () => {
    await renderPage();

    expect(privacyHeading()).not.toBeNull();
    expect(serverSwitch()).toBeNull();
    expect(stateOff()).not.toBeNull();
    expect(askedForInstanceAnswer()).toBe(true);
    expect(askedForAdminSetting()).toBe(false);
  });

  it("gives an admin of a server that has its own DSN the switch", async () => {
    authUser = { id: USER_ID, name: USER_NAME, role: ADMIN_ROLE };

    await renderPage();

    expect(privacyHeading()).not.toBeNull();
    expect(serverSwitch()).not.toBeNull();
    expect(stateOff()).toBeNull();
    expect(askedForAdminSetting()).toBe(true);
  });

  it("has nothing to show an admin whose server has no DSN of its own", async () => {
    authUser = { id: USER_ID, name: USER_NAME, role: ADMIN_ROLE };
    api.GET.mockImplementation(({ url }: { url: string }) =>
      Promise.resolve(url === ADMIN_TELEMETRY_URL ? SERVER_NO_DSN : INSTANCE_ANSWER),
    );

    await renderPage();

    expect(privacyHeading()).toBeNull();
    expect(serverSwitch()).toBeNull();
  });

  it("is not on the page in Local Mode, where there is no server answer to read", async () => {
    authUser = { id: USER_ID, name: USER_NAME, role: ADMIN_ROLE };
    localMode = true;

    await renderPage();

    expect(privacyHeading()).toBeNull();
    expect(serverSwitch()).toBeNull();
    expect(askedForAdminSetting()).toBe(false);
    expect(askedForInstanceAnswer()).toBe(false);
  });

  it("keeps the admin's switch when the web build carries no DSN of its own", async () => {
    vi.stubEnv(SENTRY_DSN_ENV, "");
    authUser = { id: USER_ID, name: USER_NAME, role: ADMIN_ROLE };

    await renderPage();

    expect(privacyHeading()).not.toBeNull();
    expect(serverSwitch()).not.toBeNull();
  });

  it("is not on the page at all when neither the build nor the server can report", async () => {
    vi.stubEnv(SENTRY_DSN_ENV, "");
    authUser = { id: USER_ID, name: USER_NAME, role: ADMIN_ROLE };
    api.GET.mockImplementation(({ url }: { url: string }) =>
      Promise.resolve(url === ADMIN_TELEMETRY_URL ? SERVER_NO_DSN : INSTANCE_ANSWER),
    );

    await renderPage();

    expect(privacyHeading()).toBeNull();
    expect(serverSwitch()).toBeNull();
  });

  it("tells a non-admin nothing when this build could not report anyway", async () => {
    vi.stubEnv(SENTRY_DSN_ENV, "");

    await renderPage();

    expect(privacyHeading()).toBeNull();
    expect(askedForInstanceAnswer()).toBe(false);
  });
});

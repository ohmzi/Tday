// @vitest-environment jsdom

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@/i18n";
import OnboardingWizard from "@/components/onboarding/OnboardingWizard";
import { setAppMode } from "@/lib/local/appMode";
import {
  isOnboardingTelemetryHeld,
  setOnboardingTelemetryHold,
} from "@/lib/privacy/onboardingTelemetryHold";

/**
 * The wizard's last step, from the sign-in that makes it possible to the answer that ends it: an
 * admin signs in, the instance has never answered, and the question appears as the next step. The
 * hold is what keeps the auth guards from replacing the wizard with the app first.
 */

const api = vi.hoisted(() => ({ GET: vi.fn(), POST: vi.fn(), PATCH: vi.fn() }));
vi.mock("@/lib/api-client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api-client")>()),
  api,
}));

const login = vi.fn();
vi.mock("@/providers/AuthProvider", () => ({
  useAuth: () => ({
    login,
    refreshSession: vi.fn(),
    user: { id: "u1", role: "ADMIN", approvalStatus: "APPROVED" },
    authState: "authenticated",
  }),
}));

vi.mock("@/lib/security/clientCredentialEnvelope", () => ({
  createClientCredentialEnvelope: vi.fn(async () => ({ clientKey: "test", nonce: "test" })),
}));

const DSN = "https://key@o1.ingest.example.invalid/2";
const NEVER_ANSWERED = { dsnConfigured: true, enabled: false, updatedAt: null };

function renderWizard() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={["/en/login"]}>
        <Routes>
          <Route path="/:locale/login" element={<OnboardingWizard initialMode="signin" />} />
          <Route path="/:locale/app" element={<div>app</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  api.GET.mockReset();
  api.PATCH.mockReset();
  api.GET.mockResolvedValue(NEVER_ANSWERED);
  login.mockReset();
  login.mockResolvedValue({ ok: true });
  setAppMode("server");
  setOnboardingTelemetryHold(false);
  vi.stubEnv("VITE_SENTRY_DSN", DSN);
});

afterEach(() => {
  cleanup();
  setAppMode(null);
  setOnboardingTelemetryHold(false);
  vi.unstubAllEnvs();
});

describe("an admin signing in for the first time", () => {
  it("asks the instance question as the next step, and writes the answer", async () => {
    api.PATCH.mockResolvedValue({ ...NEVER_ANSWERED, updatedAt: "2026-10-01T12:00:00.000Z" });
    renderWizard();

    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "taylor" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "hunter2hunter2" } });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByRole("button", { name: "Don't send" })).toBeTruthy();
    expect(isOnboardingTelemetryHeld()).toBe(true);

    fireEvent.click(screen.getByRole("button", { name: "Don't send" }));

    await waitFor(() => expect(api.PATCH).toHaveBeenCalledWith(
      expect.objectContaining({ body: JSON.stringify({ enabled: false }) }),
    ));
    await waitFor(() => expect(isOnboardingTelemetryHeld()).toBe(false));
  });

  it("never holds, or asks, when the build cannot report at all", async () => {
    vi.stubEnv("VITE_SENTRY_DSN", "");
    renderWizard();

    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "taylor" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "hunter2hunter2" } });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByText("app")).toBeTruthy();
    expect(isOnboardingTelemetryHeld()).toBe(false);
    expect(screen.queryByRole("button", { name: "Don't send" })).toBeNull();
  });

  it("releases the hold when the sign-in itself fails", async () => {
    login.mockResolvedValue({ ok: false, message: "Invalid credentials" });
    renderWizard();

    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "taylor" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "wrong-password" } });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));

    expect(await screen.findByText("Invalid credentials")).toBeTruthy();
    expect(isOnboardingTelemetryHeld()).toBe(false);
  });

  it("releases the hold if the wizard goes away before the question is answered", async () => {
    const { unmount } = renderWizard();

    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "taylor" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "hunter2hunter2" } });
    fireEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("button", { name: "Send reports" })).toBeTruthy();
    expect(isOnboardingTelemetryHeld()).toBe(true);

    unmount();

    expect(isOnboardingTelemetryHeld()).toBe(false);
  });
});

// @vitest-environment jsdom

import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import "@/i18n";
import AuthLayout from "@/pages/AuthLayout";
import LandingPage from "@/pages/LandingPage";
import { setOnboardingTelemetryHold } from "@/lib/privacy/onboardingTelemetryHold";

/**
 * The admin's error-report question is asked after sign-in, and signing in is exactly what makes
 * these guards hand the browser to the app. While the hold is up they must keep rendering the
 * wizard, or the question is unmounted before it can be answered.
 */

let user: Record<string, unknown> | null = null;
let authState = "authenticated";
vi.mock("@/providers/AuthProvider", () => ({
  useAuth: () => ({ user, authState }),
}));

const APPROVED_ADMIN = { role: "ADMIN", approvalStatus: "APPROVED" };

function renderAuthLayout() {
  render(
    <MemoryRouter initialEntries={["/en/login"]}>
      <Routes>
        <Route path="/:locale/app" element={<div>app</div>} />
        <Route path="/:locale" element={<AuthLayout />}>
          <Route path="login" element={<div>wizard</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

function renderLanding() {
  render(
    <MemoryRouter initialEntries={["/en"]}>
      <Routes>
        <Route path="/:locale/app" element={<div>app</div>} />
        <Route path="/:locale/login" element={<div>login route</div>} />
        <Route path="/:locale" element={<LandingPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  user = APPROVED_ADMIN;
  authState = "authenticated";
  window.localStorage.clear();
  setOnboardingTelemetryHold(false);
});

afterEach(() => {
  cleanup();
  setOnboardingTelemetryHold(false);
});

describe("the auth guards while the admin's error-report step is up", () => {
  it("renders the wizard on the login route instead of the app", () => {
    setOnboardingTelemetryHold(true);

    renderAuthLayout();

    expect(screen.getByText("wizard")).toBeTruthy();
    expect(screen.queryByText("app")).toBeNull();
  });

  it("still hands an approved session to the app when no step is up", () => {
    renderAuthLayout();

    expect(screen.getByText("app")).toBeTruthy();
    expect(screen.queryByText("wizard")).toBeNull();
  });

  it("stays on the landing wizard rather than bouncing to login or the app", () => {
    setOnboardingTelemetryHold(true);

    renderLanding();

    // The wizard's own first step: LandingPage rendered it instead of navigating away.
    expect(screen.getByText("Choose your setup")).toBeTruthy();
    expect(screen.queryByText("app")).toBeNull();
    expect(screen.queryByText("login route")).toBeNull();
  });

  it("hands an approved landing visitor to the app when no step is up", () => {
    renderLanding();

    expect(screen.getByText("app")).toBeTruthy();
  });
});

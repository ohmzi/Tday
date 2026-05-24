// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import AuthLayout from "@/pages/AuthLayout";
import LandingPage from "@/pages/LandingPage";
import ProtectedRoute from "@/pages/ProtectedRoute";
import { useAuth } from "@/providers/AuthProvider";
import { RETURNING_BROWSER_STORAGE_KEY } from "@/lib/security/returningBrowser";

vi.mock("@/providers/AuthProvider", () => ({
  useAuth: vi.fn(),
}));

vi.mock("@/components/landing/OnboardingLanding", () => ({
  default: function OnboardingLanding() {
    return <div>Landing Screen</div>;
  },
}));

vi.mock("@/components/ui/sonner", () => ({
  SonnerToaster: function SonnerToaster() {
    return null;
  },
}));

type AuthState = ReturnType<typeof useAuth>;

const useAuthMock = vi.mocked(useAuth);

function createAuthState(overrides: Partial<AuthState> = {}): AuthState {
  return {
    user: null,
    authState: "unauthenticated",
    isLoading: false,
    isAuthenticated: false,
    login: vi.fn(),
    logout: vi.fn(),
    refreshSession: vi.fn(),
    ...overrides,
  };
}

function renderAuthLayout(initialEntry = "/en/login") {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/:locale" element={<AuthLayout />}>
          <Route path="login" element={<div>Login Screen</div>} />
          <Route path="register" element={<div>Register Screen</div>} />
        </Route>
        <Route path="/:locale/app/tday" element={<div>Home Screen</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function renderLandingPage(initialEntry = "/en") {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/:locale" element={<LandingPage />} />
        <Route path="/:locale/login" element={<div>Login Screen</div>} />
        <Route path="/:locale/app/tday" element={<div>Home Screen</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

function renderProtectedRoute(initialEntry = "/en/app/tday") {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/:locale/app" element={<ProtectedRoute />}>
          <Route path="tday" element={<div>Home Screen</div>} />
        </Route>
        <Route path="/:locale/login" element={<div>Login Screen</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("public auth route guards", () => {
  afterEach(() => {
    cleanup();
    window.localStorage.clear();
    vi.clearAllMocks();
  });

  it("keeps auth pages in a loading state while the session is resolving", () => {
    useAuthMock.mockReturnValue(createAuthState({ authState: "loading", isLoading: true }));

    const { container } = renderAuthLayout();

    expect(screen.queryByText("Login Screen")).toBeNull();
    expect(container.querySelector("svg.animate-spin")).not.toBeNull();
  });

  it("redirects approved authenticated users away from login", async () => {
    useAuthMock.mockReturnValue(
      createAuthState({
        authState: "authenticated",
        user: {
          id: "user-1",
          name: "Taylor",
          email: "taylor@example.com",
          role: "USER",
          approvalStatus: "APPROVED",
          timeZone: "UTC",
        },
        isAuthenticated: true,
      }),
    );

    renderAuthLayout();

    await waitFor(() => {
      expect(screen.queryByText("Home Screen")).not.toBeNull();
    });
    expect(screen.queryByText("Login Screen")).toBeNull();
  });

  it("keeps the landing page in a loading state while the session is resolving", () => {
    useAuthMock.mockReturnValue(createAuthState({ authState: "loading", isLoading: true }));

    const { container } = renderLandingPage();

    expect(screen.queryByText("Landing Screen")).toBeNull();
    expect(container.querySelector("svg.animate-spin")).not.toBeNull();
  });

  it("redirects authenticated users from landing to today", async () => {
    useAuthMock.mockReturnValue(
      createAuthState({
        authState: "authenticated",
        user: {
          id: "user-1",
          name: "Taylor",
          email: "taylor@example.com",
          role: "USER",
          approvalStatus: "APPROVED",
          timeZone: "UTC",
        },
        isAuthenticated: true,
      }),
    );

    renderLandingPage();

    await waitFor(() => {
      expect(screen.queryByText("Home Screen")).not.toBeNull();
    });
    expect(screen.queryByText("Landing Screen")).toBeNull();
  });

  it("keeps the landing page in a reconnecting state while auth is unavailable", () => {
    useAuthMock.mockReturnValue(createAuthState({ authState: "unavailable" }));

    const { container } = renderLandingPage();

    expect(screen.queryByText("Landing Screen")).toBeNull();
    expect(container.querySelector("svg.animate-spin")).not.toBeNull();
  });

  it("shows onboarding for a first-time unauthenticated browser", () => {
    useAuthMock.mockReturnValue(createAuthState());

    renderLandingPage();

    expect(screen.queryByText("Landing Screen")).not.toBeNull();
    expect(screen.queryByText("Login Screen")).toBeNull();
  });

  it("redirects returning unauthenticated browsers to login from landing", async () => {
    window.localStorage.setItem(RETURNING_BROWSER_STORAGE_KEY, "1");
    useAuthMock.mockReturnValue(createAuthState());

    renderLandingPage();

    await waitFor(() => {
      expect(screen.queryByText("Login Screen")).not.toBeNull();
    });
    expect(screen.queryByText("Landing Screen")).toBeNull();
  });

  it("keeps protected routes in a reconnecting state while auth is unavailable", () => {
    useAuthMock.mockReturnValue(createAuthState({ authState: "unavailable" }));

    const { container } = renderProtectedRoute();

    expect(screen.queryByText("Home Screen")).toBeNull();
    expect(container.querySelector("svg.animate-spin")).not.toBeNull();
  });
});

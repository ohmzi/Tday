// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import AppHomeRedirectPage from "@/pages/AppHomeRedirectPage";
import { useUserPreferences } from "@/providers/UserPreferencesProvider";
import { DefaultHomeScreen } from "@/types/enums";

vi.mock("@/providers/UserPreferencesProvider", () => ({
  useUserPreferences: vi.fn(),
}));

const useUserPreferencesMock = vi.mocked(useUserPreferences);

function renderAppHomeRedirect(initialEntry = "/en/app") {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/:locale/app" element={<AppHomeRedirectPage />} />
        <Route path="/:locale/app/tday" element={<div>Scheduled Task Home Screen</div>} />
        <Route path="/:locale/app/floater" element={<div>Floater Task Home Screen</div>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("app home redirect", () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it("shows a loading shell while preferences are still loading", () => {
    useUserPreferencesMock.mockReturnValue({
      preferences: null,
      updatePreferences: vi.fn(),
      isLoading: true,
      isPending: false,
    });

    renderAppHomeRedirect();

    expect(screen.queryByText("Scheduled Task Home Screen")).toBeNull();
    expect(screen.queryByText("Floater Task Home Screen")).toBeNull();
  });

  it("redirects to Scheduled when the preference is Scheduled", async () => {
    useUserPreferencesMock.mockReturnValue({
      preferences: {
        sortBy: null,
        groupBy: null,
        direction: null,
        aiSummaryEnabled: true,
        defaultHomeScreen: DefaultHomeScreen.scheduled,
      },
      updatePreferences: vi.fn(),
      isLoading: false,
      isPending: false,
    });

    renderAppHomeRedirect();

    await waitFor(() => {
      expect(screen.queryByText("Scheduled Task Home Screen")).not.toBeNull();
    });
    expect(screen.queryByText("Floater Task Home Screen")).toBeNull();
  });

  it("redirects to Floaters when the preference is Floater", async () => {
    useUserPreferencesMock.mockReturnValue({
      preferences: {
        sortBy: null,
        groupBy: null,
        direction: null,
        aiSummaryEnabled: true,
        defaultHomeScreen: DefaultHomeScreen.floater,
      },
      updatePreferences: vi.fn(),
      isLoading: false,
      isPending: false,
    });

    renderAppHomeRedirect();

    await waitFor(() => {
      expect(screen.queryByText("Floater Task Home Screen")).not.toBeNull();
    });
    expect(screen.queryByText("Scheduled Task Home Screen")).toBeNull();
  });

  it("defaults to Scheduled when preferences load with no explicit value", async () => {
    useUserPreferencesMock.mockReturnValue({
      preferences: null,
      updatePreferences: vi.fn(),
      isLoading: false,
      isPending: false,
    });

    renderAppHomeRedirect();

    await waitFor(() => {
      expect(screen.queryByText("Scheduled Task Home Screen")).not.toBeNull();
    });
  });
});

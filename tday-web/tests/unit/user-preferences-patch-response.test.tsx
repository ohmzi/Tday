// @vitest-environment jsdom

/**
 * The `/api/preferences` PATCH response, asserted through the provider that renders from it.
 *
 * The bug this pins: the route used to answer `{"message": "preferences updated"}` instead of
 * the stored record, and `onSuccess` wrote that body straight into the query cache. Every field
 * the body did not carry was filled with a default, so picking Floater slid the "Default home
 * screen" thumb across and then yanked it back to Scheduled on the next tick — the account had
 * the right value the whole time and the control would not show it.
 *
 * An acknowledgement-only body is still legal (Local Mode sends one from
 * `lib/local/localApi.ts`), so the rule is not "trust the response" or "trust the request" but
 * "a body carrying no preference fields changes nothing".
 */

import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const getMock = vi.fn();
const patchMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  api: {
    GET: (...args: unknown[]) => getMock(...args),
    PATCH: (...args: unknown[]) => patchMock(...args),
  },
}));

import { UserPreferencesProvider, useUserPreferences } from "@/providers/UserPreferencesProvider";
import { DefaultHomeScreen } from "@/types/enums";

function createQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

/** Reads the three fields that share the one cache entry the mutation overwrites. */
function Probe() {
  const { preferences, updatePreferences } = useUserPreferences();
  return (
    <div>
      <span data-testid="home">{preferences?.defaultHomeScreen ?? "loading"}</span>
      <span data-testid="ai">{preferences ? String(preferences.aiSummaryEnabled) : "loading"}</span>
      <span data-testid="sortBy">{preferences?.sortBy ?? "none"}</span>
      <button
        type="button"
        onClick={() => updatePreferences({ defaultHomeScreen: DefaultHomeScreen.floater })}
      >
        Floater
      </button>
    </div>
  );
}

function renderProbe(queryClient: QueryClient) {
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <UserPreferencesProvider>{children}</UserPreferencesProvider>
      </QueryClientProvider>
    );
  }
  return render(<Probe />, { wrapper: Wrapper });
}

const storedRecord = {
  sortBy: "createdAt",
  groupBy: null,
  direction: null,
  aiSummaryEnabled: false,
  defaultHomeScreen: DefaultHomeScreen.scheduled,
};

describe("the /api/preferences PATCH response", () => {
  afterEach(() => {
    cleanup();
    getMock.mockReset();
    patchMock.mockReset();
  });

  it("keeps the chosen home screen when the response only acknowledges the write", async () => {
    getMock.mockResolvedValue(storedRecord);
    patchMock.mockResolvedValue({ message: "preferences updated" });

    renderProbe(createQueryClient());

    await screen.findByText(DefaultHomeScreen.scheduled);
    fireEvent.click(screen.getByRole("button", { name: "Floater" }));

    await waitFor(() =>
      expect(screen.getByTestId("home").textContent).toBe(DefaultHomeScreen.floater),
    );
    // The spring-back arrived a tick AFTER the optimistic write, so one more turn of the event
    // loop is the only thing that would have caught it.
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(screen.getByTestId("home").textContent).toBe(DefaultHomeScreen.floater);
    // And the acknowledgement must not reset the unrelated fields sharing that cache entry.
    expect(screen.getByTestId("ai").textContent).toBe("false");
    expect(screen.getByTestId("sortBy").textContent).toBe("createdAt");
  });

  it("adopts the stored record when the response echoes one", async () => {
    getMock.mockResolvedValue({ ...storedRecord, defaultHomeScreen: DefaultHomeScreen.scheduled });
    patchMock.mockResolvedValue({
      ...storedRecord,
      defaultHomeScreen: DefaultHomeScreen.floater,
    });

    renderProbe(createQueryClient());

    await screen.findByText(DefaultHomeScreen.scheduled);
    fireEvent.click(screen.getByRole("button", { name: "Floater" }));

    await waitFor(() =>
      expect(screen.getByTestId("home").textContent).toBe(DefaultHomeScreen.floater),
    );
  });

  it("sends the home screen as the only field the user changed", async () => {
    getMock.mockResolvedValue(storedRecord);
    patchMock.mockResolvedValue(storedRecord);

    renderProbe(createQueryClient());

    await screen.findByText(DefaultHomeScreen.scheduled);
    fireEvent.click(screen.getByRole("button", { name: "Floater" }));

    await waitFor(() => expect(patchMock).toHaveBeenCalledTimes(1));
    expect(JSON.parse(patchMock.mock.calls[0][0].body)).toEqual({
      defaultHomeScreen: DefaultHomeScreen.floater,
    });
  });
});

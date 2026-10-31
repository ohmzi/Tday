// @vitest-environment jsdom

/**
 * Backing out of a list screen buzzes once, and only for list screens.
 *
 * Two paths leave a list: the header chevron, which is a click, and browser back
 * or the edge swipe, which never reaches one. The chevron announces its own exit
 * inside the tap, and the route hook is told to stand down for that same change;
 * the pop path has no click, so the hook is the one that buzzes it. Both are
 * pinned here, together with the rule that a plain (non-list) back stays silent,
 * as it does on both native clients.
 *
 * `@/lib/haptics` is mocked so the question is whether the screen-change helper
 * was asked, not what the device did with it. The user's own switch is honoured
 * inside that helper and is covered by `feedback-preferences.test.tsx`.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { Outlet, RouterProvider, createMemoryRouter } from "react-router-dom";
import { NativePageBackButton } from "@/components/app/NativePageHeader";
import { useListScreenHandoverHaptic } from "@/hooks/useListScreenHandoverHaptic";
import { hapticScreenChange } from "@/lib/haptics";

vi.mock("@/lib/haptics", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/haptics")>()),
  hapticScreenChange: vi.fn(),
}));

const buzzes = vi.mocked(hapticScreenChange);

/** The shell mounts the hook once above every route, as NativeAppShell does. */
function Shell() {
  useListScreenHandoverHaptic();
  return <Outlet />;
}

function renderAt(initial: string, history: string[]) {
  const router = createMemoryRouter(
    [
      {
        element: <Shell />,
        children: [
          { path: "/en/app/tday", element: <div>home</div> },
          {
            path: "/en/app/list/:id",
            element: <NativePageBackButton fallbackHref="/en/app/tday" />,
          },
          {
            path: "/en/app/floater-list/:id",
            element: <NativePageBackButton fallbackHref="/en/app/floater" />,
          },
          {
            path: "/en/app/settings",
            element: <NativePageBackButton fallbackHref="/en/app/tday" />,
          },
        ],
      },
    ],
    { initialEntries: history, initialIndex: history.indexOf(initial) },
  );
  render(<RouterProvider router={router} />);
  return router;
}

/** Tells NativePageBackButton it has in-app history to pop, as a real session does. */
function withHistoryIndex(idx: number) {
  window.history.replaceState({ ...window.history.state, idx }, "");
}

beforeEach(() => {
  buzzes.mockClear();
  withHistoryIndex(0);
});

afterEach(() => {
  cleanup();
  withHistoryIndex(0);
});

describe("list back haptic", () => {
  it("a scheduled list back chevron buzzes once, inside the tap", async () => {
    const router = renderAt("/en/app/list/abc", ["/en/app/tday", "/en/app/list/abc"]);

    fireEvent.click(screen.getByRole("button", { name: "Back" }));
    expect(buzzes).toHaveBeenCalledTimes(1);

    await act(async () => {});
    expect(router.state.location.pathname).toBe("/en/app/tday");
    expect(buzzes).toHaveBeenCalledTimes(1);
  });

  it("a floater list back chevron buzzes once, inside the tap", async () => {
    const router = renderAt("/en/app/floater-list/xyz", [
      "/en/app/tday",
      "/en/app/floater-list/xyz",
    ]);

    fireEvent.click(screen.getByRole("button", { name: "Back" }));
    expect(buzzes).toHaveBeenCalledTimes(1);

    await act(async () => {});
    expect(router.state.location.pathname).toBe("/en/app/floater");
    expect(buzzes).toHaveBeenCalledTimes(1);
  });

  it("a list back chevron that pops history buzzes once, not twice", async () => {
    withHistoryIndex(1);
    const router = renderAt("/en/app/list/abc", ["/en/app/tday", "/en/app/list/abc"]);

    fireEvent.click(screen.getByRole("button", { name: "Back" }));
    await act(async () => {});

    expect(router.state.location.pathname).toBe("/en/app/tday");
    expect(buzzes).toHaveBeenCalledTimes(1);
  });

  it("a plain screen's back chevron stays silent, as on native", async () => {
    const router = renderAt("/en/app/settings", ["/en/app/tday", "/en/app/settings"]);

    fireEvent.click(screen.getByRole("button", { name: "Back" }));
    await act(async () => {});

    expect(router.state.location.pathname).toBe("/en/app/tday");
    expect(buzzes).not.toHaveBeenCalled();
  });

  it("browser back out of a list, with no click, still buzzes once through the hook", async () => {
    const router = renderAt("/en/app/list/abc", ["/en/app/tday", "/en/app/list/abc"]);

    await act(async () => {
      router.navigate(-1);
    });

    expect(router.state.location.pathname).toBe("/en/app/tday");
    expect(buzzes).toHaveBeenCalledTimes(1);
  });
});

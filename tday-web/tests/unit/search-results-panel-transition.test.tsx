// @vitest-environment jsdom

/**
 * The mobile search-results panel's enter and exit, proved by the DOM.
 *
 * The panel was `{onSelectResult && hasQuery && <div …>}` and nothing else, so
 * it existed on one paint and did not on the next — over a calendar grid that
 * does not move, which is where a hard cut shows worst. A class-list assertion
 * alone cannot catch the half of this that matters: the exit could be declared
 * perfectly and still never run, because the node was already out of the
 * document. So these assert presence first — that the panel is still rendered
 * after the query clears, that it is marked as leaving while it is there, and
 * that it goes exactly when the transition it declares is over — and only then
 * the classes.
 *
 * `MobileSearchHeader` is the file the calendar's panel actually lives in; the
 * ledger row is filed under the calendar because `CalendarClient` is the only
 * screen that passes `onSelectResult` today.
 *
 * The query is driven by re-rendering rather than by clicking a button beside
 * the bar, deliberately: this header closes its own search on any click landing
 * outside it (see its `onDocumentClick`), which would collapse the whole field
 * and take the panel with it — a different path from the one under test.
 */

import { act, cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock("@/lib/haptics", () => ({
  hapticButtonTap: vi.fn(),
  hapticDismiss: vi.fn(),
}));

vi.mock("@/components/app/NativeAppBrandButton", () => ({
  default: () => <span data-testid="brand" />,
}));

vi.mock("@/components/app/NativePageHeader", () => ({
  NativePageBackButton: () => <button type="button">back</button>,
  nativePageBarClassName: "bar",
  nativePageBarDockedTitleClassName: "docked-title",
  nativePageBarTitleLayerClassName: "title-layer",
  nativePageHeaderMetrics: { contentFadeHeight: 24 },
  useNativePageBarResync: () => {},
}));

import MobileSearchHeader, {
  type SearchResultItem,
} from "@/components/ui/MobileSearchHeader";
import { SURFACE_TRANSITION_MS } from "@/lib/surfaceTransitionTiming";

const MATCHES: SearchResultItem[] = [
  { id: "a", title: "Buy milk", subtitle: "Mon, Jan 5" },
  { id: "b", title: "Milk the joke", subtitle: "Tue, Jan 6" },
];

/**
 * Mirrors the calendar: the result list is derived from the query, so clearing
 * the query empties it on the same tick — which is the thing the panel has to
 * keep painting through on its way out.
 */
function Panel({
  query,
  onSelectResult = () => {},
}: {
  query: string;
  onSelectResult?: (id: string) => void;
}) {
  return (
    <MobileSearchHeader
      searchQuery={query}
      onSearchChange={() => {}}
      results={query ? MATCHES : []}
      onSelectResult={onSelectResult}
    />
  );
}

const panel = () =>
  document.querySelector<HTMLElement>(".tday-surface-enter, .tday-surface-exit");

describe("the mobile search results panel", () => {
  beforeEach(() => {
    // No `shouldAdvanceTime` here, unlike most of its neighbours: this file
    // asserts that the panel is still present one tick BEFORE its exit is due,
    // and a clock that also moves with wall time turns that into a race the
    // suite loses whenever a machine is busy. Nothing here needs real time —
    // the query is driven by re-rendering.
    vi.useFakeTimers();
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it("arrives with an enter rather than simply being there", () => {
    const { rerender } = render(<Panel query="" />);
    expect(panel()).toBeNull();

    rerender(<Panel query="milk" />);

    const shown = panel();
    expect(shown).not.toBeNull();
    expect(shown?.className).toContain("tday-surface-enter");
    expect(shown?.className).not.toContain("tday-surface-exit");
    // A panel hanging off the field drops out of it rather than rising into it.
    expect(shown?.className).toContain("tday-surface-from-top");
    // One number, read twice: the CSS is handed the same value `useFadeUnmount`
    // is holding the node for.
    expect(shown?.style.animationDuration).toBe(`${SURFACE_TRANSITION_MS}ms`);
  });

  it("is still in the document on the frame the query clears", () => {
    const { rerender } = render(<Panel query="milk" />);
    expect(panel()).not.toBeNull();

    rerender(<Panel query="" />);

    // The assertion this file exists for. Under the bare conditional the panel
    // was already gone here, and no exit could ever have run.
    const leaving = panel();
    expect(leaving).not.toBeNull();
    expect(leaving?.className).toContain("tday-surface-exit");
    // A result tapped while it is fading would jump the calendar to a date the
    // user has stopped asking about.
    expect(leaving?.className).toContain("pointer-events-none");
  });

  it("keeps painting the results it had while it leaves", () => {
    const { rerender } = render(<Panel query="milk" />);
    expect(screen.getByText("Buy milk")).toBeTruthy();

    rerender(<Panel query="" />);

    // The caller's list is empty by now. Without the snapshot the panel would
    // spend its whole exit showing an empty state that was never true.
    expect(screen.getByText("Buy milk")).toBeTruthy();
    expect(screen.queryByText("noMatchingTasks")).toBeNull();
  });

  it("leaves once the exit it declares is over, and not before", async () => {
    const { rerender } = render(<Panel query="milk" />);
    rerender(<Panel query="" />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(SURFACE_TRANSITION_MS - 1);
    });
    expect(panel()).not.toBeNull();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(panel()).toBeNull();
  });

  it("retyping mid-exit goes straight back to entering", async () => {
    const { rerender } = render(<Panel query="milk" />);
    rerender(<Panel query="" />);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(SURFACE_TRANSITION_MS / 2);
    });
    rerender(<Panel query="milk" />);

    // The enter/exit choice reads the query, never the presence value, so a
    // panel caught mid-exit is arriving again rather than stuck fading out.
    expect(panel()?.className).toContain("tday-surface-enter");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(SURFACE_TRANSITION_MS);
    });
    expect(panel()).not.toBeNull();
  });

  it("skips the wait under prefers-reduced-motion", () => {
    const original = window.matchMedia;
    window.matchMedia = ((query: string) => ({
      matches: query.includes("prefers-reduced-motion"),
      media: query,
      addEventListener: () => {
        /* the hook reads `matches` once and never subscribes */
      },
      removeEventListener: () => {
        /* see above */
      },
    })) as unknown as typeof window.matchMedia;

    try {
      const { rerender } = render(<Panel query="milk" />);
      rerender(<Panel query="" />);
      // Nothing is animating, so lingering would be a stall rather than a
      // courtesy.
      expect(panel()).toBeNull();
    } finally {
      window.matchMedia = original;
    }
  });

  it("shows no panel at all on a screen that offers no jump-to-result", () => {
    // Most screens filter in place and pass no `onSelectResult`. None of them
    // gains a dropdown just because the panel learned to animate.
    render(
      <MobileSearchHeader
        searchQuery="milk"
        onSearchChange={() => {}}
        results={MATCHES}
      />,
    );
    expect(panel()).toBeNull();
    expect(screen.queryByText("Buy milk")).toBeNull();
  });
});

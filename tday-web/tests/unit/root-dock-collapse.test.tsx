// @vitest-environment jsdom

/**
 * The web dock's scroll collapse, and the dead band that keeps it still.
 *
 * The fold is a two-threshold decision, and the middle answer below is the
 * whole reason it is: at offset 30 the dock is COLLAPSED, because it was
 * collapsed at 46 and 30 is above the release edge. A symmetric comparison
 * says false there, and a finger resting anywhere near 44px drags the offset
 * back and forth across it once a frame — so the dock strobes between its two
 * shapes for as long as the thumb sits still. The same four answers are
 * asserted on Android in PR 184, which is fixing that defect on a client that
 * shipped without the band; web ships with it.
 *
 * The render half asserts classes and reachability rather than pixels, because
 * jsdom lays nothing out: what it CAN prove is that the folded tab is still in
 * the flow (a tab removed with `hidden` takes the transition with it and the
 * dock snaps shut), that it cannot take a tap, and that a tap on a folded dock
 * expands it instead of doing what the tab under the finger does.
 *
 * The pill is the exception, and it has to be: where the pill lands is a
 * measurement, and "does it re-measure when the dock folds" cannot be asked of a
 * DOM whose every rect is zero. So the last case hands the component a layout —
 * one derived from the classes the component itself rendered, not a fixed script,
 * so a tab that stopped folding would move the expected answer with it — and
 * asks the pill where it went.
 */

import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// Hoisted, because `vi.mock` factories run before the module body and a plain
// `const` above them is still in its temporal dead zone when they do.
const { push, scrollTo, route } = vi.hoisted(() => ({
  push: vi.fn(),
  scrollTo: vi.fn(),
  // Mutable because the pill's defect only shows on the Anytime feed: the active
  // tab there is the SECOND one, so it is the only one the fold moves.
  route: { pathname: "/en/app/tday" },
}));

vi.mock("@/lib/navigation", () => ({
  usePathname: () => route.pathname,
  useRouter: () => ({ push }),
}));

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
  // `nativeScreenTheme` reaches the route config, which reaches `src/i18n`, so
  // the mock has to carry the plugin that file installs as well as the hook this
  // one reads. Labels come back as their keys: what is asserted here is which
  // tab a class landed on, and a translation would only make that harder to see.
  initReactI18next: { type: "3rdParty", init: () => {} },
}));

vi.mock("@/lib/haptics", () => ({ hapticTick: vi.fn() }));

vi.mock("@/lib/scroll", () => ({ scrollTo }));

import RootDock from "@/components/app/RootDock";
import {
  ROOT_DOCK_COLLAPSE_PX,
  ROOT_DOCK_EXPAND_PX,
  nextRootDockCollapsed,
} from "@/lib/rootDockCollapse";

/** Folds an offset series through the decision the way a scroll stream does. */
function fold(offsets: number[]): boolean[] {
  let previous = false;
  return offsets.map((offset) => {
    previous = nextRootDockCollapsed(previous, offset);
    return previous;
  });
}

function renderDock(collapsed: boolean) {
  return render(
    <RootDock onOpenMore={() => {}} moreOpen={false} collapsed={collapsed} />,
  );
}

// The capsule's own inset (`p-1.5` plus its 1px border), the tab's resting
// width, the gap between two tabs, and the pill's `left-1.5`, which `updatePill`
// subtracts back out of every translate. Four numbers, all of them the dock's own
// spelling read off `RootDock.tsx` — the point is not their absolute value but
// that the pill's answer is a function of which tabs are still open.
const NAV_INSET_PX = 7;
const TAB_WIDTH_PX = 48;
const TAB_GAP_PX = 4;
const PILL_INSET_PX = 6;

/**
 * Gives the dock a layout, derived from the classes the dock itself rendered.
 *
 * A folded tab is measured at zero because the component took its taps away, not
 * because this function was told which tab to fold — so a fold that stopped
 * happening moves the expected answer rather than quietly agreeing with a stale
 * script. `more` is zero at every state: it is `hidden sm:grid`, and this is a
 * phone-width dock.
 */
function stubDockLayout() {
  const order = ["scheduledTaskHome", "root_feed_tab_floater", "more"];
  const widthOf = (label: string) => {
    if (label === "more") return 0;
    const button = screen.queryByLabelText(label);
    if (!button || button.className.includes("pointer-events-none")) return 0;
    return TAB_WIDTH_PX;
  };
  const leftOf = (label: string) => {
    let left = NAV_INSET_PX;
    for (const other of order) {
      if (other === label) break;
      const width = widthOf(other);
      if (width) left += width + TAB_GAP_PX;
    }
    return left;
  };
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (
    this: HTMLElement,
  ) {
    const label = this.tagName === "NAV" ? null : this.getAttribute("aria-label");
    const left = label ? leftOf(label) : 0;
    const width = label ? widthOf(label) : 0;
    return {
      x: left,
      y: 0,
      left,
      top: 0,
      right: left + width,
      bottom: TAB_WIDTH_PX,
      width,
      height: TAB_WIDTH_PX,
      toJSON: () => ({}),
    } as DOMRect;
  });
}

/** The wrapper that carries the fold; the button is what the label finds. */
function tabWrapper(label: string): HTMLElement {
  const button = screen.getByLabelText(label);
  const wrapper = button.parentElement;
  if (!wrapper) throw new Error(`no wrapper around ${label}`);
  return wrapper;
}

beforeEach(() => {
  push.mockClear();
  scrollTo.mockClear();
  route.pathname = "/en/app/tday";
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe("nextRootDockCollapsed", () => {
  it("holds the collapse through the dead band on the way back up", () => {
    expect(fold([40, 46, 30, 20])).toEqual([false, true, true, false]);
  });

  it("does not flip on a single pixel either side of the fold", () => {
    expect(nextRootDockCollapsed(false, ROOT_DOCK_COLLAPSE_PX)).toBe(false);
    expect(nextRootDockCollapsed(false, ROOT_DOCK_COLLAPSE_PX + 1)).toBe(true);
    expect(nextRootDockCollapsed(true, ROOT_DOCK_EXPAND_PX)).toBe(false);
    expect(nextRootDockCollapsed(true, ROOT_DOCK_EXPAND_PX + 1)).toBe(true);
  });

  it("reads a rubber-banded or unmeasured scroller as the top of the feed", () => {
    // Both are the same claim: the feed is at its top. A negative offset is the
    // bounce past it, and NaN is a scroller that has not laid out. Neither is
    // clamped and neither needs to be — both edges are positive, so both inputs
    // lose both comparisons on their own. What this pins is the CONTRACT, which
    // the hook leans on at every route change: an offset that is not a real
    // position expands the dock rather than holding the last answer. A
    // `Number.isNaN(offset) ? previous : …` written here later would read like a
    // kindness and would fail this.
    expect(nextRootDockCollapsed(true, -120)).toBe(false);
    expect(nextRootDockCollapsed(true, Number.NaN)).toBe(false);
  });
});

describe("RootDock, folded", () => {
  it("leaves the unselected tab in the flow and takes its tap away", () => {
    const { rerender } = renderDock(false);
    expect(tabWrapper("root_feed_tab_floater").className).toContain("grid-cols-[1fr]");
    expect(screen.getByLabelText("root_feed_tab_floater").className).not.toContain(
      "pointer-events-none",
    );

    rerender(<RootDock onOpenMore={() => {}} moreOpen={false} collapsed />);

    const wrapper = tabWrapper("root_feed_tab_floater");
    expect(wrapper.className).toContain("grid-cols-[0fr]");
    expect(wrapper.className).toContain("opacity-0");
    // Still laid out — `hidden` would delete the transition along with the tab.
    expect(wrapper.classList.contains("hidden")).toBe(false);
    const button = screen.getByLabelText("root_feed_tab_floater");
    expect(button.className).toContain("pointer-events-none");
    // Load-bearing, not cosmetic: a `0fr` track is floored by its item's own
    // minimum size, so the tab has to give up `min-w-12` at both breakpoints or
    // the capsule closes to 48px and stops there. `cn` is the thing dropping
    // them, and an ordering change in that call would take the collapse with it.
    expect(button.className).toContain("min-w-0");
    expect(button.className).toContain("sm:min-w-0");
    expect(button.className).not.toContain("min-w-12");
    // And the other half of the same floor: `box-sizing: border-box` will not let
    // a box be used narrower than its own padding, so `px-3` surviving the fold
    // parks a 24px stub of every hidden tab inside the capsule — measured in
    // Chromium, a mobile dock closing to 86px instead of 62px, with the stub
    // sitting beside the one tab that is left.
    expect(button.className).toContain("px-0");
    expect(button.className).not.toContain("px-3");
  });

  it("spends a tap on opening itself rather than on the tab under the finger", () => {
    renderDock(true);
    fireEvent.click(screen.getByLabelText("scheduledTaskHome"));

    expect(push).not.toHaveBeenCalled();
    expect(scrollTo).not.toHaveBeenCalled();
    expect(tabWrapper("root_feed_tab_floater").className).toContain("grid-cols-[1fr]");
  });

  it("takes the pill with it when the capsule closes around the second tab", () => {
    // The Anytime feed, where the active tab is the one with a tab to its LEFT:
    // that tab closing slides the active one to the capsule's inset, and a pill
    // that only re-measures on a selection change never hears about it. Left
    // there it does not lag and recover — it holds the open dock's offset for as
    // long as the dock stays folded, which puts most of it outside a capsule that
    // clips, and the rest beside the tab it is supposed to be marking.
    route.pathname = "/en/app/floater";
    stubDockLayout();
    const { rerender } = renderDock(false);
    const pill = screen.getByLabelText("Primary app navigation")
      .firstElementChild as HTMLElement;

    expect(pill.style.transform).toBe(
      `translateX(${NAV_INSET_PX + TAB_WIDTH_PX + TAB_GAP_PX - PILL_INSET_PX}px)`,
    );

    rerender(<RootDock onOpenMore={() => {}} moreOpen={false} collapsed />);

    expect(pill.style.transform).toBe(`translateX(${NAV_INSET_PX - PILL_INSET_PX}px)`);
  });

  it("gives the tap-expanded dock back after its dwell", () => {
    vi.useFakeTimers();
    renderDock(true);
    fireEvent.click(screen.getByLabelText("scheduledTaskHome"));
    expect(tabWrapper("root_feed_tab_floater").className).toContain("grid-cols-[1fr]");

    act(() => {
      vi.advanceTimersByTime(2500);
    });

    expect(tabWrapper("root_feed_tab_floater").className).toContain("grid-cols-[0fr]");
  });
});

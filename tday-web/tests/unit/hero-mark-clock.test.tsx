// @vitest-environment jsdom

/**
 * The sun/moon mark turns over on its own.
 *
 * The hour used to be read during render, which is only ever as fresh as the
 * last render — and this header is mounted for the life of the tab, with
 * nothing that guarantees one: a session left open across 18:00 kept the sun
 * up. It reads as a motion bug (the mark that never changes) and it is a
 * sampling one, so what is pinned here is the sampling. iOS reads the glyph off
 * `TimelineView(.periodic(from: .now, by: 60))` and Android polls the same
 * minute, so all three turn over on the same boundary.
 *
 * Nothing animates when it fires, and nothing should: the change happens once a
 * day while nobody is looking at the header.
 */

import { act, cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import RootFeedHeroHeader, {
  type RootFeedHeroMark,
} from "@/components/app/RootFeedHeroHeader";

const A_MINUTE = 60_000;

let containers: HTMLElement[] = [];

const baseProps = {
  title: "Today",
  searchOpen: false,
  searchQuery: "",
  searchPlaceholder: "Search tasks",
  searchPlaceholderShort: "Search",
  searchAriaLabel: "Search tasks",
  createListAriaLabel: "Create list",
  settingsAriaLabel: "More",
  onSearchQueryChange: () => {},
  onSearchOpenChange: () => {},
  onCreateList: () => {},
  onOpenSettings: () => {},
};

/**
 * The mark glyph is the only icon the header gives an inline colour to, which
 * is a sturdier handle than a class name the icon library owns — and the colour
 * is half of the day/night switch anyway.
 */
function markGlyph(container: HTMLElement): SVGElement {
  const glyph = Array.from(container.querySelectorAll("svg")).find(
    (svg) => svg.style.color !== "",
  );
  if (!glyph) throw new Error("no mark glyph in the rendered header");
  return glyph;
}

function renderAt(date: Date, mark: RootFeedHeroMark = "timeOfDay") {
  vi.setSystemTime(date);
  const container = document.createElement("div");
  document.body.appendChild(container);
  containers.push(container);
  render(<RootFeedHeroHeader {...baseProps} mark={mark} />, { container });
  return container;
}

/** Wind the wall clock forward and let the poll notice. */
function tickTo(date: Date) {
  vi.setSystemTime(date);
  act(() => {
    vi.advanceTimersByTime(A_MINUTE);
  });
}

beforeEach(() => {
  containers = [];
  vi.useFakeTimers();
});

afterEach(() => {
  cleanup();
  for (const container of containers) container.remove();
  vi.useRealTimers();
});

describe("root feed hero mark clock", () => {
  it("swaps the sun for the moon when the hour crosses out of the daytime band", () => {
    const container = renderAt(new Date(2026, 8, 13, 17, 59));
    const day = markGlyph(container).getAttribute("class");

    tickTo(new Date(2026, 8, 13, 18, 0));

    // The band is 06:00–17:59 on all three clients — Swift `(6..<18).contains`,
    // Kotlin `in 6..17`, this one `>= 6 && < 18`.
    expect(markGlyph(container).getAttribute("class")).not.toBe(day);
  });

  it("holds the glyph steady inside the band, rather than re-rendering on every tick", () => {
    const container = renderAt(new Date(2026, 8, 13, 9, 0));
    const morning = markGlyph(container).getAttribute("class");

    tickTo(new Date(2026, 8, 13, 9, 1));

    expect(markGlyph(container).getAttribute("class")).toBe(morning);
  });

  it("gives the Floater's leaf no clock at all, since it never changes", () => {
    renderAt(new Date(2026, 8, 13, 17, 59), "floaterLeaf");

    // A timer per mounted header that can only ever set the state it already
    // holds is a wake-up the leaf has no use for.
    expect(vi.getTimerCount()).toBe(0);
  });
});

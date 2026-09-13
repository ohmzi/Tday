// @vitest-environment jsdom

/**
 * The calendar card is exactly as tall as the page it is showing, and the four
 * pages it can show are four different heights: a 35-day month, a 42-day
 * month, a week strip and a day summary. Every chevron, swipe, Today jump and
 * view change therefore resizes the card — and it used to do that in the frame
 * the slide started, so the content travelled for 320ms over a container that
 * had already finished.
 *
 * What that regression looks like in a diff is nothing at all: the pager
 * renders the right page either way, and a screenshot of the settled card is
 * identical. The two things that make it a motion rather than a jump are
 * structural, and this file pins both — the pager is inside the measured box
 * and the header is not, and the box is handed a NUMBER that keeps following
 * the content after mount.
 *
 * `CalendarModeCard` is exported for this, the way `CalendarTaskRow` already
 * is: rendering `CalendarClient` to reach it would put four queries, a drag
 * context and a task list between the test and the one box it is about.
 */

import { act, cleanup, render, screen } from "@testing-library/react";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CalendarModeCard } from "@/features/calendar/component/CalendarClient";

/**
 * What the pager reports as its own height. jsdom lays nothing out and answers
 * 0 for every `scrollHeight`, so without this every assertion below would be
 * comparing "0px" to "0px" and would pass on a card that had stopped measuring.
 */
let contentHeight = 0;

/** jsdom has no `ResizeObserver`, and the box does not measure without one. */
class StubResizeObserver {
  static live: StubResizeObserver[] = [];
  readonly observed: Element[] = [];

  constructor(readonly callback: () => void) {
    StubResizeObserver.live.push(this);
  }

  observe(element: Element) {
    this.observed.push(element);
  }

  disconnect() {}
}

const originalScrollHeight = Object.getOwnPropertyDescriptor(
  HTMLElement.prototype,
  "scrollHeight",
);

beforeEach(() => {
  contentHeight = 0;
  StubResizeObserver.live = [];
  vi.stubGlobal("ResizeObserver", StubResizeObserver);
  Object.defineProperty(HTMLElement.prototype, "scrollHeight", {
    configurable: true,
    get: () => contentHeight,
  });
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  if (originalScrollHeight) {
    Object.defineProperty(HTMLElement.prototype, "scrollHeight", originalScrollHeight);
  }
});

/** The element the slide plays on, and the only thing the card animates. */
const pagerOf = (container: HTMLElement) =>
  container.querySelector<HTMLElement>(".touch-pan-y")!;
/** The box in the layout, which carries the height transition. */
const boxOf = (container: HTMLElement) => pagerOf(container).parentElement!.parentElement!;

function renderCard(props: { animationKey: number; view?: "month" | "week" | "day" }) {
  return render(
    <CalendarModeCard
      view={props.view ?? "month"}
      selectedDate={new Date("2026-08-22T10:00:00.000Z")}
      tasksByDay={new Map()}
      slideDirection="right"
      animationKey={props.animationKey}
      canGoPrevious
      onNavigate={vi.fn()}
      onSelectDate={vi.fn()}
    />,
  );
}

describe("the calendar card's height follows the page it is showing", () => {
  it("measures the pager and writes the height onto the box", () => {
    contentHeight = 384;
    const { container } = renderCard({ animationKey: 0 });

    expect(boxOf(container).style.height).toBe("384px");
    expect(StubResizeObserver.live[0].observed).toEqual([pagerOf(container).parentElement]);
  });

  it("keeps the header outside the box, so it stays anchored while the card resizes", () => {
    // AGENTS.md's Calendar UX Contract, and the reason the box wraps the pager
    // rather than the card: a month title that grew and shrank with the grid
    // under it would be the card redrawing, not a page turning.
    contentHeight = 384;
    const { container } = renderCard({ animationKey: 0 });

    expect(boxOf(container).contains(screen.getByRole("heading"))).toBe(false);
    expect(boxOf(container).contains(pagerOf(container))).toBe(true);
  });

  it("follows the new page's height instead of keeping the one it mounted with", () => {
    // A 42-day month arriving where a 35-day one was. The measurement has to be
    // retaken on the swap — this is the assertion that fails if the box ever
    // goes back to measuring once.
    contentHeight = 384;
    const { container, rerender } = renderCard({ animationKey: 0 });

    contentHeight = 440;
    rerender(
      <CalendarModeCard
        view="month"
        selectedDate={new Date("2026-09-22T10:00:00.000Z")}
        tasksByDay={new Map()}
        slideDirection="right"
        animationKey={1}
        canGoPrevious
        onNavigate={vi.fn()}
        onSelectDate={vi.fn()}
      />,
    );
    act(() => StubResizeObserver.live[0].callback());

    expect(boxOf(container).style.height).toBe("440px");
  });

  it("plays the slide and the resize on one clock", () => {
    // Both halves change geometry, so both are Emphasis by `docs/motion.md`'s
    // second idiom rule — and being the same length is what makes them one page
    // turn rather than a slide followed by a card still growing. jsdom applies
    // no stylesheet, so the pager's class says which animation runs and only
    // the stylesheet says how long for; the two reads together are the claim.
    contentHeight = 384;
    const { container } = renderCard({ animationKey: 0 });

    expect(pagerOf(container).classList.contains("cal-native-slide-from-right")).toBe(true);
    expect(boxOf(container).classList.contains("duration-emphasis")).toBe(true);

    const styles = readFileSync(
      resolve(__dirname, "..", "..", "src/features/calendar/style/calendar-styles.css"),
      "utf8",
    );
    for (const rule of ["cal-native-slide-from-left", "cal-native-slide-from-right"]) {
      expect(styles).toContain(
        `.${rule} {\n  animation: ${rule.replace("-from-", "-in-")} var(--tday-duration-emphasis)`,
      );
    }
  });

  it("leaves the day cells room to paint outside themselves", () => {
    // The box clips, and a clip is the one thing a height animation cannot do
    // without: a card growing while the content it uncovers spills past it is
    // not an animation of anything. But nothing on this path clipped before,
    // so three things that legitimately paint outside the pager's measured
    // content would have started being sliced — the drag-over ring (4px on
    // every side, and in week view the cells are flush with the box), the
    // selected day's glow (6px below the bottom row), and a month cell, which
    // is a fixed width inside a seventh of the card and hangs past its last
    // column on a small phone. jsdom lays nothing out, so the classes are the
    // claim here the same way they are for the slide above: the sides are
    // bought by bleeding the clip box out to the card's padding edge, the top
    // and bottom by padding inside it, because padding on the box itself would
    // come out of the measured height.
    contentHeight = 384;
    const { container } = renderCard({ animationKey: 0 });

    const box = boxOf(container);
    expect(box.classList.contains("overflow-hidden")).toBe(true);
    for (const bleed of ["-mx-4", "px-4", "sm:-mx-5", "sm:px-5"]) {
      expect(box.classList.contains(bleed)).toBe(true);
    }
    for (const pad of ["pt-1", "pb-1.5"]) {
      expect(pagerOf(container).classList.contains(pad)).toBe(true);
    }
  });

  it("measures a view change too, not only a page change", () => {
    // Month to week is the biggest height change the card makes, and it arrives
    // through a different call path (`changeView`) than the chevrons do.
    contentHeight = 384;
    const { container, rerender } = renderCard({ animationKey: 0 });

    contentHeight = 96;
    rerender(
      <CalendarModeCard
        view="day"
        selectedDate={new Date("2026-08-22T10:00:00.000Z")}
        tasksByDay={new Map()}
        slideDirection="left"
        animationKey={1}
        canGoPrevious
        onNavigate={vi.fn()}
        onSelectDate={vi.fn()}
      />,
    );
    act(() => StubResizeObserver.live[0].callback());

    expect(boxOf(container).style.height).toBe("96px");
  });
});

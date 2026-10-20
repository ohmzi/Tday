// @vitest-environment jsdom

/**
 * The Earlier header answers the tap that opened it, and it has to do that
 * before the bucket is open.
 *
 * On the way open the hand-off holds `expanded`/`collapsed` where they were for
 * the length of the scene's exit above (`useEarlierExpandHandoff`), so a header
 * that draws itself from that flag alone is pixel-identical for the whole wait
 * — which is a tap that reads as dropped, and gets made again. `expanding` is
 * the half that says "your tap landed"; these tests pin that both headers read
 * it, because the two are separate components drawing what has to look like one
 * control (`TodayEarlierSection` is deliberately outside the dnd machinery —
 * see its own doc comment).
 *
 * jsdom applies no stylesheet, so what can be asserted is the class the turn
 * hangs off and the fact that there is ONE glyph to turn. Two swapped glyphs
 * render identically here and animate nothing in a browser.
 */

import { cleanup, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import TimelineSectionDroppable from "@/components/todo/dnd/TimelineSectionDroppable";
import TodayEarlierSection from "@/features/todayTodos/component/TodayEarlierSection";
import type { TimelineSection } from "@/lib/timeline/buildTimelineSections";

afterEach(cleanup);

const earlierSection: TimelineSection = {
  key: "earlier",
  label: "Earlier",
  kind: "earlier",
  targetDayKey: null,
  collapsible: true,
  dayDiff: null,
  todos: [],
};

/** The header's chevron — the only glyph either header draws. */
function chevronOf(container: HTMLElement): SVGElement {
  const glyphs = container.querySelectorAll("svg");
  expect(glyphs).toHaveLength(1);
  return glyphs[0];
}

const turned = (container: HTMLElement) => chevronOf(container).classList.contains("rotate-90");

describe("TodayEarlierSection's header chevron", () => {
  // `expanded` stays false while the rows are still not there, so the body is
  // never mounted in these renders — which is also why they need no providers.
  it("points along the header while the bucket is shut", () => {
    const { container } = render(
      <TodayEarlierSection todos={[]} label="Earlier" expanded={false} onToggle={() => {}} />,
    );

    expect(turned(container)).toBe(false);
  });

  it("turns during the hand-off, before `expanded` has caught up with the tap", () => {
    const { container } = render(
      <TodayEarlierSection
        todos={[]}
        label="Earlier"
        expanded={false}
        expanding
        onToggle={() => {}}
      />,
    );

    expect(turned(container)).toBe(true);
  });

  it("carries a transition on the rung the turn is timed at", () => {
    // The class list rather than a computed style, because jsdom has no
    // stylesheet: without this the glyph would still be the right way up at
    // both ends and would simply snap between them, which is the defect with
    // its own fix removed.
    const { container } = render(
      <TodayEarlierSection todos={[]} label="Earlier" expanded={false} onToggle={() => {}} />,
    );

    const chevron = chevronOf(container);
    expect(chevron.classList.contains("transition-transform")).toBe(true);
    expect(chevron.classList.contains("duration-quick")).toBe(true);
    expect(chevron.classList.contains("motion-reduce:transition-none")).toBe(true);
  });
});

describe("TimelineSectionDroppable's Earlier header chevron", () => {
  it("points along the header while the bucket is shut", () => {
    const { container } = render(
      <TimelineSectionDroppable section={earlierSection} collapsed onToggleCollapse={() => {}}>
        <p>row</p>
      </TimelineSectionDroppable>,
    );

    expect(turned(container)).toBe(false);
  });

  it("turns during the hand-off, before `collapsed` has caught up with the tap", () => {
    const { container } = render(
      <TimelineSectionDroppable section={earlierSection} collapsed expanding onToggleCollapse={() => {}}>
        <p>row</p>
      </TimelineSectionDroppable>,
    );

    expect(turned(container)).toBe(true);
  });

  it("stays turned once the bucket is actually open", () => {
    const { container } = render(
      <TimelineSectionDroppable
        section={earlierSection}
        collapsed={false}
        onToggleCollapse={() => {}}
      >
        <p>row</p>
      </TimelineSectionDroppable>,
    );

    expect(turned(container)).toBe(true);
  });
});

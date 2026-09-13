// @vitest-environment jsdom

/**
 * The calendar cannot page before the current month, and until now a back swipe
 * at that floor did nothing whatsoever: the gesture was received, measured,
 * compared against the rule and dropped without a frame of feedback. A gesture
 * answered with nothing is indistinguishable from a gesture that never arrived,
 * which is the one thing an app must never make a user guess about.
 *
 * What jsdom can hold of the answer is its structure, and the structure is most
 * of the decision. The refusal has to play on an element that is NOT the pager:
 * the pager owns the slide, `animation` is a single property, and a refusal
 * declared there would supersede the slide and then re-add it on the way out,
 * replaying a page turn nobody asked for.
 *
 * The flag's own clock is `useNavigationRefusal`'s and is tested there.
 *
 * The clock is the stylesheet's, so the stylesheet is read for it — the same
 * split `calendar-pager-height.test.tsx` makes: the class says which animation
 * runs, the CSS says how long for, and the two reads together are the claim.
 *
 * `CalendarModeCard` is exported for tests like this one; rendering
 * `CalendarClient` to reach it would put four queries, a drag context and a
 * task list between the test and the one wrapper it is about. The cost of that
 * choice is that `refusedBack` arrives here from a test rather than from the
 * screen, so the seam between the two is somebody else's job:
 * `calendar-client-motion-wiring.test.tsx` renders the whole screen and presses
 * the arrow key for exactly that reason.
 */

import { cleanup, fireEvent, render } from "@testing-library/react";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CalendarModeCard } from "@/features/calendar/component/CalendarClient";

/** Mirrors `swipeThreshold` in CalendarClient.tsx. */
const SWIPE_THRESHOLD = 48;

const STYLES = readFileSync(
  resolve(__dirname, "..", "..", "src/features/calendar/style/calendar-styles.css"),
  "utf8",
);

afterEach(cleanup);

/** The element the slide plays on. */
const pagerOf = (container: HTMLElement) =>
  container.querySelector<HTMLElement>(".touch-pan-y")!;
/** The measured height box, between the pager and the refusal's wrapper. */
const boxOf = (container: HTMLElement) => pagerOf(container).parentElement!.parentElement!;
/** The element the refusal plays on. */
const wrapperOf = (container: HTMLElement) => boxOf(container).parentElement!;

function renderCard(props: { refusedBack: boolean }) {
  const onNavigate = vi.fn();
  const view = render(
    <CalendarModeCard
      view="month"
      selectedDate={new Date("2026-09-12T10:00:00.000Z")}
      tasksByDay={new Map()}
      slideDirection="right"
      animationKey={0}
      canGoPrevious={false}
      refusedBack={props.refusedBack}
      onNavigate={onNavigate}
      onSelectDate={vi.fn()}
    />,
  );
  return { ...view, onNavigate };
}

describe("the calendar answers a page it refuses to turn", () => {
  it("draws nothing extra while nothing has been refused", () => {
    const { container } = renderCard({ refusedBack: false });

    expect(container.querySelector(".cal-native-page-refused")).toBeNull();
  });

  it("plays the refusal above the height box, not on the pager that slides", () => {
    const { container } = renderCard({ refusedBack: true });

    expect(wrapperOf(container).classList.contains("cal-native-page-refused")).toBe(true);
    // The load-bearing half: one element, one `animation`. If the refusal ever
    // moves down onto the pager it will take the slide's slot while it plays
    // and hand it back when it stops, and the card will turn a page the user
    // did not ask for every time it declines one.
    expect(pagerOf(container).classList.contains("cal-native-page-refused")).toBe(false);
    expect(pagerOf(container).classList.contains("cal-native-slide-from-right")).toBe(true);
  });

  it("still hands a back swipe at the floor to the screen that owns the rule", () => {
    // The card does not decide what is refusable. It reports the gesture and is
    // told to play the answer, which is what lets one refusal cover the arrow
    // keys as well as the swipe — both arrive at `navigatePeriod`.
    const { container, onNavigate } = renderCard({ refusedBack: false });

    const pager = pagerOf(container);
    fireEvent.pointerDown(pager, { pointerId: 1, clientX: 100 });
    fireEvent.pointerUp(pager, { pointerId: 1, clientX: 100 + SWIPE_THRESHOLD + 1 });

    expect(onNavigate).toHaveBeenCalledWith(-1);
  });

  it("answers on Quick, which is shorter than the page turn it declined", () => {
    // Quick is the rung for the app answering a finger that is on it. It also
    // has to be visibly shorter than the Emphasis a real page turn takes, or
    // the refusal reads as a page that started to turn and changed its mind.
    expect(STYLES).toContain(
      ".cal-native-page-refused {\n  animation: cal-native-page-refuse var(--tday-duration-quick)",
    );
    expect(STYLES).toContain(
      ".cal-native-slide-from-left {\n  animation: cal-native-slide-in-left var(--tday-duration-emphasis)",
    );
  });

  it("has a reduced-motion answer, which is no answer at all", () => {
    // A refusal ends where it began — there is no finished state to draw
    // instead of playing, which makes it the one motion the fifth idiom rule
    // leaves nothing of. The flag is not even raised in that case, so this is
    // belt and braces; `CalendarClient` carries the argument for the other
    // half, and the two together are what keep a class in the DOM that no
    // `animationend` would come to clear.
    const reduced = STYLES.slice(STYLES.lastIndexOf("@media (prefers-reduced-motion: reduce)"));
    expect(reduced).toContain(".cal-native-page-refused {\n    animation: none;\n  }");
  });
});

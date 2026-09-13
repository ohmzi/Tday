// @vitest-environment jsdom

/**
 * The calendar's page swipe used to record an x on `pointerdown` and jump a page
 * on `pointerup`, with nothing moving in between: a slideshow being operated
 * rather than a surface being dragged. This is the file that says the grid is
 * under the finger for the whole gesture, and that every way the gesture can end
 * puts it back.
 *
 * It renders `CalendarModeCard` rather than a harness of its own, because half
 * of what is being asserted is structural and a harness cannot hold it: the drag
 * has to be written to a CHILD of the element that slides. A filling CSS
 * animation outranks an inline style, so the page that arrived on
 * `cal-native-slide-from-*` holds its own `transform` at `translateX(0)` for as
 * long as it lives — a drag written there would be silently ignored on every
 * page but the first the card ever drew, and no unit test of a hook in isolation
 * would ever see it. `calendar-swipe-pointer-capture.test.tsx` keeps the
 * harness, because the exits it covers are about the pointer rather than the
 * page.
 *
 * jsdom runs no transitions, so what a release can be asked here is which
 * transition the page is sent home under — the token string, or `none` — and the
 * stylesheet's own rungs are read in `calendar-floor-refusal.test.tsx`.
 */

import { cleanup, fireEvent, render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CalendarModeCard } from "@/features/calendar/component/CalendarClient";
import { installReducedMotion } from "../setup/reduced-motion";

/** Mirrors `swipeThreshold` in CalendarClient.tsx. */
const SWIPE_THRESHOLD = 48;

/** The page going home, spelled exactly as `useCalendarPagerSwipe` spells it. */
const RETURN_TRANSITION = "transform var(--tday-duration-quick) var(--tday-ease-gesture)";

const originalMatchMedia = window.matchMedia;

afterEach(() => {
  cleanup();
  window.matchMedia = originalMatchMedia;
});

/** The element the slide plays on, and the one the handlers sit on. */
const pagerOf = (container: HTMLElement) =>
  container.querySelector<HTMLElement>(".touch-pan-y")!;
/** The element the finger moves, which is the pager's own child. */
const trackOf = (container: HTMLElement) => pagerOf(container).firstElementChild as HTMLElement;

/** How far the page has actually travelled, in px. */
function offsetOf(container: HTMLElement): number {
  const match = /translateX\((-?[\d.]+)px\)/.exec(trackOf(container).style.transform);
  return match ? Number(match[1]) : 0;
}

function renderCard(props: { canGoPrevious?: boolean } = {}) {
  const onNavigate = vi.fn();
  const view = render(
    <CalendarModeCard
      view="month"
      selectedDate={new Date("2026-09-12T10:00:00.000Z")}
      tasksByDay={new Map()}
      slideDirection="right"
      animationKey={0}
      canGoPrevious={props.canGoPrevious ?? true}
      refusedBack={false}
      onNavigate={onNavigate}
      onSelectDate={vi.fn()}
    />,
  );
  return { ...view, onNavigate };
}

/** A finger going down on the grid, away from any day cell's button. */
function press(container: HTMLElement, clientX: number, clientY = 200) {
  fireEvent.pointerDown(pagerOf(container), { pointerId: 1, clientX, clientY });
}

function move(container: HTMLElement, clientX: number, clientY = 200) {
  fireEvent.pointerMove(pagerOf(container), { pointerId: 1, clientX, clientY });
}

function lift(container: HTMLElement, clientX: number, clientY = 200) {
  fireEvent.pointerUp(pagerOf(container), { pointerId: 1, clientX, clientY });
}

describe("the calendar page follows the finger", () => {
  it("moves the grid one pixel per pixel while the swipe is still a question", () => {
    // Every pixel up to the threshold is the user deciding, and a page that lags
    // there is the app arguing with a finger it should be following.
    const { container } = renderCard();

    press(container, 300);
    move(container, 268);

    expect(trackOf(container).style.transform).toBe("translateX(-32px)");
    // No clock between the finger and the page: that is the whole defect.
    expect(trackOf(container).style.transition).toBe("none");
  });

  it("writes the drag below the element that slides, never on it", () => {
    // The load-bearing half, and the one only a real card can show. The pager
    // carries `cal-native-slide-from-*`, whose `both` fill holds its transform
    // at translateX(0) for the life of the element — a drag written there would
    // be ignored on every page but the first one the card ever drew.
    const { container } = renderCard();

    press(container, 300);
    move(container, 268);

    expect(pagerOf(container).classList.contains("cal-native-slide-from-right")).toBe(true);
    expect(pagerOf(container).style.transform).toBe("");
    expect(trackOf(container).style.transform).toBe("translateX(-32px)");
  });

  it("gives rather than tracks once the swipe has passed the threshold", () => {
    // Web keeps one page in the DOM — the thing that lets the card measure a
    // single height — so past the threshold there is nothing left to uncover.
    // The pull keeps being answered, at a price, against a limit it never
    // reaches, instead of promising a neighbour that is not there.
    const { container } = renderCard();

    press(container, 300);
    move(container, 100);

    const travelled = Math.abs(offsetOf(container));
    expect(travelled).toBeGreaterThan(SWIPE_THRESHOLD);
    expect(travelled).toBeLessThan(SWIPE_THRESHOLD * 2);
    expect(travelled).toBeLessThan(200 / 2);
  });

  it("barely gives at all in a direction the floor has refused", () => {
    // The chevron says the page behind this one does not exist by being
    // disabled; the gesture says it by never travelling far enough to read as a
    // page about to turn. The answer to the refusal is still the screen's.
    const { container, onNavigate } = renderCard({ canGoPrevious: false });

    press(container, 100);
    move(container, 300);

    const travelled = offsetOf(container);
    expect(travelled).toBeGreaterThan(0);
    expect(travelled).toBeLessThanOrEqual(SWIPE_THRESHOLD / 2);

    lift(container, 300);
    expect(onNavigate).toHaveBeenCalledWith(-1);
    // And it glides home, because nothing is about to replace this element: the
    // refusal the screen plays is an answer, not a page turn.
    expect(trackOf(container).style.transform).toBe("");
    expect(trackOf(container).style.transition).toBe(RETURN_TRANSITION);
  });

  it("hands a turned page over to the slide instead of gliding under it", () => {
    // The page that turns is replaced by the one that displaced it, and that
    // arrival is the continuation of this gesture — Emphasis, on the same
    // Gesture curve the drag was tracked with. A trip home underneath it would
    // be a second page turn nobody asked for.
    const { container, onNavigate } = renderCard();

    press(container, 300);
    move(container, 200);
    lift(container, 200);

    expect(onNavigate).toHaveBeenCalledWith(1);
    expect(trackOf(container).style.transform).toBe("");
    expect(trackOf(container).style.transition).toBe("none");
  });

  it("glides the page home when the swipe stops short", () => {
    const { container, onNavigate } = renderCard();

    press(container, 300);
    move(container, 270);
    lift(container, 270);

    expect(onNavigate).not.toHaveBeenCalled();
    expect(trackOf(container).style.transform).toBe("");
    expect(trackOf(container).style.transition).toBe(RETURN_TRANSITION);
  });

  it("puts the page back when the platform takes the gesture away", () => {
    // A cancelled gesture is an abandoned one, not a quiet commit.
    const { container, onNavigate } = renderCard();

    press(container, 300);
    move(container, 150);
    expect(offsetOf(container)).toBeLessThan(0);

    fireEvent.pointerCancel(pagerOf(container), { pointerId: 1, clientX: 150 });

    expect(onNavigate).not.toHaveBeenCalled();
    expect(trackOf(container).style.transform).toBe("");
    expect(trackOf(container).style.transition).toBe(RETURN_TRANSITION);
  });

  it("never moves the page for a drag that belongs to the scroller", () => {
    // The grid sits directly above a scrolling task list, so a finger that means
    // to scroll very often lands here first. The axis locks on the first move
    // that says what it meant, and a vertical one takes the release with it.
    const { container, onNavigate } = renderCard();

    press(container, 300, 200);
    move(container, 296, 320);
    expect(trackOf(container).style.transform).toBe("");

    lift(container, 200, 320);
    expect(onNavigate).not.toHaveBeenCalled();
  });

  it("keeps the tracking under reduced motion and drops the trip home", () => {
    // The preference is about motion the app plays, not about the movement a
    // finger is making — the same reason it does not switch scrolling off. What
    // it removes is the trip: the instant the finger leaves, everything it moved
    // is put back in the frame that asks for it.
    installReducedMotion(true);
    const { container } = renderCard();

    press(container, 300);
    move(container, 268);
    expect(trackOf(container).style.transform).toBe("translateX(-32px)");

    lift(container, 268);
    expect(trackOf(container).style.transform).toBe("");
    expect(trackOf(container).style.transition).toBe("none");
  });
});

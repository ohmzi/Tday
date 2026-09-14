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

import { cleanup, createEvent, fireEvent, render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CalendarModeCard } from "@/features/calendar/component/CalendarClient";
import { installReducedMotion } from "../setup/reduced-motion";

/** Mirrors `swipeThreshold` in CalendarClient.tsx. */
const SWIPE_THRESHOLD = 48;

/** The page going home — `SWIPE_SETTLE_HOME`, spelled exactly as the module has it. */
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
/** A day cell, which in month view is where nearly every thumb actually lands. */
const dayCellOf = (container: HTMLElement) => trackOf(container).querySelector("button")!;
/** S M T W T F S — the first seven-column grid on the page, and a header. */
const weekdayRowOf = (container: HTMLElement) =>
  container.querySelector<HTMLElement>(".grid.grid-cols-7")!;

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

/**
 * One pointer event at a time the test chose, which is the only way to put a
 * speed on a gesture here: everything fired in a single tick carries jsdom's own
 * stamps, a fraction of a millisecond apart, and the sampler reads a gap that
 * short as no evidence at all. `timeStamp` is readonly on the prototype, so it
 * is defined on the instance — React's synthetic event copies it across, except
 * for a stamp of exactly zero, which it replaces with the wall clock.
 */
function at(
  container: HTMLElement,
  kind: "pointerDown" | "pointerMove" | "pointerUp",
  clientX: number,
  t: number,
) {
  const pager = pagerOf(container);
  const event = createEvent[kind](pager, { pointerId: 1, clientX, clientY: 200 });
  Object.defineProperty(event, "timeStamp", { value: t });
  fireEvent(pager, event);
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

  it("starts the drag wherever the thumb lands, day cells included", () => {
    // The whole gesture is worth nothing if it can only be started in the 8px
    // gaps between rows: a month grid is seven columns of day-cell buttons with
    // no horizontal gap at all, so that is where a thumb goes down. Tap or
    // swipe is settled by where the finger goes afterwards, which is the only
    // place a pager can settle it and still be draggable across its own cells.
    const { container } = renderCard();
    const cell = dayCellOf(container);

    fireEvent.pointerDown(cell, { pointerId: 1, clientX: 300, clientY: 200 });
    fireEvent.pointerMove(cell, { pointerId: 1, clientX: 268, clientY: 200 });

    expect(trackOf(container).style.transform).toBe("translateX(-32px)");
  });

  it("leaves the weekday row behind when the grid moves", () => {
    // AGENTS.md's Calendar UX Contract: in month view the month title and the
    // weekday row do not slide with the date grid. The labels are derived from
    // today and the locale rather than from the selected date, so every page
    // draws the same seven letters — carrying them under the thumb says nothing
    // and then says it backwards.
    const { container } = renderCard();
    const weekdays = weekdayRowOf(container);
    expect(weekdays.children).toHaveLength(7);
    // Outside the pager, which is the element that carries the page turn, and
    // therefore outside the track inside it that carries the drag.
    expect(pagerOf(container).contains(weekdays)).toBe(false);

    press(container, 300);
    move(container, 268);

    expect(trackOf(container).style.transform).toBe("translateX(-32px)");
    expect(weekdays.style.transform).toBe("");
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

    // Stamped, and stopped before the lift, because "stops short" is a statement
    // about distance and the release is no longer decided on distance alone: a
    // finger still travelling at 30px would be the flick two tests below, and
    // left to the wall clock this one is whichever of the two the runner's load
    // happens to make it.
    at(container, "pointerDown", 300, 1000);
    at(container, "pointerMove", 270, 1020);
    at(container, "pointerUp", 270, 1200);

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

  it("hands the page to a second finger rather than stranding it", () => {
    // Two thumbs on a phone, which is all this takes. The page is taken over
    // rather than abandoned: an arrival that merely ended the gesture underneath
    // it would leave the grid parked where that gesture had dragged it with
    // nothing left listening to put it back — and nothing would come along
    // later, because selecting a date does not re-key the pager and so never
    // replaces the element holding the offset.
    const { container, onNavigate } = renderCard();

    press(container, 300);
    move(container, 268);
    expect(offsetOf(container)).toBe(-32);

    const cell = dayCellOf(container);
    fireEvent.pointerDown(cell, { pointerId: 2, clientX: 268, clientY: 200 });
    // Everything the first finger does from here belongs to a gesture that is
    // no longer the page's: its capture goes, then its move, then its release.
    fireEvent.lostPointerCapture(pagerOf(container), { pointerId: 1 });
    move(container, 200);
    lift(container, 200);
    expect(offsetOf(container)).toBe(-32);

    fireEvent.pointerUp(cell, { pointerId: 2, clientX: 268, clientY: 200 });

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

  it("turns the page on a flick that never reached the threshold", () => {
    // How most people turn a page that is a whole grid wide: a short, fast
    // throw. 28px is well short of the 48 the old rule demanded, and the page
    // turns anyway, because the finger was plainly still going when it left.
    const { container, onNavigate } = renderCard();

    at(container, "pointerDown", 300, 1000);
    at(container, "pointerMove", 272, 1020);
    expect(Math.abs(offsetOf(container))).toBeLessThan(SWIPE_THRESHOLD);
    at(container, "pointerUp", 272, 1020);

    expect(onNavigate).toHaveBeenCalledWith(1);
  });

  it("refuses a press that jittered but never locked an axis", () => {
    // Contact jitter, not a swipe: 7px is inside the 8px lock, so no move ever
    // said which way this gesture meant to go. The projection is what makes that
    // dangerous — 7px in 20ms carries 52px further in the 150ms a release is
    // worth, clearing a threshold the finger itself came nowhere near — and an
    // unlocked gesture is precisely the one the sampler timed across the whole
    // press rather than across its last 100ms. In month view the same touch is a
    // tap on a day cell, so the user would get a date selected and a month
    // turned by one press.
    const { container, onNavigate } = renderCard();

    at(container, "pointerDown", 300, 1000);
    at(container, "pointerMove", 293, 1016);
    at(container, "pointerUp", 293, 1020);

    expect(onNavigate).not.toHaveBeenCalled();
    // And nothing was written under the finger either: a press that stayed
    // inside the slop leaves the grid exactly as it found it.
    expect(trackOf(container).style.transform).toBe("");
  });

  it("refuses a long drag that was already being walked back", () => {
    // The same mistake from the other side. The finger is 60px from where it
    // started — past the threshold — but spent the last 60ms travelling the
    // other way, which is a gesture that changed its mind before it ended.
    const { container, onNavigate } = renderCard();

    at(container, "pointerDown", 300, 1000);
    at(container, "pointerMove", 200, 1400);
    at(container, "pointerMove", 240, 1440);
    at(container, "pointerUp", 240, 1460);

    expect(onNavigate).not.toHaveBeenCalled();
    expect(trackOf(container).style.transition).toBe(RETURN_TRANSITION);
  });

  it("keeps the tracking under reduced motion and drops the trip home", () => {
    // The preference is about motion the app plays, not about the movement a
    // finger is making — the same reason it does not switch scrolling off. What
    // it removes is the trip: the instant the finger leaves, everything it moved
    // is put back in the frame that asks for it.
    installReducedMotion(true);
    const { container } = renderCard();

    // Stamped so the release is undecided for certain. A turned page also leaves
    // `transition: none` behind — it hands over to the incoming slide rather than
    // gliding — so an unstamped lift that the wall clock read as a flick would
    // pass these two assertions without the preference having done anything.
    at(container, "pointerDown", 300, 1000);
    at(container, "pointerMove", 268, 1020);
    expect(trackOf(container).style.transform).toBe("translateX(-32px)");

    at(container, "pointerUp", 268, 1200);
    expect(trackOf(container).style.transform).toBe("");
    expect(trackOf(container).style.transition).toBe("none");
  });
});

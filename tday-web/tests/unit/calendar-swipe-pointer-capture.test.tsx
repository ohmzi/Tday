// @vitest-environment jsdom

/**
 * Both calendar swipes are gestures that used to have exactly one way to end —
 * a clean release on the element that started them — and no answer at all for
 * the ways a real device ends a gesture instead: a finger lifted past the
 * element's edge, or a touch the platform takes away mid-drag for a scroll, an
 * edge back-swipe or an incoming call.
 *
 * The page swipe leaked its tracking refs: nothing cleared them but `pointerup`
 * on the card, so a gesture that ended anywhere else left them saying "a swipe
 * is in progress and it started 300 px ago", and the next bare `pointerup` the
 * card saw — which need not belong to any swipe of the card's — was measured
 * against that dead origin and paged the calendar by itself.
 *
 * The row swipe leaked the flag that switches its CSS transition off while a
 * finger is down: a cancelled touch never reached `touchend`, so the row stayed
 * parked at whatever offset the finger last reached with its transition still
 * disabled — frozen half-open rather than animating home.
 *
 * These tests pin the two exits that close both: pointer capture plus
 * `pointercancel` on the pager, and a cancel path on the row. The harnesses
 * below mirror the real call sites in `CalendarClient.tsx` — the same
 * threshold, the same actions width, the same `transition: none` while
 * swiping — so an assertion about `style.transition` here is an assertion about
 * what the row actually does on screen.
 */

import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render } from "@testing-library/react";
import { useCalendarPagerSwipe } from "@/features/calendar/lib/useCalendarPagerSwipe";
import { useCalendarRowSwipe } from "@/features/calendar/lib/useCalendarRowSwipe";

/** Mirrors `swipeThreshold` in CalendarClient.tsx. */
const SWIPE_THRESHOLD = 48;
/** Mirrors `ACTIONS_WIDTH` in CalendarTaskRow. */
const ACTIONS_WIDTH = 210;

afterEach(cleanup);

/**
 * jsdom implements neither `setPointerCapture` nor `releasePointerCapture`, so
 * they are installed as spies — which is also how the test reads whether the
 * card asked for capture at all, and gave it back afterwards.
 */
function installCaptureSpies(element: HTMLElement) {
  const setPointerCapture = vi.fn();
  const releasePointerCapture = vi.fn();
  Object.assign(element, { setPointerCapture, releasePointerCapture });
  return { setPointerCapture, releasePointerCapture };
}

function PagerHarness({ onNavigate }: { onNavigate: (offset: -1 | 1) => void }) {
  const { trackRef, swipeHandlers } = useCalendarPagerSwipe(SWIPE_THRESHOLD, onNavigate, true);
  return (
    <div data-testid="card" {...swipeHandlers}>
      {/* The card's two elements, in the order the real one has them: the
          handlers and the capture on the outer, the drag on the inner. What the
          page does under the finger is `calendar-pager-tracks-finger.test.tsx`;
          what this file is about is how the gesture ends. */}
      <div ref={trackRef}>
        {/* A day cell: the card bails out of tracking on any press that lands on
            a button, because capture would otherwise steal the button's click. */}
        <button type="button" data-testid="day">
          7
        </button>
      </div>
    </div>
  );
}

function renderPager() {
  const onNavigate = vi.fn();
  const view = render(<PagerHarness onNavigate={onNavigate} />);
  const card = view.getByTestId("card");
  return { onNavigate, card, day: view.getByTestId("day"), ...installCaptureSpies(card) };
}

describe("calendar page swipe — pointer capture and cancel", () => {
  it("a swipe past the threshold still pages the calendar", () => {
    const { card, onNavigate } = renderPager();

    fireEvent.pointerDown(card, { pointerId: 1, clientX: 300 });
    fireEvent.pointerUp(card, { pointerId: 1, clientX: 300 - SWIPE_THRESHOLD - 1 });

    expect(onNavigate).toHaveBeenCalledWith(1);
  });

  it("takes pointer capture on the way down and hands it back on the way up", () => {
    const { card, setPointerCapture, releasePointerCapture } = renderPager();

    fireEvent.pointerDown(card, { pointerId: 4, clientX: 300 });
    // Capture is what makes a release past the card's edge arrive here at all,
    // instead of being delivered to whatever is under the finger and lost.
    expect(setPointerCapture).toHaveBeenCalledWith(4);

    fireEvent.pointerUp(card, { pointerId: 4, clientX: 320 });
    expect(releasePointerCapture).toHaveBeenCalledWith(4);
  });

  it("a cancelled gesture releases capture and does not page, however far it travelled", () => {
    const { card, onNavigate, releasePointerCapture } = renderPager();

    fireEvent.pointerDown(card, { pointerId: 2, clientX: 300 });
    // The platform taking the pointer away is not a decision the user made, so
    // it commits nothing even though the cancel lands a page-width away.
    fireEvent.pointerCancel(card, { pointerId: 2, clientX: 40 });

    expect(onNavigate).not.toHaveBeenCalled();
    expect(releasePointerCapture).toHaveBeenCalledWith(2);
  });

  it("a cancelled gesture leaves no origin behind for the next release to be measured against", () => {
    const { card, onNavigate } = renderPager();

    // The finger goes down on the card and the gesture is then taken away —
    // dragged off the edge and cancelled by the platform.
    fireEvent.pointerDown(card, { pointerId: 3, clientX: 300 });
    fireEvent.pointerCancel(card, { pointerId: 3, clientX: 300 });

    // Now a release that belongs to nothing: a drag that began elsewhere and
    // happened to finish over the calendar, arriving with no `pointerdown` of
    // its own. Before the cancel handler existed, this was measured against the
    // stale 300 and paged the calendar on its own.
    fireEvent.pointerUp(card, { pointerId: 3, clientX: 420 });

    expect(onNavigate).not.toHaveBeenCalled();
  });

  it("ignores a release belonging to a different pointer", () => {
    const { card, onNavigate } = renderPager();

    fireEvent.pointerDown(card, { pointerId: 1, clientX: 300 });
    // A second finger landing and lifting mid-gesture must not commit — nor end
    // — the gesture the first one is still making.
    fireEvent.pointerUp(card, { pointerId: 9, clientX: 100 });
    expect(onNavigate).not.toHaveBeenCalled();

    fireEvent.pointerUp(card, { pointerId: 1, clientX: 400 });
    expect(onNavigate).toHaveBeenCalledWith(-1);
  });

  it("never captures a press that lands on a day cell, and never pages from one", () => {
    const { day, onNavigate, setPointerCapture } = renderPager();

    fireEvent.pointerDown(day, { pointerId: 1, clientX: 300 });
    // Capture retargets the click that follows a press, so taking it here would
    // cost the day cell its own tap.
    expect(setPointerCapture).not.toHaveBeenCalled();

    fireEvent.pointerUp(day, { pointerId: 1, clientX: 100 });
    expect(onNavigate).not.toHaveBeenCalled();
  });

  it("losing capture without a cancel — the capturing node replaced mid-gesture — also resets", () => {
    const { card, onNavigate } = renderPager();

    fireEvent.pointerDown(card, { pointerId: 5, clientX: 300 });
    // The card's inner element is keyed by its animation key and is replaced on
    // every navigation; a capture dropped that way reports `lostpointercapture`
    // and no `pointercancel` at all.
    fireEvent.lostPointerCapture(card, { pointerId: 5 });
    fireEvent.pointerUp(card, { pointerId: 5, clientX: 420 });

    expect(onNavigate).not.toHaveBeenCalled();
  });
});

function RowHarness({ onOpen }: { onOpen: () => void }) {
  const { swipeX, swiping, swipeHandlers } = useCalendarRowSwipe(ACTIONS_WIDTH, onOpen);
  return (
    <div
      data-testid="row"
      {...swipeHandlers}
      // The real row's style, verbatim: `swiping` is what switches the
      // transition off so the row can track the finger, which is exactly what a
      // cancelled gesture used to leave switched off forever.
      style={{
        transform: `translateX(${swipeX}px)`,
        transition: swiping ? "none" : "transform 220ms ease",
      }}
    />
  );
}

function renderRow() {
  const onOpen = vi.fn();
  const view = render(<RowHarness onOpen={onOpen} />);
  return { onOpen, row: view.getByTestId("row") };
}

function touch(clientX: number, clientY = 0) {
  return { touches: [{ clientX, clientY }] };
}

describe("calendar row swipe — a cancelled touch is an exit, not a freeze", () => {
  it("a horizontal drag tracks the finger with its transition switched off", () => {
    const { row, onOpen } = renderRow();

    fireEvent.touchStart(row, touch(200, 50));
    fireEvent.touchMove(row, touch(100, 52));

    expect(row.style.transform).toBe("translateX(-100px)");
    expect(row.style.transition).toBe("none");
    // Locking onto the horizontal axis is what closes every other open row.
    expect(onOpen).toHaveBeenCalledTimes(1);
  });

  it("a cancelled touch restores the transition and returns the row to where the gesture found it", () => {
    const { row } = renderRow();

    fireEvent.touchStart(row, touch(200, 50));
    fireEvent.touchMove(row, touch(100, 52));
    expect(row.style.transform).toBe("translateX(-100px)");

    // The platform takes the touch away. Before this path existed the row stayed
    // at -100px with `transition: none` — visibly stuck, not slow.
    fireEvent.touchCancel(row);

    expect(row.style.transition).toBe("transform 220ms ease");
    expect(row.style.transform).toBe("translateX(0px)");
  });

  it("cancelling a gesture that began on an open row returns it to open, not closed", () => {
    const { row } = renderRow();

    // Open it for real first: a drag past half the actions width, then a lift.
    fireEvent.touchStart(row, touch(300, 50));
    fireEvent.touchMove(row, touch(300 - ACTIONS_WIDTH / 2 - 10, 50));
    fireEvent.touchEnd(row);
    expect(row.style.transform).toBe(`translateX(-${ACTIONS_WIDTH}px)`);

    // Now a second gesture that starts closing it and is cancelled part-way. A
    // cancel abandons the gesture; it does not quietly commit the half of it
    // that happened to get made.
    fireEvent.touchStart(row, touch(100, 50));
    fireEvent.touchMove(row, touch(150, 50));
    expect(row.style.transform).toBe(`translateX(-${ACTIONS_WIDTH - 50}px)`);

    fireEvent.touchCancel(row);

    expect(row.style.transform).toBe(`translateX(-${ACTIONS_WIDTH}px)`);
    expect(row.style.transition).toBe("transform 220ms ease");
  });

  it("a pointercancel resets the row just as a touchcancel does", () => {
    const { row } = renderRow();

    fireEvent.touchStart(row, touch(200, 50));
    fireEvent.touchMove(row, touch(120, 52));
    expect(row.style.transition).toBe("none");

    // The same interruption reported by the other event family; a device may
    // deliver either, and the reset is idempotent so both is fine too.
    fireEvent.pointerCancel(row, { pointerId: 1 });

    expect(row.style.transition).toBe("transform 220ms ease");
    expect(row.style.transform).toBe("translateX(0px)");
  });

  it("a lift still settles to the nearer edge", () => {
    const { row } = renderRow();

    // Short of halfway: the row closes again.
    fireEvent.touchStart(row, touch(300, 50));
    fireEvent.touchMove(row, touch(300 - ACTIONS_WIDTH / 2 + 10, 50));
    fireEvent.touchEnd(row);

    expect(row.style.transform).toBe("translateX(0px)");
    expect(row.style.transition).toBe("transform 220ms ease");
  });

  it("a vertical drag never moves the row and never claims the open slot", () => {
    const { row, onOpen } = renderRow();

    fireEvent.touchStart(row, touch(200, 50));
    fireEvent.touchMove(row, touch(196, 140));

    expect(row.style.transform).toBe("translateX(0px)");
    expect(onOpen).not.toHaveBeenCalled();

    // And the scroll the platform is about to take over ends the gesture
    // cleanly rather than leaving the transition off.
    fireEvent.touchCancel(row);
    expect(row.style.transition).toBe("transform 220ms ease");
  });
});

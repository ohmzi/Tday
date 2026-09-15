// @vitest-environment jsdom

/**
 * What an open task row does about a touch that was not meant for it.
 *
 * The decision itself is two pure functions in `@/lib/swipeGesture`, tested
 * without a DOM in `swipe-gesture.test.ts` next to the rest of the gesture
 * maths. What cannot be answered there is the wiring: which listeners exist,
 * when, in which phase, and what they measure a touch against. All four of those
 * are the feature — an interceptor attached in the wrong phase sees nothing, one
 * attached always costs a five-hundred-row feed five hundred listeners, and one
 * without the row-subtree guard eats the taps on the row's own pills.
 *
 * The harness is `swipe-row-reveal-haptic.test.tsx`'s, which argues the
 * timestamps: jsdom stamps a tick's events within a fraction of a millisecond of
 * each other, which the sampler reads as no velocity, so a test has to say when
 * everything happened. It renders a component rather than calling `renderHook`
 * because the thing under test is a `ref` pointed at a real subtree, and a hook
 * with nothing rendered around it has no inside and no outside.
 */

import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, createEvent, fireEvent, render, screen } from "@testing-library/react";
import { useSwipeRow } from "@/hooks/useSwipeRow";

/** Mirrors `ACTIONS_WIDTH` in all three row components. */
const ACTIONS_WIDTH = 210;

afterEach(cleanup);

/**
 * The shape all three containers have: one wrapper holding both the stationary
 * pill strip and the foreground that translates over it, with the ref on the
 * wrapper — and something else on the page to touch.
 */
function RowHarness() {
  const { swipeX, rowRef, swipeHandlers } = useSwipeRow({
    actionsWidth: ACTIONS_WIDTH,
    onOpen: () => undefined,
  });
  return (
    <div>
      <div
        data-testid="wrapper"
        ref={(node) => {
          rowRef.current = node;
        }}
      >
        <button type="button" data-testid="pill">
          Delete task
        </button>
        <div
          data-testid="row"
          {...swipeHandlers}
          style={{ transform: `translateX(${swipeX}px)` }}
        />
      </div>
      <div data-testid="scroller">
        <button type="button" data-testid="other-row">
          Another task
        </button>
      </div>
    </div>
  );
}

/** One touch event at a time the test chose. */
function touchAt(
  row: HTMLElement,
  kind: "touchStart" | "touchMove" | "touchEnd",
  clientX: number,
  t: number,
  clientY = 50,
) {
  const point = { clientX, clientY };
  const event = createEvent[kind](row, {
    touches: kind === "touchEnd" ? [] : [point],
    changedTouches: [point],
  });
  Object.defineProperty(event, "timeStamp", { value: t });
  fireEvent(row, event);
}

/** How far the row has travelled, in px. */
function offsetOf(row: HTMLElement): number {
  const match = /translateX\((-?[\d.]+)px\)/.exec(row.style.transform);
  return match ? Number(match[1]) : 0;
}

/** A row dragged all the way out and released there, which is how one is opened. */
function openRow(): HTMLElement {
  const row = screen.getByTestId("row");
  touchAt(row, "touchStart", 300, 1000);
  touchAt(row, "touchMove", 200, 1100);
  touchAt(row, "touchMove", 90, 1200);
  touchAt(row, "touchEnd", 90, 1300);
  expect(offsetOf(row)).toBe(-ACTIONS_WIDTH);
  return row;
}

describe("a touch that was not meant for the open row", () => {
  it("shuts it, wherever on the page it landed", () => {
    // The ask, in one assertion: the buttons are showing, the user does not want
    // them, and touching anything else puts the row back. One interceptor, so
    // "anything else" needs no enumeration of widgets — this is another row's
    // own button, and the header, the FAB and the dock are the same event.
    render(<RowHarness />);
    const row = openRow();

    fireEvent.pointerDown(screen.getByTestId("other-row"));

    expect(offsetOf(row)).toBe(0);
  });

  it("leaves the row alone when the touch was inside it", () => {
    // The guard that makes firing on the pointer-*down* safe at all. The pills
    // are `absolute inset-y-0 right-0` and hold still while the foreground
    // slides over them: a dismissal on the down would slide the foreground back
    // across the pill before the up, the up target would no longer be the
    // button, and the browser would fire `click` on the common ancestor — the
    // pill's own handler would silently never run.
    render(<RowHarness />);
    const row = openRow();

    fireEvent.pointerDown(screen.getByTestId("pill"));

    expect(offsetOf(row)).toBe(-ACTIONS_WIDTH);
  });

  it("consumes nothing, so the touch still does its own job", () => {
    // Non-consumption is the load-bearing half of the decision and the
    // accessibility requirement behind it: with a screen reader on, a swallowed
    // first activation is a double-tap that silently does nothing and announces
    // no reason. The other row's button must still be clicked by the same
    // gesture that shut this row.
    render(<RowHarness />);
    const row = openRow();
    const other = screen.getByTestId("other-row");
    const reached = vi.fn();
    other.addEventListener("pointerdown", reached);

    const down = createEvent.pointerDown(other);
    fireEvent(other, down);

    expect(offsetOf(row)).toBe(0);
    // The two halves of "observes, never consumes": the event still reached its
    // own target after the capture-phase listener saw it (no `stopPropagation`),
    // and it is still going to do whatever it does (no `preventDefault`).
    expect(reached).toHaveBeenCalledTimes(1);
    expect(down.defaultPrevented).toBe(false);
  });
});

describe("a scroll under an open row", () => {
  it("shuts it, on an event that does not bubble", () => {
    // A scroll closes the row at the moment the list starts moving, which is a
    // deliberate divergence from the search capsule: that field is chrome and
    // stays put, while a row travels with the list and would end up holding an
    // armed Delete pill under a thumb aimed at a different task. The listener is
    // capture-phase because `scroll` does not bubble but does capture-propagate,
    // and this fires it on a descendant with `bubbles: false` to say so.
    render(<RowHarness />);
    const row = openRow();

    fireEvent.scroll(screen.getByTestId("scroller"));

    expect(offsetOf(row)).toBe(0);
  });

  it("is the only interceptor that can see a scroll starting on the row itself", () => {
    // The likeliest scroll of all, because the hand is already there. The finger
    // goes down inside the row, so it is not a pointer-down *outside* anything
    // and the tap listener never hears about it; the drag locks vertical and the
    // list moves under it.
    render(<RowHarness />);
    const row = openRow();

    touchAt(row, "touchStart", 300, 2000);
    touchAt(row, "touchMove", 300, 2100, 120);
    expect(offsetOf(row)).toBe(-ACTIONS_WIDTH);

    fireEvent.scroll(screen.getByTestId("scroller"));

    expect(offsetOf(row)).toBe(0);
  });
});

describe("a finger that owns the row", () => {
  it("keeps it through anything arriving from outside", () => {
    // The single most important negative case: the row is being dragged — here,
    // back toward home, which is the user closing it themselves — and no
    // interceptor may take it away mid-gesture.
    render(<RowHarness />);
    const row = openRow();

    touchAt(row, "touchStart", 100, 2000);
    touchAt(row, "touchMove", 160, 2100);
    const dragged = offsetOf(row);
    expect(dragged).toBeGreaterThan(-ACTIONS_WIDTH);
    expect(dragged).toBeLessThan(0);

    fireEvent.pointerDown(screen.getByTestId("other-row"));
    fireEvent.scroll(screen.getByTestId("scroller"));

    expect(offsetOf(row)).toBe(dragged);
  });

  it("does not snap back open when a dismissal lands before the axis is known", () => {
    // The re-seed, and the reason `dismissSwipe` is not `setSwipeX(0)`. A touch
    // inside the axis slop has claimed nothing, so a momentum scroll may still
    // shut the row under it — but `onTouchMove` computes `startX + dx`, and a
    // reset that left `startX` at -210 would put the row straight back out on
    // the next move. This is a real sequence (a finger landing on an open row
    // while the list is still gliding) and it is latent on the row-to-row
    // broadcast path too, which is why both go through the same function.
    render(<RowHarness />);
    const row = openRow();

    touchAt(row, "touchStart", 300, 2000);
    fireEvent.scroll(screen.getByTestId("scroller"));
    expect(offsetOf(row)).toBe(0);

    touchAt(row, "touchMove", 280, 2100);

    expect(offsetOf(row)).toBe(-20);
  });
});

describe("what a closed row costs", () => {
  it("has no document listener at all until it is opened, and none after", () => {
    // The whole of web's answer to the perf question the other two clients had
    // to work at. The owner here is a `window` event and no React state, so a
    // row never subscribes to anything to find out whether it is the open one —
    // it reads its own offset — and the listeners are keyed on that offset. At
    // most one row in a feed of hundreds holds a pair, and a feed with nothing
    // open holds none. A context or a store would undo exactly this.
    const added = vi.spyOn(document, "addEventListener");
    const removed = vi.spyOn(document, "removeEventListener");
    const intercepts = (calls: unknown[][]) =>
      calls.filter(([type]) => type === "pointerdown" || type === "scroll").length;

    render(<RowHarness />);
    expect(intercepts(added.mock.calls)).toBe(0);

    const row = openRow();
    expect(intercepts(added.mock.calls)).toBe(2);
    expect(intercepts(removed.mock.calls)).toBe(0);

    fireEvent.pointerDown(screen.getByTestId("other-row"));
    expect(offsetOf(row)).toBe(0);
    expect(intercepts(removed.mock.calls)).toBe(2);

    added.mockRestore();
    removed.mockRestore();
  });
});

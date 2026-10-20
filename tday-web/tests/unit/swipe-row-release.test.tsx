// @vitest-environment jsdom

/**
 * What a task row does when the finger leaves it.
 *
 * Three screens list tasks and all three rows swipe: the scheduled row, the
 * calendar row and the Anytime row. They used to carry three copies of one
 * gesture, and the copies decided a release the same wrong way — is the row past
 * halfway *right now*. That reads the one frame of a gesture that says least
 * about it. A flick is short and fast and means yes; a drag that crept past the
 * mark and was already being walked back is long and slow and means no; under a
 * position-only rule the first is refused and the second is committed.
 *
 * The hook is driven directly here rather than through a row, because what is
 * being asserted is the gesture and not the markup — `calendar-swipe-pointer-
 * capture.test.tsx` is where the row's own wiring is pinned. Timestamps are set
 * on the events explicitly: jsdom stamps everything fired in one tick within a
 * fraction of a millisecond of everything else, which the sampler correctly
 * reads as no velocity at all, so a test that did not set them could only ever
 * see the position-only half. They start at 1000 and not at 0 for a reason worth
 * knowing — React's synthetic event reads `nativeEvent.timeStamp || Date.now()`,
 * so a stamp of exactly zero is quietly replaced by the wall clock.
 */

import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, createEvent, fireEvent, render } from "@testing-library/react";
import { useSwipeRow } from "@/hooks/useSwipeRow";
import { installReducedMotion } from "../setup/reduced-motion";

/** Mirrors `ACTIONS_WIDTH` in all three row components. */
const ACTIONS_WIDTH = 210;
/** Past this, a release commits to open. Half the actions, as the hook has it. */
const HALFWAY = ACTIONS_WIDTH / 2;

const SETTLE_HOME = "transform var(--tday-duration-quick) var(--tday-ease-gesture)";
const SETTLE_OPEN = "transform var(--tday-duration-emphasis) var(--tday-ease-gesture)";
const SETTLE_INSTANT = "transform 0s";

const originalMatchMedia = window.matchMedia;

afterEach(() => {
  cleanup();
  window.matchMedia = originalMatchMedia;
});

function RowHarness({ onOpen }: { onOpen: () => void }) {
  const { swipeX, transition, swipeHandlers } = useSwipeRow({
    actionsWidth: ACTIONS_WIDTH,
    onOpen,
  });
  return (
    <div
      data-testid="row"
      {...swipeHandlers}
      style={{ transform: `translateX(${swipeX}px)`, transition }}
    />
  );
}

function renderRow() {
  const onOpen = vi.fn();
  const view = render(<RowHarness onOpen={onOpen} />);
  return { onOpen, row: view.getByTestId("row") };
}

/**
 * One touch event at a time the test chose.
 *
 * `timeStamp` is readonly on the prototype and cannot be passed through an event
 * init, so it is defined on the instance — which is where the getter reads from,
 * and which React's synthetic event copies straight across.
 */
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

/** The transform clause of the row's whitelist — the settle, and only that. */
const settleOf = (row: HTMLElement) => row.style.transition.split(",")[0].trim();

describe("a release is a projection, not a position", () => {
  it("opens on a flick that never reached halfway", () => {
    // 40px in 20ms, which is an ordinary thumb flick and a quarter of the travel
    // the old rule demanded. The actions were never uncovered, and the row opens
    // anyway, because the finger was plainly still going.
    const { row } = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 260, 1020);
    expect(Math.abs(offsetOf(row))).toBeLessThan(HALFWAY);
    touchAt(row, "touchEnd", 260, 1020);

    expect(offsetOf(row)).toBe(-ACTIONS_WIDTH);
    expect(settleOf(row)).toBe(SETTLE_OPEN);
  });

  it("stays shut on a drag that went past halfway and was already coming back", () => {
    // The other half of the same mistake. The row is at 108px — past the mark —
    // but the last 60ms of the gesture were spent travelling the other way, so
    // the finger had changed its mind before it lifted.
    const { row } = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 180, 1400);
    expect(Math.abs(offsetOf(row))).toBeGreaterThan(HALFWAY);
    touchAt(row, "touchMove", 192, 1440);
    touchAt(row, "touchEnd", 192, 1460);

    expect(offsetOf(row)).toBe(0);
    expect(settleOf(row)).toBe(SETTLE_HOME);
  });

  it("still decides on position when the finger simply stopped", () => {
    // A hold before the lift is a decision not to flick, and the projection has
    // nothing to add to it: the row is past halfway and stays open. This is the
    // case the old rule got right, and it has to keep getting it right.
    const { row } = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 180, 1040);
    touchAt(row, "touchEnd", 180, 1400);

    expect(offsetOf(row)).toBe(-ACTIONS_WIDTH);
  });
});

describe("a limit gives rather than stops dead", () => {
  it("lets a closed row be pulled the wrong way, a little", () => {
    // There is nothing to the right of a closed row and there never will be, so
    // the row answers the pull and refuses it in the same movement. Before this
    // the finger moved 100px and the row did not move at all, which is the app
    // declining to admit the gesture happened.
    const { row } = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 400, 1040);

    const travelled = offsetOf(row);
    expect(travelled).toBeGreaterThan(0);
    expect(travelled).toBeLessThan(ACTIONS_WIDTH / 8 + 1);
    expect(travelled).toBeLessThan(100);
  });

  it("lets an open row be pulled past its actions, and no further", () => {
    const { row } = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", -100, 1040);

    const travelled = offsetOf(row);
    expect(travelled).toBeLessThan(-ACTIONS_WIDTH);
    expect(travelled).toBeGreaterThan(-(ACTIONS_WIDTH + ACTIONS_WIDTH / 8 + 1));
  });

  it("tracks pixel for pixel everywhere in between", () => {
    // The give is for the limits only. Between them every pixel is the user
    // uncovering an action, and a row that lagged there would be the app arguing
    // with a finger it should be following.
    const { row } = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 220, 1040);

    expect(offsetOf(row)).toBe(-80);
  });
});

describe("the way back", () => {
  // The other half of the ask — "or even sliding the row back to right should
  // stop showing the button and slide the row back" — and the half that already
  // worked. It is pinned rather than changed, because the way it could quietly
  // stop working is invisible: if a gesture seeded its `startX` at 0 instead of
  // at the offset the row is resting at, the row would jump to the finger on the
  // first move and every one of these numbers would still be plausible.
  // Android and iOS carry the same round trip for the same reason.

  /** A row dragged out and released open, which is where each of these starts. */
  function openedRow() {
    const { row } = renderRow();
    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 200, 1040);
    touchAt(row, "touchMove", 90, 1080);
    touchAt(row, "touchEnd", 90, 1120);
    expect(offsetOf(row)).toBe(-ACTIONS_WIDTH);
    return row;
  }

  it("follows a drag back from where the row is, not from home", () => {
    // The bug wearing the same clothes: a row that jumps 210px to meet the
    // finger on the first move closes on the release too, so only the first
    // frame tells them apart.
    const row = openedRow();

    touchAt(row, "touchStart", 100, 2000);
    touchAt(row, "touchMove", 130, 2040);

    expect(offsetOf(row)).toBe(-(ACTIONS_WIDTH - 30));
  });

  it("closes when the drag back passes halfway", () => {
    const row = openedRow();

    touchAt(row, "touchStart", 100, 2000);
    touchAt(row, "touchMove", 180, 2040);
    touchAt(row, "touchMove", 230, 2080);
    touchAt(row, "touchEnd", 230, 2120);

    expect(offsetOf(row)).toBe(0);
  });

  it("closes on a rightward flick that never reached halfway", () => {
    // Same rule as the way out, read the other way round: the release is a
    // projection, so a row still travelling right when the finger left is a row
    // going home even from 30px short of its actions.
    const row = openedRow();

    touchAt(row, "touchStart", 100, 2000);
    touchAt(row, "touchMove", 130, 2020);
    touchAt(row, "touchEnd", 130, 2030);

    expect(offsetOf(row)).toBe(0);
  });

  it("stays open when the drag back was walked most of the way and turned round", () => {
    // The negative case that keeps the projection honest in this direction: the
    // finger went right, changed its mind, and was heading back out when it
    // lifted. The row is past halfway by position and stays open all the same.
    const row = openedRow();

    touchAt(row, "touchStart", 100, 2000);
    touchAt(row, "touchMove", 210, 2040);
    touchAt(row, "touchMove", 190, 2080);
    touchAt(row, "touchEnd", 190, 2120);

    expect(offsetOf(row)).toBe(-ACTIONS_WIDTH);
  });
});

describe("a reader who asked not to be moved", () => {
  it("gets the resting place the release chose, with no trip to it", () => {
    // The fifth idiom rule: reduced motion removes the trip and keeps the
    // destination. A row held part-way would be a row nobody can tell from a
    // broken render.
    installReducedMotion(true);
    const { row } = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 260, 1020);
    touchAt(row, "touchEnd", 260, 1020);

    expect(offsetOf(row)).toBe(-ACTIONS_WIDTH);
    expect(settleOf(row)).toBe(SETTLE_INSTANT);
  });

  it("still tracks the finger, because a finger is not a motion the app plays", () => {
    installReducedMotion(true);
    const { row } = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 220, 1040);

    expect(offsetOf(row)).toBe(-80);
    expect(row.style.transition).toBe("none");
  });
});

describe("a row that will not take a swipe", () => {
  it("ignores the gesture outright when it is disabled", () => {
    // A row being picked in a multi-select, or one rendered read-only: the only
    // gesture it has is the tap that chooses it, and uncovering Edit and Delete
    // for one task in the middle of choosing several is not an answer to that.
    const onOpen = vi.fn();
    function Disabled() {
      const { swipeX, swipeHandlers } = useSwipeRow({
        actionsWidth: ACTIONS_WIDTH,
        onOpen,
        disabled: true,
      });
      return (
        <div data-testid="row" {...swipeHandlers} style={{ transform: `translateX(${swipeX}px)` }} />
      );
    }
    const row = render(<Disabled />).getByTestId("row");

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 180, 1040);
    touchAt(row, "touchEnd", 180, 1060);

    expect(offsetOf(row)).toBe(0);
    expect(onOpen).not.toHaveBeenCalled();
  });
});

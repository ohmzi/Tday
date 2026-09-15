// @vitest-environment jsdom

/**
 * When a task row buzzes, and — mostly — when it does not.
 *
 * No gate in this repository can feel a haptic, and no machine this was written
 * on has a vibrator attached. So what is pinned here is not the buzz but the
 * decision behind it, which is an ordinary pure question about a gesture: has
 * this row, on this frame, become one that would open if the finger vanished?
 * That is `commitsOpen` with the velocity term zeroed, and it is the same
 * function the release has always used, so the buzz cannot come to announce a
 * reveal that does not then happen.
 *
 * `@/lib/haptics` is mocked outright rather than stubbed through
 * `navigator.vibrate`, because the question at this level is *whether the hook
 * asked*. The other half — whether asking actually vibrates, and whether the
 * user's own switch is honoured on the way — is `feedback-preferences.test.tsx`,
 * through the real module and a stubbed vibrator. A mock here cannot see the
 * chokepoint gate and deliberately does not try to.
 *
 * The harness, the timestamps and the 1000 they start at are all borrowed from
 * `swipe-row-release.test.tsx`, which explains each of them; the short version is
 * that jsdom stamps a whole tick's events within a fraction of a millisecond of
 * each other, which the sampler reads as no velocity at all, so a test that wants
 * a flick has to say when everything happened.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, createEvent, fireEvent, render } from "@testing-library/react";
import { hapticReveal } from "@/lib/haptics";
import { useSwipeRow } from "@/hooks/useSwipeRow";

vi.mock("@/lib/haptics", () => ({ hapticReveal: vi.fn() }));

/** Mirrors `ACTIONS_WIDTH` in all three row components. */
const ACTIONS_WIDTH = 210;
/** Past this, the row is committed to opening — and that is the detent. */
const HALFWAY = ACTIONS_WIDTH / 2;

const buzzes = vi.mocked(hapticReveal);

beforeEach(() => {
  buzzes.mockClear();
});

afterEach(cleanup);

function RowHarness() {
  const { swipeX, swipeHandlers } = useSwipeRow({
    actionsWidth: ACTIONS_WIDTH,
    onOpen: () => undefined,
  });
  return (
    <div data-testid="row" {...swipeHandlers} style={{ transform: `translateX(${swipeX}px)` }} />
  );
}

function renderRow() {
  return render(<RowHarness />).getByTestId("row");
}

/** One touch event at a time the test chose. */
function touchAt(
  row: HTMLElement,
  kind: "touchStart" | "touchMove" | "touchEnd" | "touchCancel",
  clientX: number,
  t: number,
  clientY = 50,
) {
  const point = { clientX, clientY };
  const event = createEvent[kind](row, {
    touches: kind === "touchStart" || kind === "touchMove" ? [point] : [],
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

describe("the detent, under the finger", () => {
  it("buzzes on the update that commits the row to opening, and not before", () => {
    // The moment the ask names: the row slid far enough left that the buttons
    // behind it are staying, felt while the thumb is still on it rather than
    // reported after the hand has gone.
    const row = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 290, 1100);
    expect(Math.abs(offsetOf(row))).toBeLessThan(HALFWAY);
    expect(buzzes).not.toHaveBeenCalled();

    touchAt(row, "touchMove", 180, 1400);
    expect(Math.abs(offsetOf(row))).toBeGreaterThan(HALFWAY);
    expect(buzzes).toHaveBeenCalledTimes(1);
  });

  it("says nothing further to a finger that holds still on the mark", () => {
    // The failure a flag exists to prevent. "Is the row past the threshold?"
    // answers yes on every frame a finger rests there, and a detent that repeats
    // for as long as you hold is a rattle rather than a detent.
    const row = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 180, 1400);
    expect(buzzes).toHaveBeenCalledTimes(1);

    touchAt(row, "touchMove", 175, 1500);
    touchAt(row, "touchMove", 185, 1600);
    touchAt(row, "touchMove", 178, 1700);

    expect(buzzes).toHaveBeenCalledTimes(1);
  });

  it("does not fire twice for a drag that crosses, comes back and crosses again", () => {
    // The other thing the flag buys over a hysteresis band: within one gesture it
    // cannot re-arm at all, so there is no second crossing to catch and no second
    // number to justify.
    const row = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 180, 1400);
    touchAt(row, "touchMove", 290, 1800);
    expect(Math.abs(offsetOf(row))).toBeLessThan(HALFWAY);
    touchAt(row, "touchMove", 170, 2200);

    expect(buzzes).toHaveBeenCalledTimes(1);
  });

  it("adds nothing when the release then opens the row it already announced", () => {
    // One open, one buzz. The release finds the flag spent and stays quiet, which
    // is the whole of the second arm's condition read the other way round.
    const row = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 180, 1400);
    touchAt(row, "touchEnd", 180, 1800);

    expect(offsetOf(row)).toBe(-ACTIONS_WIDTH);
    expect(buzzes).toHaveBeenCalledTimes(1);
  });
});

describe("the flick, at the release", () => {
  it("buzzes once at lift-off for a swipe that opens without ever crossing", () => {
    // 40px in 20ms — an ordinary thumb flick, a quarter of the travel the detent
    // asks for, and a row that opens anyway because the finger was plainly still
    // going. Without this arm the fastest, most deliberate swipe in the app would
    // be the only silent one.
    const row = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 260, 1020);
    expect(Math.abs(offsetOf(row))).toBeLessThan(HALFWAY);
    expect(buzzes).not.toHaveBeenCalled();

    touchAt(row, "touchEnd", 260, 1020);

    expect(offsetOf(row)).toBe(-ACTIONS_WIDTH);
    expect(buzzes).toHaveBeenCalledTimes(1);
  });

  it("stays silent for a release that puts the row back", () => {
    // Nothing was revealed, so nothing is announced. This is also the case that
    // makes the honest cost above bearable: the only way to feel a reveal that
    // did not happen is to have crossed the detent first and walked it back.
    const row = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 260, 1400);
    touchAt(row, "touchEnd", 260, 1800);

    expect(offsetOf(row)).toBe(0);
    expect(buzzes).not.toHaveBeenCalled();
  });
});

describe("gestures that are not a reveal", () => {
  it("says nothing when the platform takes the touch away", () => {
    // A scroll, an edge back-swipe, an incoming call. The row goes back where the
    // gesture found it and the user never finished choosing, which is the reason
    // `cancelSwipe` commits nothing either.
    const row = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 260, 1400);
    touchAt(row, "touchCancel", 260, 1420);

    expect(offsetOf(row)).toBe(0);
    expect(buzzes).not.toHaveBeenCalled();
  });

  it("does not repeat the detent it already spent when the touch is then taken away", () => {
    const row = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 180, 1400);
    expect(buzzes).toHaveBeenCalledTimes(1);

    touchAt(row, "touchCancel", 180, 1420);

    expect(offsetOf(row)).toBe(0);
    expect(buzzes).toHaveBeenCalledTimes(1);
  });

  it("says nothing at all while dragging a row that is already open", () => {
    // There is nothing left to uncover. A row pulled further open, walked part of
    // the way back and pulled out again never comes to rest closed, so it never
    // re-arms — which is what the seed at `touchstart` is for.
    const row = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 180, 1400);
    touchAt(row, "touchEnd", 180, 1800);
    expect(offsetOf(row)).toBe(-ACTIONS_WIDTH);
    buzzes.mockClear();

    touchAt(row, "touchStart", 300, 2000);
    touchAt(row, "touchMove", 250, 2400);
    touchAt(row, "touchMove", 380, 2800);
    touchAt(row, "touchMove", 280, 3200);

    expect(buzzes).not.toHaveBeenCalled();
  });
});

describe("the next open", () => {
  it("buzzes again once the row has come to rest closed", () => {
    // The re-arm, and the reason no close path has to remember anything: every
    // way a row shuts — this release, a pill, the row another row claimed the
    // slot from — leaves it at 0, and a gesture that starts at 0 is armed.
    const row = renderRow();

    touchAt(row, "touchStart", 300, 1000);
    touchAt(row, "touchMove", 180, 1400);
    touchAt(row, "touchEnd", 180, 1800);
    expect(buzzes).toHaveBeenCalledTimes(1);

    touchAt(row, "touchStart", 300, 2000);
    touchAt(row, "touchMove", 500, 2400);
    touchAt(row, "touchEnd", 500, 2800);
    expect(offsetOf(row)).toBe(0);
    expect(buzzes).toHaveBeenCalledTimes(1);

    touchAt(row, "touchStart", 300, 3000);
    touchAt(row, "touchMove", 180, 3400);

    expect(buzzes).toHaveBeenCalledTimes(2);
  });
});

// @vitest-environment jsdom

/**
 * The clock behind the calendar's refused back swipe.
 *
 * The card draws the resistance from a flag, and everything that can go wrong
 * with a flag like this one goes wrong in time rather than in markup: it never
 * comes down, so the class sits in the DOM forever; it comes down late, so a
 * second refusal is ignored; it is raised under reduced motion, where the
 * animation it describes never runs; or its timer outlives the screen and calls
 * `setState` on a component the user has already left.
 *
 * The length is asserted against `DURATION_MS.quick` rather than against 150.
 * The number is not this hook's to know — the point of the assertion is that
 * the flag is held for exactly as long as the stylesheet animates, and both
 * read the same rung.
 */

import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DURATION_MS } from "@/lib/motion";
import { useNavigationRefusal } from "@/features/calendar/lib/useNavigationRefusal";
import { installReducedMotion } from "../setup/reduced-motion";

const originalMatchMedia = window.matchMedia;

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  installReducedMotion(false);
});

afterEach(() => {
  vi.useRealTimers();
  window.matchMedia = originalMatchMedia;
});

describe("useNavigationRefusal", () => {
  it("starts with nothing to answer", () => {
    const { result } = renderHook(() => useNavigationRefusal());

    expect(result.current.refusing).toBe(false);
  });

  it("raises the flag for exactly as long as the answer plays, then puts it down", () => {
    const { result } = renderHook(() => useNavigationRefusal());

    act(() => result.current.refuse());
    expect(result.current.refusing).toBe(true);

    act(() => void vi.advanceTimersByTime(DURATION_MS.quick - 1));
    expect(result.current.refusing).toBe(true);

    act(() => void vi.advanceTimersByTime(1));
    expect(result.current.refusing).toBe(false);
  });

  it("lets the answer already on screen run to its end when it is refused again", () => {
    // A second swipe at the wall inside the first answer is one complaint, not
    // two: the class never comes off, so the resistance keeps playing, and the
    // timer is pushed back rather than the animation being restarted.
    const { result } = renderHook(() => useNavigationRefusal());

    act(() => result.current.refuse());
    act(() => void vi.advanceTimersByTime(DURATION_MS.quick - 10));
    act(() => result.current.refuse());

    act(() => void vi.advanceTimersByTime(10));
    expect(result.current.refusing).toBe(true);

    act(() => void vi.advanceTimersByTime(DURATION_MS.quick));
    expect(result.current.refusing).toBe(false);
  });

  it("says nothing at all under reduced motion", () => {
    // The one motion with no finished state to draw instead of playing. Raising
    // the flag anyway would leave a class in the DOM describing an animation the
    // stylesheet has switched off.
    installReducedMotion(true);
    const { result } = renderHook(() => useNavigationRefusal());

    act(() => result.current.refuse());

    expect(result.current.refusing).toBe(false);
  });

  it("does not leave its timer running past the screen", () => {
    // A refused swipe is very easily the last thing a user does before leaving
    // the calendar.
    const clearTimeoutSpy = vi.spyOn(window, "clearTimeout");
    const { result, unmount } = renderHook(() => useNavigationRefusal());

    act(() => result.current.refuse());
    // Only the unmount's own call is left to see: arming the first refusal
    // finds no timer to clear.
    clearTimeoutSpy.mockClear();
    unmount();

    expect(clearTimeoutSpy).toHaveBeenCalled();
    clearTimeoutSpy.mockRestore();
  });
});

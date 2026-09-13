// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { useDuckPresence } from "@/hooks/useDuckPresence";
import { DURATION_MS } from "@/lib/motion";
import { installReducedMotion } from "../setup/reduced-motion";

/**
 * The duck is the dock and the create button travelling out of their slot
 * instead of ceasing to exist. `useFadeUnmount` already has its own suite for
 * the lingering half; what is tested here is the half that is this hook's own —
 * which frames get a class, which deliberately get none, and which of the
 * lingering ones may still be tapped.
 */
describe("useDuckPresence", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("draws a control that was present from the first paint with no class at all", () => {
    // The behaviour Android gets from AnimatedVisibility and iOS from a value
    // that has not changed: opening the app is not the dock arriving.
    const { result } = renderHook(() => useDuckPresence(true));
    expect(result.current.mounted).toBe(true);
    expect(result.current.className).toBe("");
  });

  it("keeps a control that starts absent unmounted, and plays no exit for it", () => {
    const { result } = renderHook(() => useDuckPresence(false));
    expect(result.current.mounted).toBe(false);
  });

  it("plays the exit on the frame the slot is taken, and keeps the node for it", () => {
    const { result, rerender } = renderHook(({ present }) => useDuckPresence(present), {
      initialProps: { present: true },
    });

    rerender({ present: false });
    expect(result.current.mounted).toBe(true);
    expect(result.current.className).toBe("tday-duck-exit");
  });

  it("takes the control's taps away for the frames it is only a picture of one", () => {
    // The node outlives the flag so the exit has somewhere to play; the hit
    // targets must not, because the selection bar arriving in that row is
    // painted underneath the leaving dock for the whole rung.
    const { result, rerender } = renderHook(({ present }) => useDuckPresence(present), {
      initialProps: { present: true },
    });
    expect(result.current.interactive).toBe(true);

    rerender({ present: false });
    expect(result.current.interactive).toBe(false);
  });

  it("takes the node away once the exit has had the Emphasis rung to play in", async () => {
    const { result, rerender } = renderHook(({ present }) => useDuckPresence(present), {
      initialProps: { present: true },
    });
    rerender({ present: false });

    await act(async () => {
      await vi.advanceTimersByTimeAsync(DURATION_MS.emphasis - 1);
    });
    expect(result.current.mounted).toBe(true);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(1);
    });
    expect(result.current.mounted).toBe(false);
  });

  it("plays the enter once the control has a departure behind it", () => {
    const { result, rerender } = renderHook(({ present }) => useDuckPresence(present), {
      initialProps: { present: true },
    });

    rerender({ present: false });
    act(() => {
      vi.advanceTimersByTime(DURATION_MS.emphasis);
    });
    rerender({ present: true });

    expect(result.current.mounted).toBe(true);
    expect(result.current.className).toBe("tday-duck-enter");
    // And live again on that same frame, with the whole enter still to play.
    expect(result.current.interactive).toBe(true);
  });

  it("goes straight back to arriving when the slot is handed back mid-exit", () => {
    // `useFadeUnmount` cancels the pending unmount; what matters here is that
    // the class is picked from the live flag and never from anything the exit
    // stored, so the control cannot be left wearing a stale departure.
    const { result, rerender } = renderHook(({ present }) => useDuckPresence(present), {
      initialProps: { present: true },
    });

    rerender({ present: false });
    act(() => {
      vi.advanceTimersByTime(DURATION_MS.emphasis / 2);
    });
    rerender({ present: true });

    expect(result.current.mounted).toBe(true);
    expect(result.current.className).toBe("tday-duck-enter");
  });

  it("removes the trip and not the destination under reduced motion", () => {
    // The stylesheet switches the keyframes off; the class still names the
    // direction, and the node goes on the same flip rather than waiting out an
    // animation that will not play.
    const originalMatchMedia = window.matchMedia;
    installReducedMotion(true);

    try {
      const { result, rerender } = renderHook(({ present }) => useDuckPresence(present), {
        initialProps: { present: true },
      });
      rerender({ present: false });
      expect(result.current.mounted).toBe(false);
    } finally {
      window.matchMedia = originalMatchMedia;
    }
  });
});

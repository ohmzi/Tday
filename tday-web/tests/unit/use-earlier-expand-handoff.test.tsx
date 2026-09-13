// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { useEarlierExpandHandoff } from "@/features/todayTodos/lib/useEarlierExpandHandoff";
import { installReducedMotion } from "../setup/reduced-motion";

const EXIT_MS = 520;

describe("useEarlierExpandHandoff", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("starts collapsed with no hand-off in flight", () => {
    const { result } = renderHook(() => useEarlierExpandHandoff(EXIT_MS));
    expect(result.current.expanded).toBe(false);
    expect(result.current.handoffPending).toBe(false);
  });

  it("toggle(false) — no illustration to hand off from — expands immediately, never sets handoffPending", () => {
    const { result } = renderHook(() => useEarlierExpandHandoff(EXIT_MS));

    act(() => {
      result.current.toggle(false);
    });

    expect(result.current.expanded).toBe(true);
    expect(result.current.handoffPending).toBe(false);
  });

  it("toggle(true) while collapsed — requirement 3 — hides the illustration first, expands only after exitMs", async () => {
    const { result } = renderHook(() => useEarlierExpandHandoff(EXIT_MS));

    act(() => {
      result.current.toggle(true);
    });

    // Phase 1: hand-off pending, rows not revealed yet.
    expect(result.current.handoffPending).toBe(true);
    expect(result.current.expanded).toBe(false);

    // Not yet — the whole point is this doesn't fire early.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(EXIT_MS - 50);
    });
    expect(result.current.expanded).toBe(false);
    expect(result.current.handoffPending).toBe(true);

    // Phase 2: exactly at exitMs, the hand-off completes — same instant the
    // CSS `.tday-empty-exit` animation (given the same `exitMs` via
    // `animationDuration`) finishes playing.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(50);
    });
    expect(result.current.expanded).toBe(true);
    expect(result.current.handoffPending).toBe(false);
  });

  it("a second tap during the exit beat is ignored — no new timer, no restart", async () => {
    const { result } = renderHook(() => useEarlierExpandHandoff(EXIT_MS));

    act(() => {
      result.current.toggle(true);
    });
    expect(result.current.handoffPending).toBe(true);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(EXIT_MS / 2);
    });

    // A tap landing inside the running beat — matches the real toggle
    // handler being called again by a second click on the header.
    act(() => {
      result.current.toggle(true);
    });
    // Still pending, still not expanded — the tap changed nothing.
    expect(result.current.handoffPending).toBe(true);
    expect(result.current.expanded).toBe(false);

    // The ORIGINAL timer still fires on schedule — a restarted timer would
    // still be pending here.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(EXIT_MS / 2);
    });
    expect(result.current.expanded).toBe(true);
    expect(result.current.handoffPending).toBe(false);
  });

  it("collapsing is always immediate, even if called mid a stale pending window", async () => {
    const { result } = renderHook(() => useEarlierExpandHandoff(EXIT_MS));

    act(() => {
      result.current.toggle(true);
    });
    await act(async () => {
      await vi.advanceTimersByTimeAsync(EXIT_MS);
    });
    expect(result.current.expanded).toBe(true);

    // Collapse: expanded is true, so this takes the immediate branch
    // regardless of the (irrelevant, since !expanded is false) argument.
    act(() => {
      result.current.toggle(true);
    });
    expect(result.current.expanded).toBe(false);
    expect(result.current.handoffPending).toBe(false);
  });

  it("the hand-off flag is cleared by setExpandedImmediately — the exact class of bug the iOS review caught", async () => {
    const { result } = renderHook(() => useEarlierExpandHandoff(EXIT_MS));

    act(() => {
      result.current.toggle(true);
    });
    expect(result.current.handoffPending).toBe(true);

    // A deep-link/focus effect firing mid-exit (e.g. a toast navigation)
    // must not leave `handoffPending` stuck true forever — that was the
    // exact bug: a hand-off flag set on expand that no other path reset.
    act(() => {
      result.current.setExpandedImmediately(true);
    });
    expect(result.current.expanded).toBe(true);
    expect(result.current.handoffPending).toBe(false);

    // And the original timer, if it fired anyway, would be a no-op — assert
    // nothing throws and state stays put.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(EXIT_MS + 10);
    });
    expect(result.current.expanded).toBe(true);
    expect(result.current.handoffPending).toBe(false);
  });

  it("setExpandedImmediately(false) also clears a pending hand-off (collapse-while-exiting path)", () => {
    const { result } = renderHook(() => useEarlierExpandHandoff(EXIT_MS));

    act(() => {
      result.current.toggle(true);
    });
    expect(result.current.handoffPending).toBe(true);

    act(() => {
      result.current.setExpandedImmediately(false);
    });
    expect(result.current.expanded).toBe(false);
    expect(result.current.handoffPending).toBe(false);
  });

  it("unmounting mid hand-off clears the pending timer instead of leaking a setState-after-unmount", async () => {
    const { result, unmount } = renderHook(() => useEarlierExpandHandoff(EXIT_MS));

    act(() => {
      result.current.toggle(true);
    });
    expect(result.current.handoffPending).toBe(true);

    unmount();

    // If the timeout were not cleared, this would call setState on an
    // unmounted component — vitest/jsdom surfaces that as a thrown/console
    // error, so simply not throwing here is the assertion.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(EXIT_MS + 10);
    });
  });

  describe("under prefers-reduced-motion", () => {
    const REAL_MATCH_MEDIA = window.matchMedia;

    afterEach(() => {
      window.matchMedia = REAL_MATCH_MEDIA;
    });

    it("expands on the same tap — no dead wait in front of an illustration that cannot animate", () => {
      installReducedMotion(true);
      const { result } = renderHook(() => useEarlierExpandHandoff(EXIT_MS));

      act(() => {
        result.current.toggle(true);
      });

      // The bug this replaced: `handoffPending` went true, `.tday-empty-exit`
      // was `animation: none`, and the reader got EXIT_MS of a static picture
      // followed by the whole screen changing at once.
      expect(result.current.expanded).toBe(true);
      expect(result.current.handoffPending).toBe(false);
    });

    it("arms no timer at all — nothing is left to fire later and undo the expand", async () => {
      installReducedMotion(true);
      const { result } = renderHook(() => useEarlierExpandHandoff(EXIT_MS));

      act(() => {
        result.current.toggle(true);
      });
      expect(result.current.expanded).toBe(true);

      await act(async () => {
        await vi.advanceTimersByTimeAsync(EXIT_MS + 10);
      });
      expect(result.current.expanded).toBe(true);
      expect(result.current.handoffPending).toBe(false);
    });

    it("still collapses on the next tap — the preference removes the beat, not the toggle", () => {
      installReducedMotion(true);
      const { result } = renderHook(() => useEarlierExpandHandoff(EXIT_MS));

      act(() => {
        result.current.toggle(true);
      });
      act(() => {
        result.current.toggle(true);
      });
      expect(result.current.expanded).toBe(false);
      expect(result.current.handoffPending).toBe(false);
    });

    it("a mid-session flip reaches the toggle the header is already holding", () => {
      const media = installReducedMotion(false);
      const { result } = renderHook(() => useEarlierExpandHandoff(EXIT_MS));

      // The preference arrives after mount — an OS battery-saver, or the user
      // changing their mind — so a `matchMedia` read once at mount would still
      // be answering "motion is fine" here.
      act(() => {
        media.set(true);
      });
      act(() => {
        result.current.toggle(true);
      });

      expect(result.current.expanded).toBe(true);
      expect(result.current.handoffPending).toBe(false);
    });
  });

  it("a fresh instance never inherits a previous instance's pending state", () => {
    // Guards the "component remounts per scope/route" claim in the hook's
    // own doc comment: a brand-new mount always starts clean.
    const { result } = renderHook(() => useEarlierExpandHandoff(EXIT_MS));
    expect(result.current.expanded).toBe(false);
    expect(result.current.handoffPending).toBe(false);
  });
});

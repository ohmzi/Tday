// @vitest-environment jsdom
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { act, renderHook } from "@testing-library/react";
import { useEarlierExpandHandoff } from "@/features/todayTodos/lib/useEarlierExpandHandoff";
import { installReducedMotion } from "../setup/reduced-motion";

/**
 * The two beats are deliberately different lengths here, and neither is a token
 * value: every assertion below is about WHICH exit a direction waits for, and
 * two numbers that happen to match would let a collapse wait on the scene's
 * timer — or an expand on the rows' — with nothing to notice.
 */
const SCENE_EXIT_MS = 200;
const ROWS_EXIT_MS = 260;

function mountHandoff() {
  return renderHook(() => useEarlierExpandHandoff(SCENE_EXIT_MS, ROWS_EXIT_MS));
}

describe("useEarlierExpandHandoff", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("starts collapsed with no beat in flight", () => {
    const { result } = mountHandoff();
    expect(result.current.expanded).toBe(false);
    expect(result.current.handoff).toBe("idle");
  });

  it("toggle(false) — nothing swaps — expands immediately, never leaves idle", () => {
    const { result } = mountHandoff();

    act(() => {
      result.current.toggle(false);
    });

    expect(result.current.expanded).toBe(true);
    expect(result.current.handoff).toBe("idle");
  });

  describe("expanding: the scene is the one leaving", () => {
    it("hides the illustration first, expands only after the scene's own exit", async () => {
      const { result } = mountHandoff();

      act(() => {
        result.current.toggle(true);
      });

      // Phase 1: the scene is leaving, rows not revealed yet.
      expect(result.current.handoff).toBe("scene-leaving");
      expect(result.current.expanded).toBe(false);

      // Not yet — the whole point is this doesn't fire early.
      await act(async () => {
        await vi.advanceTimersByTimeAsync(SCENE_EXIT_MS - 50);
      });
      expect(result.current.expanded).toBe(false);
      expect(result.current.handoff).toBe("scene-leaving");

      // Phase 2: exactly at the scene's exit, the hand-off completes — same
      // instant the CSS `.tday-empty-exit` animation, drawn on the rung
      // `TODAY_EARLIER_EXIT_MS` names, finishes playing.
      await act(async () => {
        await vi.advanceTimersByTimeAsync(50);
      });
      expect(result.current.expanded).toBe(true);
      expect(result.current.handoff).toBe("idle");
    });

    it("waits the scene's exit and not the rows', which are two different numbers", async () => {
      const { result } = mountHandoff();

      act(() => {
        result.current.toggle(true);
      });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(SCENE_EXIT_MS);
      });

      // Still short of ROWS_EXIT_MS. A beat timed against the wrong exit would
      // still be pending here.
      expect(result.current.expanded).toBe(true);
      expect(result.current.handoff).toBe("idle");
    });

    it("a second tap during the beat is ignored — no new timer, no restart", async () => {
      const { result } = mountHandoff();

      act(() => {
        result.current.toggle(true);
      });
      expect(result.current.handoff).toBe("scene-leaving");

      await act(async () => {
        await vi.advanceTimersByTimeAsync(SCENE_EXIT_MS / 2);
      });

      // A tap landing inside the running beat — matches the real toggle
      // handler being called again by a second click on the header.
      act(() => {
        result.current.toggle(true);
      });
      expect(result.current.handoff).toBe("scene-leaving");
      expect(result.current.expanded).toBe(false);

      // The ORIGINAL timer still fires on schedule — a restarted timer would
      // still be pending here.
      await act(async () => {
        await vi.advanceTimersByTimeAsync(SCENE_EXIT_MS / 2);
      });
      expect(result.current.expanded).toBe(true);
      expect(result.current.handoff).toBe("idle");
    });
  });

  describe("collapsing: the rows are the one leaving", () => {
    async function expand(result: { current: ReturnType<typeof useEarlierExpandHandoff> }) {
      act(() => {
        result.current.toggle(true);
      });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(SCENE_EXIT_MS);
      });
      expect(result.current.expanded).toBe(true);
    }

    it("drops `expanded` on the tap — that flag is what arms the rows' own fade", async () => {
      const { result } = mountHandoff();
      await expand(result);

      act(() => {
        result.current.toggle(true);
      });

      // The mirror of the expand, and mirrored rather than copied: there the
      // flag is held BACK so the scene keeps drawing itself while it leaves;
      // here it is dropped AT ONCE, because it is the thing `useFadeUnmount`
      // and `.tday-rows-exit` read to start the rows leaving at all.
      expect(result.current.expanded).toBe(false);
      expect(result.current.handoff).toBe("rows-leaving");
    });

    it("holds the beat open for the rows' fade, not the scene's exit", async () => {
      const { result } = mountHandoff();
      await expand(result);

      act(() => {
        result.current.toggle(true);
      });

      // The defect this replaced: the scene remounted into an already-open
      // track on the tap frame and claimed its 42vh while the rows it landed
      // on were still holding their own height behind it. Ending the beat at
      // the scene's exit instead of the rows' would leave most of that overlap
      // in place, so the two numbers are kept apart to prove which one is read.
      await act(async () => {
        await vi.advanceTimersByTimeAsync(SCENE_EXIT_MS);
      });
      expect(result.current.handoff).toBe("rows-leaving");

      await act(async () => {
        await vi.advanceTimersByTimeAsync(ROWS_EXIT_MS - SCENE_EXIT_MS);
      });
      expect(result.current.handoff).toBe("idle");
      expect(result.current.expanded).toBe(false);
    });

    it("ignores a tap that lands inside its own beat, the same way the expand does", async () => {
      const { result } = mountHandoff();
      await expand(result);

      act(() => {
        result.current.toggle(true);
      });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(ROWS_EXIT_MS / 2);
      });
      act(() => {
        result.current.toggle(true);
      });
      expect(result.current.handoff).toBe("rows-leaving");
      expect(result.current.expanded).toBe(false);

      await act(async () => {
        await vi.advanceTimersByTimeAsync(ROWS_EXIT_MS / 2);
      });
      expect(result.current.handoff).toBe("idle");
      expect(result.current.expanded).toBe(false);
    });

    it("is immediate when nothing swaps — a screen with tasks on it", async () => {
      const { result } = mountHandoff();
      act(() => {
        result.current.toggle(false);
      });
      expect(result.current.expanded).toBe(true);

      act(() => {
        result.current.toggle(false);
      });
      expect(result.current.expanded).toBe(false);
      expect(result.current.handoff).toBe("idle");
    });
  });

  it("the beat is cleared by setExpandedImmediately — the exact class of bug the iOS review caught", async () => {
    const { result } = mountHandoff();

    act(() => {
      result.current.toggle(true);
    });
    expect(result.current.handoff).toBe("scene-leaving");

    // A deep-link/focus effect firing mid-exit (e.g. a toast navigation)
    // must not leave the beat stuck open forever — that was the exact bug: a
    // hand-off flag set on expand that no other path reset.
    act(() => {
      result.current.setExpandedImmediately(true);
    });
    expect(result.current.expanded).toBe(true);
    expect(result.current.handoff).toBe("idle");

    // And the original timer, if it fired anyway, would be a no-op — assert
    // nothing throws and state stays put.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(ROWS_EXIT_MS + 10);
    });
    expect(result.current.expanded).toBe(true);
    expect(result.current.handoff).toBe("idle");
  });

  it("setExpandedImmediately(false) also clears a beat in flight", () => {
    const { result } = mountHandoff();

    act(() => {
      result.current.toggle(true);
    });
    expect(result.current.handoff).toBe("scene-leaving");

    act(() => {
      result.current.setExpandedImmediately(false);
    });
    expect(result.current.expanded).toBe(false);
    expect(result.current.handoff).toBe("idle");
  });

  it("unmounting mid-beat clears the pending timer instead of leaking a setState-after-unmount", async () => {
    const { result, unmount } = mountHandoff();

    act(() => {
      result.current.toggle(true);
    });
    expect(result.current.handoff).toBe("scene-leaving");

    unmount();

    // If the timeout were not cleared, this would call setState on an
    // unmounted component — vitest/jsdom surfaces that as a thrown/console
    // error, so simply not throwing here is the assertion.
    await act(async () => {
      await vi.advanceTimersByTimeAsync(ROWS_EXIT_MS + 10);
    });
  });

  describe("under prefers-reduced-motion", () => {
    const REAL_MATCH_MEDIA = window.matchMedia;

    afterEach(() => {
      window.matchMedia = REAL_MATCH_MEDIA;
    });

    it("expands on the same tap — no dead wait in front of an illustration that cannot animate", () => {
      installReducedMotion(true);
      const { result } = mountHandoff();

      act(() => {
        result.current.toggle(true);
      });

      // The bug this replaced: the beat went open, `.tday-empty-exit` was
      // `animation: none`, and the reader got the scene's exit length of a
      // static picture followed by the whole screen changing at once.
      expect(result.current.expanded).toBe(true);
      expect(result.current.handoff).toBe("idle");
    });

    it("collapses on the same tap too — the rows have no fade to wait for either", () => {
      installReducedMotion(true);
      const { result } = mountHandoff();

      act(() => {
        result.current.toggle(true);
      });
      act(() => {
        result.current.toggle(true);
      });

      // `.tday-rows-exit` is `animation: none` under the preference and
      // `useFadeUnmount` skips its own delay, so the rows are gone on the tap
      // frame. Holding the scene off for a fade nobody is playing would be the
      // same dead wait from the other direction.
      expect(result.current.expanded).toBe(false);
      expect(result.current.handoff).toBe("idle");
    });

    it("arms no timer at all — nothing is left to fire later and undo the expand", async () => {
      installReducedMotion(true);
      const { result } = mountHandoff();

      act(() => {
        result.current.toggle(true);
      });
      expect(result.current.expanded).toBe(true);

      await act(async () => {
        await vi.advanceTimersByTimeAsync(ROWS_EXIT_MS + 10);
      });
      expect(result.current.expanded).toBe(true);
      expect(result.current.handoff).toBe("idle");
    });

    it("a mid-session flip reaches the toggle the header is already holding", () => {
      const media = installReducedMotion(false);
      const { result } = mountHandoff();

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
      expect(result.current.handoff).toBe("idle");
    });
  });

  it("a fresh instance never inherits a previous instance's pending state", () => {
    // Guards the "component remounts per scope/route" claim in the hook's
    // own doc comment: a brand-new mount always starts clean.
    const { result } = mountHandoff();
    expect(result.current.expanded).toBe(false);
    expect(result.current.handoff).toBe("idle");
  });
});

// @vitest-environment jsdom

/**
 * The second half of the undo complaint, one layer down from the first.
 *
 * Ending the celebration when a task comes back is right; cutting forty-six
 * pieces out of mid-air to do it is not — it is the same complaint the user made
 * about the paper flying over a restored row, made smaller. The burst's
 * existence used to BE the flag (`{celebrate ? <Confetti/> : null}`), so the
 * frame the celebration stopped being true was the frame the canvas left the
 * tree.
 *
 * So the two things worth pinning are that the burst OUTLIVES `play`, and that
 * it outlives it by a bounded amount rather than for ever. Neither is visible
 * from the kinematics — `envelopedAlpha` is one multiplication and says nothing
 * about when it is handed a zero — and neither is visible from a snapshot: what
 * is being asserted is when an element exists and what was painted while it did.
 *
 * Under reduced motion there is nothing to outlive. Nothing was ever painted,
 * the rAF loop returns before its first frame, and `docs/motion.md`'s fifth
 * idiom rule then says the wait must go with the trip: the canvas leaves on the
 * cancel frame.
 */

import { act, cleanup, render } from "@testing-library/react";
import { Leaf } from "lucide-react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import Confetti from "@/components/app/Confetti";
import EmptyState from "@/components/app/EmptyState";
import { DURATION_MS } from "@/lib/motion";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

function scene(celebrate: boolean) {
  return (
    <EmptyState icon={Leaf} accentColor="#22c55e" title="All done" celebrate={celebrate} />
  );
}

const canvas = (container: HTMLElement) => container.querySelector("canvas");

describe("the canvas the cancel used to take away", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("stays in the tree past the frame the celebration ends", () => {
    const { container, rerender } = render(scene(true));
    expect(canvas(container)).not.toBeNull();

    // The undo. `celebrate` is false from here on — the gate has cancelled — and
    // the burst has to be still on screen, still flying, fading.
    rerender(scene(false));
    expect(canvas(container)).not.toBeNull();

    // Same element, not a replacement: a canvas swapped for an equivalent one
    // would satisfy every presence assertion here and still cut, because the
    // pieces are rolled and the flight clock is stamped on mount.
    const held = canvas(container);
    act(() => {
      vi.advanceTimersByTime(DURATION_MS.quick / 2);
    });
    expect(canvas(container)).toBe(held);

    act(() => {
      vi.advanceTimersByTime(DURATION_MS.quick);
    });
    expect(canvas(container)).toBeNull();
  });

  it("does not linger for a scene that was never celebrating", () => {
    const { container, rerender } = render(scene(false));
    expect(canvas(container)).toBeNull();
    rerender(scene(false));
    expect(canvas(container)).toBeNull();
  });
});

describe("the same cancel with animations turned off", () => {
  it("takes the canvas away on the cancel frame, with no wait left standing", () => {
    const original = window.matchMedia;
    window.matchMedia = ((query: string) => ({
      matches: query.includes("prefers-reduced-motion"),
      media: query,
      addEventListener: () => {
        /* read once at the instant the fade would be armed, never subscribed */
      },
      removeEventListener: () => {
        /* see above */
      },
    })) as unknown as typeof window.matchMedia;

    try {
      const { container, rerender } = render(scene(true));
      rerender(scene(false));
      // Nothing was ever painted into it — `Confetti`'s effect returns before the
      // rAF loop under this preference — so holding it here would be a stall in
      // front of the restored row rather than a courtesy.
      expect(canvas(container)).toBeNull();
    } finally {
      window.matchMedia = original;
    }
  });
});

/**
 * What the pieces are actually painted at, which is the half `EmptyState`'s
 * presence assertions above cannot see.
 *
 * A hand-cranked frame loop, the same technique `empty-state-celebration-order`
 * uses: `requestAnimationFrame` hands back whatever the last call queued, so the
 * test decides what time it is when the next frame runs.
 */
describe("the envelope, at the draw site", () => {
  function installFrames() {
    let queued: FrameRequestCallback | null = null;
    vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => {
      queued = callback;
      return 1;
    });
    vi.stubGlobal("cancelAnimationFrame", () => {});
    vi.stubGlobal(
      "ResizeObserver",
      class {
        observe() {}
        disconnect() {}
      },
    );
    return {
      frame: (at: number) => {
        const callback = queued;
        queued = null;
        callback?.(at);
      },
      queuedAnother: () => queued !== null,
    };
  }

  /** Only the members `Confetti` draws with, plus a record of what it painted. */
  function fakeContext() {
    const painted: number[] = [];
    const context = {
      setTransform: vi.fn(),
      clearRect: vi.fn(),
      save: vi.fn(),
      restore: vi.fn(),
      translate: vi.fn(),
      rotate: vi.fn(),
      beginPath: vi.fn(),
      roundRect: vi.fn(),
      fill: vi.fn(() => {
        painted.push(context.globalAlpha);
      }),
      globalAlpha: 1,
      fillStyle: "",
    };
    return { context, painted };
  }

  it("keeps flying at a fading alpha, then stops once the envelope is spent", () => {
    const { context, painted } = fakeContext();
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
      context as unknown as CanvasRenderingContext2D,
    );
    const mounted = 1_000;
    const now = vi.spyOn(performance, "now").mockReturnValue(mounted);
    const { frame, queuedAnother } = installFrames();

    const { rerender } = render(<Confetti accentColor="#22c55e" play />);

    // A tenth of the way in, well before the pieces' own fade begins at 0.60 of
    // the flight, so every piece is at full opacity and the only thing that can
    // move this number later is the envelope.
    frame(mounted + 200);
    expect(painted.length).toBeGreaterThan(0);
    expect(Math.max(...painted)).toBe(1);

    // The undo lands. The cancel is stamped off the same clock the flight is.
    const cancelledAt = mounted + 200;
    now.mockReturnValue(cancelledAt);
    rerender(<Confetti accentColor="#22c55e" play={false} />);

    // Half an envelope later: still painting — the pieces are still travelling,
    // spinning and flipping — but no longer at full strength.
    painted.length = 0;
    frame(cancelledAt + DURATION_MS.quick / 2);
    expect(painted.length).toBeGreaterThan(0);
    expect(Math.max(...painted)).toBeLessThan(1);
    expect(Math.max(...painted)).toBeGreaterThan(0);
    expect(queuedAnother()).toBe(true);

    // And at the end of it: nothing painted, and no frame asked for. The flight
    // clock is nowhere near done — this is the envelope ending the run, which is
    // what leaves the host free to take the element away on the same rung.
    painted.length = 0;
    frame(cancelledAt + DURATION_MS.quick + 1);
    expect(painted).toHaveLength(0);
    expect(queuedAnother()).toBe(false);
  });

  it("throws fresh paper for a celebration that arrives inside the envelope", () => {
    // The other side of the same latch. The host keeps this mounted for the
    // length of the fade, so a completion landing inside it would otherwise
    // adopt a flight that is already half spent — the window would re-open and
    // nothing would be thrown. A run generation is what makes the next
    // celebration a fresh run, exactly as an unmount would have.
    const { context } = fakeContext();
    const getContext = vi
      .spyOn(HTMLCanvasElement.prototype, "getContext")
      .mockReturnValue(context as unknown as CanvasRenderingContext2D);
    vi.spyOn(performance, "now").mockReturnValue(1_000);
    installFrames();

    const { rerender } = render(<Confetti accentColor="#22c55e" play />);
    rerender(<Confetti accentColor="#22c55e" play={false} />);
    expect(getContext).toHaveBeenCalledTimes(1);

    rerender(<Confetti accentColor="#22c55e" play />);
    expect(getContext).toHaveBeenCalledTimes(2);
  });

  it("does not re-roll the burst when the cancel arrives", () => {
    // `play` is deliberately absent from the draw effect's dependencies: re-running
    // it re-rolls `fan()` and re-stamps the flight's start, which teleports every
    // piece back to the launch patch in the frame it was supposed to start fading.
    // A second `getContext` is the fingerprint of that effect having re-run.
    const { context } = fakeContext();
    const getContext = vi
      .spyOn(HTMLCanvasElement.prototype, "getContext")
      .mockReturnValue(context as unknown as CanvasRenderingContext2D);
    vi.spyOn(performance, "now").mockReturnValue(1_000);
    installFrames();

    const { rerender } = render(<Confetti accentColor="#22c55e" play />);
    expect(getContext).toHaveBeenCalledTimes(1);

    rerender(<Confetti accentColor="#22c55e" play={false} />);
    expect(getContext).toHaveBeenCalledTimes(1);
  });
});

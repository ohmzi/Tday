// @vitest-environment jsdom

/**
 * The order the completion celebration's three legs run in when the empty state is
 * drawn INLINE — in the slot the last row just vacated, with the rest of the page
 * travelling to its new position underneath.
 *
 * `MotionTokens.kt` calls `PlacementLead` and `CelebrationLead` "added, never
 * traded", and the whole argument in `globals.css` for the second one — the burst
 * leads, the scene follows through it — assumes the burst has a still page to fly
 * over. An inline host breaks both at once if it lets the two leads overlap: the
 * paper is thrown across a screen that is still sliding, and the scene arrives on
 * the frame the tiles land instead of a beat after the burst.
 *
 * So the two halves are asserted where each one actually lives: the scene's wait is
 * a custom property CSS adds to its own lead, and the burst's wait is a clock inside
 * `Confetti` that nothing else can see.
 */

import { cleanup, render } from "@testing-library/react";
import { Leaf } from "lucide-react";
import { afterEach, describe, expect, it, vi } from "vitest";

import Confetti from "@/components/app/Confetti";
import EmptyState from "@/components/app/EmptyState";
import { DELAY_MS } from "@/lib/motion";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

function renderScene(celebrationStartDelayMs?: number) {
  return render(
    <EmptyState
      icon={Leaf}
      accentColor="#22c55e"
      title="All done"
      celebrate
      celebrationStartDelayMs={celebrationStartDelayMs}
    />,
  );
}

describe("the scene's own wait", () => {
  it("carries an inline host's travel as the lead's offset, not in place of it", () => {
    const { container } = renderScene(DELAY_MS.placementLead);

    // On the node that carries the celebrating class, because that is the one whose
    // `animation-delay` the property is added into. Read as a property rather than
    // as a computed delay: jsdom applies no stylesheet, so the `calc` itself is only
    // ever provable in a browser — what is provable here is that the host's number
    // reaches the element the rule reads it from.
    const scene = container.querySelector(".tday-empty-enter-celebrating");
    expect(scene).not.toBeNull();
    expect(
      (scene as HTMLElement).style.getPropertyValue("--tday-celebration-start"),
    ).toBe(`${DELAY_MS.placementLead}ms`);
  });

  it("leaves the property unset for a host whose page does not move", () => {
    // The overlay callers — the list screens — draw this over a page where nothing
    // travels, and must land on the CSS fallback rather than on some host's number.
    const { container } = renderScene();
    const scene = container.querySelector(".tday-empty-enter-celebrating");
    expect(
      (scene as HTMLElement).style.getPropertyValue("--tday-celebration-start"),
    ).toBe("");
  });
});

describe("the burst's own wait", () => {
  /** Only the members `Confetti` actually draws with. */
  function fakeContext() {
    return {
      setTransform: vi.fn(),
      clearRect: vi.fn(),
      save: vi.fn(),
      restore: vi.fn(),
      translate: vi.fn(),
      rotate: vi.fn(),
      beginPath: vi.fn(),
      roundRect: vi.fn(),
      fill: vi.fn(),
      globalAlpha: 1,
      fillStyle: "",
    };
  }

  /**
   * A hand-cranked frame loop. `requestAnimationFrame` hands back whatever the last
   * call queued, so a test can decide what time it is when the next frame runs —
   * which is the only way to ask "had it drawn yet?" about a specific instant.
   */
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
    return (at: number) => {
      const callback = queued;
      queued = null;
      callback?.(at);
    };
  }

  it("throws nothing while the feed is still travelling, then throws", () => {
    const context = fakeContext();
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
      context as unknown as CanvasRenderingContext2D,
    );
    const mounted = 1_000;
    vi.spyOn(performance, "now").mockReturnValue(mounted);
    const frame = installFrames();

    render(<Confetti accentColor="#22c55e" startDelayMs={DELAY_MS.placementLead} />);

    // The last frame before the tiles land. A single piece drawn here is a piece
    // crossing a moving page, which is the failure this delay exists to stop.
    frame(mounted + DELAY_MS.placementLead - 1);
    expect(context.fill).not.toHaveBeenCalled();

    // Mid-flight — comfortably inside the burst, which runs for 1.8s once it starts.
    frame(mounted + DELAY_MS.placementLead + 900);
    expect(context.fill).toHaveBeenCalled();
  });
});

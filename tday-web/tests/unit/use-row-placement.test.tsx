// @vitest-environment jsdom

/**
 * `useRowPlacement` is First-Last-Invert-Play, and the only part of it that is visible
 * from the outside is the call it makes to `Element.animate`: which children it decided
 * had moved, how far it thinks each one travelled, and on which rung. jsdom has no
 * layout and no Web Animations API, so both are stubbed here — the stub for `animate`
 * turns the hook's decisions into assertable records, and the stub for
 * `getBoundingClientRect` gives it a layout simple enough to predict by hand: children
 * stack in DOM order, each one as tall as its own `data-height`, separated by the
 * container's `data-gap`.
 *
 * That fake layout is the point of the whole file rather than a convenience. Because it
 * is derived from the CURRENT DOM, removing a child moves everything below it exactly
 * as a browser would, which means the measurement the hook takes during render and the
 * one it takes after the commit disagree by the real travel distance — the thing being
 * tested — instead of by a number the test told it to expect.
 */

import { useLayoutEffect } from "react";
import { cleanup, render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useRowPlacement } from "@/hooks/useRowPlacement";
import { DURATION_MS, EASE } from "@/lib/motion";
import { installReducedMotion } from "../setup/reduced-motion";

const ROW_HEIGHT = 60;

type AnimateCall = {
  element: Element;
  keyframes: Keyframe[];
  options: KeyframeAnimationOptions;
  animation: { cancel: ReturnType<typeof vi.fn> };
};

let calls: AnimateCall[] = [];

const realGetBoundingClientRect = Element.prototype.getBoundingClientRect;
const realMatchMedia = window.matchMedia;

function heightOf(element: Element): number {
  const declared = element.getAttribute("data-height");
  return declared == null ? ROW_HEIGHT : Number(declared);
}

/**
 * The container sits at the viewport origin and its children stack below it. Anything
 * that is not a tracked child — the container itself, the wrappers testing-library puts
 * around it — reads as the origin too, which is what makes the container's own rect a
 * fixed base for the relative offsets the hook works in.
 */
const COLUMN_WIDTH = 320;

function fakeRect(element: Element): DOMRect {
  // `data-hidden` stands in for `display: none`, which reports every box in the subtree
  // as zero-sized and sitting at the origin.
  if (element.closest("[data-hidden]")) return emptyRect();

  const parent = element.parentElement;
  let top = 0;
  if (parent?.hasAttribute("data-placement-container")) {
    const gap = Number(parent.getAttribute("data-gap") ?? 0);
    for (const sibling of Array.from(parent.children)) {
      if (sibling === element) break;
      top += heightOf(sibling) + gap;
    }
  }
  const height = parent ? heightOf(element) : 0;
  return {
    top,
    left: 0,
    right: COLUMN_WIDTH,
    bottom: top + height,
    width: COLUMN_WIDTH,
    height,
    x: 0,
    y: top,
    toJSON: () => ({}),
  } as DOMRect;
}

function emptyRect(): DOMRect {
  return {
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    width: 0,
    height: 0,
    x: 0,
    y: 0,
    toJSON: () => ({}),
  } as DOMRect;
}

function installFakeLayout() {
  Element.prototype.getBoundingClientRect = function fake(this: Element) {
    return fakeRect(this);
  };
}

function installAnimate() {
  Element.prototype.animate = function fake(
    this: Element,
    keyframes: Keyframe[],
    options: KeyframeAnimationOptions,
  ) {
    const animation = { cancel: vi.fn(), onfinish: null };
    calls.push({ element: this, keyframes, options, animation });
    return animation as unknown as Animation;
  } as unknown as typeof Element.prototype.animate;
}

/** The travel a call was asked to invert, as the `dy` the keyframes spell out. */
function travelOf(call: AnimateCall): string {
  return String(call.keyframes[0].transform);
}

function callFor(id: string): AnimateCall | undefined {
  return calls.find((call) => call.element.getAttribute("data-id") === id);
}

function Column({
  ids,
  gap = 0,
  heights = {},
  hidden = false,
}: {
  ids: string[];
  gap?: number;
  heights?: Record<string, number>;
  hidden?: boolean;
}) {
  const placementRef = useRowPlacement<HTMLDivElement>();
  return (
    <div
      ref={placementRef}
      data-placement-container
      data-gap={gap}
      // Spread rather than `data-hidden={hidden}`, which would render the attribute as
      // the string "false" and still match `[data-hidden]`.
      {...(hidden ? { "data-hidden": true } : null)}
    >
      {ids.map((id) => (
        <div key={id} data-id={id} data-height={heights[id] ?? ROW_HEIGHT} />
      ))}
    </div>
  );
}

describe("useRowPlacement", () => {
  beforeEach(() => {
    calls = [];
    installFakeLayout();
    installAnimate();
  });

  afterEach(() => {
    cleanup();
    Element.prototype.getBoundingClientRect = realGetBoundingClientRect;
    delete (Element.prototype as Partial<Element>).animate;
    window.matchMedia = realMatchMedia;
  });

  it("travels the rows a removed row displaced, and leaves the ones above it alone", () => {
    const { rerender } = render(<Column ids={["a", "b", "c"]} />);
    expect(calls).toHaveLength(0);

    rerender(<Column ids={["a", "c"]} />);

    // `c` was the third row and is now the second: it is put back where it was — a row
    // lower — and travels up from there. `a` never moved, so it is never touched.
    expect(calls).toHaveLength(1);
    expect(travelOf(calls[0])).toBe(`translate(0px, ${ROW_HEIGHT}px)`);
    expect(calls[0].keyframes[1].transform).toBe("translate(0px, 0px)");
    expect(callFor("a")).toBeUndefined();
  });

  it("runs the travel on the Emphasis rung and the Standard curve", () => {
    const { rerender } = render(<Column ids={["a", "b"]} />);
    rerender(<Column ids={["b"]} />);

    // Compared against the vocabulary rather than against 320 and the four control
    // points, because the claim being pinned is which RUNG a placement is on — geometry
    // decides that, and Change sits one rung below with no visible difference in a
    // screenshot. The numbers behind the names are pinned across all three clients by
    // `motion-parity.test.ts`, which is the file that may not import them.
    expect(calls[0].options).toEqual({
      duration: DURATION_MS.emphasis,
      easing: EASE.standard,
    });
  });

  it("travels the rows an arriving row pushes down, but never the arrival itself", () => {
    const { rerender } = render(<Column ids={["b", "c"]} />);
    rerender(<Column ids={["a", "b", "c"]} />);

    // A row this hook has never measured has no slot to have come from — whatever fades
    // it in owns its arrival. The two it displaced are inverted upward and fall into
    // place.
    expect(callFor("a")).toBeUndefined();
    expect(travelOf(callFor("b")!)).toBe(`translate(0px, ${-ROW_HEIGHT}px)`);
    expect(travelOf(callFor("c")!)).toBe(`translate(0px, ${-ROW_HEIGHT}px)`);
  });

  it("travels the column when a section leaves, for the gap alone", () => {
    // The scheduled task home's Today wrapper, at the moment that matters: the last
    // incomplete row has already closed its own box, so the section is zero-height and
    // has cost the page nothing yet. Unmounting it still takes the column's flex gap —
    // the one piece of the drop no row-level collapse can reach, because it belongs to
    // the column and not to the row.
    const gap = 16;
    const heights = { today: 0, tiles: 200 };
    const { rerender } = render(<Column ids={["today", "tiles"]} gap={gap} heights={heights} />);

    rerender(<Column ids={["tiles"]} gap={gap} heights={heights} />);

    expect(travelOf(callFor("tiles")!)).toBe(`translate(0px, ${gap}px)`);
  });

  it("does not animate under reduced motion, and the rows are already placed", () => {
    installReducedMotion(true);
    const { container, rerender } = render(<Column ids={["a", "b", "c"]} />);

    rerender(<Column ids={["a", "c"]} />);

    // The trip is gone; the destination is not. Nothing had to be drawn for that to be
    // true — the DOM is at its finished layout the moment React commits, and this hook
    // declining to animate is the whole of the reduced-motion path.
    expect(calls).toHaveLength(0);
    expect(Array.from(container.querySelectorAll("[data-id]")).map((el) => el.getAttribute("data-id")))
      .toEqual(["a", "c"]);
  });

  it("survives an environment with no Element.animate", () => {
    // jsdom is that environment, and so is any browser old enough to matter: a feed that
    // cannot animate its placements still has to show them placed.
    delete (Element.prototype as Partial<Element>).animate;
    const { container, rerender } = render(<Column ids={["a", "b", "c"]} />);

    expect(() => rerender(<Column ids={["a", "c"]} />)).not.toThrow();
    expect(container.querySelectorAll("[data-id]")).toHaveLength(2);
  });

  it("cancels a placement that is still in flight rather than stacking a second one", () => {
    const { rerender } = render(<Column ids={["a", "b", "c", "d"]} />);
    rerender(<Column ids={["a", "c", "d"]} />);
    const firstTravel = callFor("d")!;

    rerender(<Column ids={["a", "d"]} />);

    // Two live animations on one element would composite into a transform neither of
    // them asked for, and the second would be the only one this hook could still stop.
    expect(firstTravel.animation.cancel).toHaveBeenCalled();
    expect(calls.filter((call) => call.element === firstTravel.element)).toHaveLength(2);
  });

  it("ignores a sub-pixel re-layout", () => {
    const { rerender } = render(<Column ids={["a", "b"]} heights={{ a: ROW_HEIGHT }} />);

    // A fraction of a pixel is a scrollbar arriving or a font settling, not a row taking
    // a new slot; inverting it would put a transform on the feed for travel nobody can
    // see.
    rerender(<Column ids={["a", "b"]} heights={{ a: ROW_HEIGHT + 0.4 }} />);

    expect(calls).toHaveLength(0);
  });

  it("measures where a row actually is, not where the last commit left it", () => {
    // This is the reason FIRST is read during render, and the one case that decides it.
    //
    // A ticked row spends the whole Emphasis rung closing its own box before it is
    // pruned (`taskCompletionTiming.ts`), and that collapse is a CSS transition on a
    // style the row set for itself: it moves every row below it, over 320ms, with no
    // commit behind any of it. The layout React last saw is a whole row out of date by
    // the time the prune arrives. A hook that compared against that would believe the
    // neighbours were still a row lower and yank them back down to replay a trip the
    // browser had already finished — the one bug in this hook a user would call a
    // glitch rather than a missing animation.
    //
    // So the collapse is made here the way the browser makes it: by changing the
    // layout without telling React.
    const { container, rerender } = render(<Column ids={["a", "b", "c"]} />);
    container.querySelector('[data-id="b"]')!.setAttribute("data-height", "0");

    rerender(<Column ids={["a", "c"]} />);

    // `c` is where it already was, so it has nowhere to travel from.
    expect(calls).toHaveLength(0);
  });

  it("does not place a feed the frame it is shown", () => {
    // A container that is not being displayed reports every child as a zero box at the
    // origin. Taken at face value that reads as every row having travelled the length of
    // the list, and the feed flies in from the top the first time it is revealed —
    // which is not a placement, it is a list that was always there.
    const { rerender } = render(<Column ids={["a", "b", "c"]} hidden />);
    rerender(<Column ids={["a", "b", "c"]} />);

    expect(calls).toHaveLength(0);
  });

  it("never measures a container it does not have yet", () => {
    // The mount render has no node to read, and the mount commit therefore has nothing
    // to compare against. A placement on first paint would animate the whole feed in
    // from wherever the browser had last painted something else.
    const seen: number[] = [];
    function Probe() {
      const placementRef = useRowPlacement<HTMLDivElement>();
      useLayoutEffect(() => {
        seen.push(calls.length);
      });
      return (
        <div ref={placementRef} data-placement-container data-gap={0}>
          <div data-id="only" />
        </div>
      );
    }

    render(<Probe />);
    expect(seen).toEqual([0]);
  });
});

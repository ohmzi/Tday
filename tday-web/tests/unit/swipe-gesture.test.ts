/**
 * The arithmetic four swipeable surfaces now share, tested without a DOM.
 *
 * `src/lib/swipeGesture.ts` was pulled out of two hooks that had grown the same
 * shapes independently, and the point of pulling it out is that the shapes can
 * be argued about here rather than inferred from what a jsdom row happened to
 * end up at. Every number below is a property of the curve or the sampler, not
 * of a surface: how much a limit gives, what a held finger is worth, what a
 * release the platform reported twice in one frame is worth.
 */

import { describe, expect, it } from "vitest";
import { DURATION_MS } from "@/lib/motion";
import {
  createSwipeSampler,
  projectedRest,
  rubberBand,
  SWIPE_SETTLE_HOME,
  SWIPE_SETTLE_OPEN,
} from "@/lib/swipeGesture";

describe("the give at a limit", () => {
  it("answers a small pull almost pixel for pixel", () => {
    // The first pixels past a limit have to feel like the surface heard them,
    // or the give reads as a delayed hard stop rather than as resistance.
    expect(rubberBand(4, 40)).toBeGreaterThan(3);
    expect(rubberBand(4, 40)).toBeLessThan(4);
  });

  it("approaches the give it was granted and never reaches it", () => {
    // A limit that can be pushed all the way through is not a limit, and one
    // that stops dead is the defect this replaced. Approaching is the only
    // answer that keeps saying "heard, and still no" for as long as it is asked.
    expect(rubberBand(1000, 40)).toBeLessThan(40);
    expect(rubberBand(1000, 40)).toBeGreaterThan(39);
    // Past a pull no screen is wide enough to receive, the exponential underflows
    // and the curve sits exactly on the asymptote. That is still the limit and
    // never beyond it, which is the property that matters; asserting a strict
    // inequality here would be asserting something about doubles instead.
    expect(rubberBand(10_000, 40)).toBeLessThanOrEqual(40);
  });

  it("gives nothing at the limit itself, or without a give to spend", () => {
    expect(rubberBand(0, 40)).toBe(0);
    expect(rubberBand(-10, 40)).toBe(0);
    expect(rubberBand(50, 0)).toBe(0);
  });
});

describe("what a release was worth", () => {
  it("reads a steady drag as the speed it was travelling at", () => {
    const sampler = createSwipeSampler();
    sampler.sample(300, 0);
    sampler.sample(260, 20);
    sampler.sample(220, 40);

    expect(sampler.release(180, 60)).toBeCloseTo(-2, 5);
  });

  it("is worth nothing at all when the finger stopped before it lifted", () => {
    // The bug this exists to refuse: a fast drag that ends in a hold is a
    // decision NOT to flick, and a sampler that remembered the fast part would
    // commit it anyway. Everything older than the window drops out, which leaves
    // the release alone in it.
    const sampler = createSwipeSampler();
    sampler.sample(300, 0);
    sampler.sample(100, 40);

    expect(sampler.release(100, 400)).toBe(0);
  });

  it("is worth nothing across less than a frame", () => {
    // A browser coalescing moves can put two of them a fraction of a millisecond
    // apart. Dividing a real distance by that gap measures event delivery, not a
    // finger, and answers a velocity no hand ever produced.
    const sampler = createSwipeSampler();
    sampler.sample(300, 0);

    expect(sampler.release(260, 0.4)).toBe(0);
  });

  it("keeps each gesture's readings to itself", () => {
    // A sampler that outlived its gesture would answer the next release with the
    // speed of the last one — the same class of leak the pager's `endGesture`
    // exists to close on its other refs.
    const first = createSwipeSampler();
    first.sample(300, 0);
    expect(first.release(100, 50)).toBeLessThan(0);

    const second = createSwipeSampler();
    second.sample(300, 0);
    expect(second.release(300, 50)).toBe(0);
  });
});

describe("where the release was going", () => {
  it("carries a still-travelling finger past where it let go", () => {
    expect(projectedRest(-40, -2)).toBe(-40 - 2 * DURATION_MS.quick);
  });

  it("pulls a finger that was already coming back short of where it let go", () => {
    // The half nobody writes: a long drag being walked back commits under a
    // position-only rule, because the position is still past the threshold.
    expect(projectedRest(-108, 0.2)).toBeGreaterThan(-108);
  });

  it("leaves a release with no speed exactly where it is", () => {
    expect(projectedRest(-108, 0)).toBe(-108);
  });
});

describe("the two settles", () => {
  it("names tokens and not numbers, on the curve a release continues on", () => {
    // The whole point of the pair: `docs/motion.md` reserves the Gesture curve
    // for a surface carrying on after a finger lets go, and web spends it where
    // Android and iOS spend the Gesture spring.
    expect(SWIPE_SETTLE_HOME).toContain("var(--tday-ease-gesture)");
    expect(SWIPE_SETTLE_OPEN).toContain("var(--tday-ease-gesture)");
    expect(`${SWIPE_SETTLE_HOME}${SWIPE_SETTLE_OPEN}`).not.toMatch(/\d/);
  });

  it("sends a surface home on the shorter of the two", () => {
    // The first idiom rule: the trip home undoes the trip out, so it is the one
    // that has to be shorter. Read off the rung names rather than the numbers,
    // which is the only reading that survives a rung being retimed upstream.
    expect(SWIPE_SETTLE_HOME).toContain("var(--tday-duration-quick)");
    expect(SWIPE_SETTLE_OPEN).toContain("var(--tday-duration-emphasis)");
    expect(DURATION_MS.quick).toBeLessThan(DURATION_MS.emphasis);
  });
});

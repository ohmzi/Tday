// @vitest-environment jsdom

/**
 * The hero header's search open, proved by what it asks the browser to play.
 *
 * The capsule's width and translateX are written straight onto the node by a
 * rAF, because both are also scroll-derived and a CSS transition on them would
 * lag the fold by its own length. That leaves the open/close step with no
 * declaration anywhere in the markup: it is armed imperatively, and the only
 * thing visible from outside the component is the `Element.animate` call. So
 * `animate` is stubbed here and the calls are the assertions — the same shape
 * `use-row-placement.test.tsx` uses for the same reason.
 *
 * What this pins is the half a refactor breaks silently. The ratchet in
 * `tests/guardrails/motion-parity.test.ts` counts raw literals, so it cannot
 * see `DURATION_MS.change` written where `DURATION_MS.emphasis` belongs — and
 * the rung here is not a matter of taste: the capsule changes where it is and
 * how big it is, which is `docs/motion.md`'s second idiom rule, while the title
 * only fades and leaves on `Quick` with the mark and the two round buttons.
 * Comparing against the token rather than against 320 is deliberate for the
 * same reason: the number is the exporter's business, the rung is this file's.
 *
 * jsdom lays nothing out, so `clientWidth` is stubbed — without it the rAF
 * bails on a zero-width header and every assertion below would be about a frame
 * that never ran.
 */

import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import RootFeedHeroHeader, {
  rootFeedHeroHeaderMetrics,
} from "@/components/app/RootFeedHeroHeader";
import { nativeAppScrollAttribute } from "@/components/app/nativeAppLayout";
import { DURATION_MS, EASE } from "@/lib/motion";
import { installReducedMotion } from "../setup/reduced-motion";

const HEADER_WIDTH = 390;
const TITLE = "Today";

type AnimateCall = {
  element: Element;
  keyframes: Keyframe[];
  options: KeyframeAnimationOptions;
};

let calls: AnimateCall[] = [];
let scrollers: HTMLElement[] = [];

const realAnimate = Element.prototype.animate;
const realMatchMedia = window.matchMedia;

const baseProps = {
  title: TITLE,
  mark: "timeOfDay" as const,
  searchQuery: "",
  searchPlaceholder: "Search tasks",
  searchPlaceholderShort: "Search",
  searchAriaLabel: "Search tasks",
  createListAriaLabel: "Create list",
  settingsAriaLabel: "More",
  onSearchQueryChange: () => {},
  onSearchOpenChange: () => {},
  onCreateList: () => {},
  onOpenSettings: () => {},
};

/**
 * The clipping shell the rAF sizes — reached through the input rather than by
 * index, so a sibling added to the header does not quietly re-point this at
 * something else.
 */
function capsuleOf(root: HTMLElement): HTMLElement {
  const capsule = root
    .querySelector("input")
    ?.closest<HTMLElement>("[class~='overflow-hidden'][class~='rounded-full']");
  if (!capsule) throw new Error("no capsule in the rendered header");
  return capsule;
}

/**
 * Renders the header inside a scroller the way the app shell does, at a scroll
 * offset the caller picks: the title's opacity is a function of how far the
 * hero has folded, so a header sitting at the top has nothing to fade.
 */
function renderHeader({ scrollTop = 0 } = {}) {
  const scroller = document.createElement("div");
  scroller.setAttribute(nativeAppScrollAttribute, "");
  // jsdom has no layout, so `scrollTop` is a permanent 0 unless it is given one.
  Object.defineProperty(scroller, "scrollTop", { value: scrollTop, writable: true });
  document.body.appendChild(scroller);
  scrollers.push(scroller);

  const view = render(<RootFeedHeroHeader {...baseProps} searchOpen={false} />, {
    container: scroller,
  });
  return {
    scroller,
    capsule: capsuleOf(scroller),
    setSearchOpen: (open: boolean) =>
      view.rerender(<RootFeedHeroHeader {...baseProps} searchOpen={open} />),
  };
}

const callsOn = (element: Element) => calls.filter((call) => call.element === element);

beforeEach(() => {
  calls = [];
  scrollers = [];
  Object.defineProperty(HTMLElement.prototype, "clientWidth", {
    configurable: true,
    get: () => HEADER_WIDTH,
  });
  Element.prototype.animate = function fake(
    this: Element,
    keyframes: Keyframe[],
    options: KeyframeAnimationOptions,
  ) {
    calls.push({ element: this, keyframes, options });
    return { cancel: vi.fn() } as unknown as Animation;
  } as unknown as typeof Element.prototype.animate;
});

afterEach(() => {
  cleanup();
  for (const scroller of scrollers) scroller.remove();
  delete (HTMLElement.prototype as Partial<HTMLElement>).clientWidth;
  Element.prototype.animate = realAnimate;
  window.matchMedia = realMatchMedia;
});

describe("root feed hero search capsule", () => {
  it("morphs the capsule open on the Emphasis rung, and arms nothing on mount", () => {
    const { capsule, setSearchOpen } = renderHeader();

    // The first commit is not an open. A morph armed here would play the
    // capsule in from wherever the previous screen left it every time the tab
    // is mounted.
    expect(calls).toHaveLength(0);

    setSearchOpen(true);

    const [morph, ...extra] = callsOn(capsule);
    expect(extra).toHaveLength(0);
    expect(morph.options).toEqual({
      duration: DURATION_MS.emphasis,
      easing: EASE.standard,
    });
    // The trip ends where the rAF has already put the node, which is what makes
    // this a playback of the open rather than a second opinion about it.
    expect(morph.keyframes.at(-1)).toEqual({
      width: capsule.style.width,
      transform: capsule.style.transform,
    });
    expect(capsule.style.width).toBe(`${HEADER_WIDTH}px`);
  });

  it("clears the docked title on Quick, alongside the controls it takes the row from", () => {
    // Folded all the way down: up in its hero position the title sits clear of
    // the toolbar row and does not fade at all, so there would be nothing to
    // watch here.
    const { setSearchOpen } = renderHeader({
      scrollTop: rootFeedHeroHeaderMetrics.collapseDistance,
    });

    setSearchOpen(true);

    const [fade, ...extra] = callsOn(screen.getByRole("button", { name: TITLE }));
    expect(extra).toHaveLength(0);
    expect(fade.options).toEqual({ duration: DURATION_MS.quick, easing: EASE.standard });
    // A departure, not a step: the ends are the two opacities, and the second
    // one is the value the rAF settled on.
    expect(fade.keyframes.map((frame) => frame.opacity)).toEqual(["1", "0"]);
  });

  it("draws the open capsule without the trip when motion is switched off", () => {
    installReducedMotion(true);
    const { capsule, setSearchOpen } = renderHeader();

    setSearchOpen(true);

    // `docs/motion.md`'s fifth idiom rule: reduced motion removes the trip and
    // never the destination. The geometry is written by the rAF above the
    // guard, so the field is simply already open.
    expect(calls).toHaveLength(0);
    expect(capsule.style.width).toBe(`${HEADER_WIDTH}px`);
    expect(capsule.style.transform).toBe("translateX(0px)");
  });
});

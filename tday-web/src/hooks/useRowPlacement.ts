import { useEffect, useLayoutEffect, useRef, type RefObject } from "react";
import { FEED_ITEM_EASING, FEED_ITEM_PLACEMENT_MS } from "@/lib/feedItemMotion";
import { prefersReducedMotion } from "@/lib/prefersReducedMotion";

/**
 * First-Last-Invert-Play for the direct children of one container: whatever a commit
 * moved is put back where it was and travelled to its new slot, instead of arriving
 * there in a single frame.
 *
 * This is the piece web did not have. Android gets it from `Modifier.animateItem`
 * (`TdayFeedItemMotion.Placement`) and iOS from SwiftUI's implicit layout animation;
 * on web, a row leaving a feed teleported every neighbour below it — and a whole
 * section leaving took its container's flex gap with it, dropping the rest of the page
 * by the gap plus whatever the section still held. The travel is the same motion in
 * both cases, so it is the same hook: the children can be task rows or the sections of
 * a feed, and "row" is the name the vocabulary uses for the thing that takes a new slot.
 *
 * Placement is the Emphasis rung on the Standard curve, read through
 * `feedItemMotion.ts`. Geometry decides that rung and not importance: a slot is a
 * position (`docs/motion.md`, second idiom rule).
 *
 * Three things it deliberately does not do. It never animates a child it has not seen
 * before, because an arrival has no slot to travel from and belongs to whatever fades
 * it in. It never animates the child that left, which is already gone from the DOM by
 * the time this runs — a departing row animates itself on its way out
 * (`taskCompletionTiming.ts`). And it reads the reduce-motion preference at the instant
 * it would arm an animation rather than subscribing to it: the DOM is already at its
 * finished layout when this runs, so switching the trip off leaves the destination
 * drawn, which is the fifth idiom rule kept for free.
 */

/** Where a child sits inside its container. Relative, so page scroll cancels out. */
type Offset = { readonly x: number; readonly y: number };

/**
 * Below this, a "move" is sub-pixel rounding from a re-layout that changed nothing —
 * a scrollbar appearing, a font settling — and animating it would put a transform on
 * half the feed for travel no one can see.
 */
const MIN_TRAVEL_PX = 1;

/**
 * A child with no box at all has no slot, so it is left out of the record entirely and
 * skipped again on the way back in — the same rule at both ends.
 *
 * This is the guard for a container that is not being displayed: `display: none`
 * reports every child as a zero box at the origin, and a commit that straddles the
 * moment one is shown would otherwise read as every row having travelled the length of
 * the list, and fly the whole feed in from the top.
 */
function hasBox(rect: DOMRect): boolean {
  return rect.width !== 0 || rect.height !== 0;
}

function offsetsWithin(container: HTMLElement): Map<Element, Offset> {
  const base = container.getBoundingClientRect();
  const offsets = new Map<Element, Offset>();
  for (const child of Array.from(container.children)) {
    const rect = child.getBoundingClientRect();
    if (!hasBox(rect)) continue;
    offsets.set(child, { x: rect.left - base.left, y: rect.top - base.top });
  }
  return offsets;
}

export function useRowPlacement<T extends HTMLElement = HTMLElement>(): RefObject<T | null> {
  const containerRef = useRef<T | null>(null);
  const firstRef = useRef<Map<Element, Offset>>(new Map());
  const playingRef = useRef<Map<Element, Animation>>(new Map());

  // FIRST, and it has to be read here in the render phase, which is the one moment
  // that is both after React knows the list changed and before the DOM does.
  //
  // The obvious alternative — record positions in the layout effect and compare them
  // on the next commit — is wrong for this feed specifically, and wrong in a way that
  // is visible. A ticked row spends 320ms closing its own box (`taskCompletionTiming`'s
  // collapse) before it is pruned, and that collapse runs in CSS with no commit behind
  // it: the rows below have already travelled most of the way by the time the prune
  // arrives. Positions recorded at the previous commit would claim they were still a
  // whole row lower, and this hook would yank them back down to re-play a trip the
  // browser had just finished. Measuring here reads where they actually are.
  //
  // A layout read during render is a read, not a write: nothing in React's render phase
  // has touched the DOM, so the layout is clean and this costs a cache hit rather than
  // a reflow. A render React abandons is harmless for the same reason — the record
  // still says where the children are.
  const container = containerRef.current;
  if (container) firstRef.current = offsetsWithin(container);

  // No dependency list: the whole point is to catch a move this hook was not told
  // about, and the caller has no way to name one.
  useLayoutEffect(() => {
    const node = containerRef.current;
    if (!node) return;

    const first = firstRef.current;
    const playing = playingRef.current;

    // Every in-flight placement is cancelled before anything is measured, so what gets
    // read below is settled layout rather than layout plus a transform. The cost is
    // that a placement interrupted mid-travel restarts on a fresh clock instead of
    // resuming — but it restarts from where the child actually is (FIRST was measured
    // with the transform still applied, because that is where the eye had it), so the
    // travel stays continuous and only its length is wrong. Resuming exactly would
    // mean carrying each animation's keyframes and current time forward, which is a
    // lot of machinery for a case that needs a commit to land inside 320ms of travel.
    for (const animation of playing.values()) animation.cancel();
    playing.clear();

    // Asked here rather than subscribed to, the same way `useFadeUnmount` asks: the
    // answer is only ever wanted at the instant a placement would arm. Every child is
    // already at its destination — this returns before adding the trip, never before
    // drawing the finished frame.
    if (prefersReducedMotion()) return;

    const base = node.getBoundingClientRect();
    for (const child of Array.from(node.children)) {
      const from = first.get(child);
      if (!from) continue;

      const rect = child.getBoundingClientRect();
      if (!hasBox(rect)) continue;

      const to = { x: rect.left - base.left, y: rect.top - base.top };
      const dx = from.x - to.x;
      const dy = from.y - to.y;
      if (Math.abs(dx) < MIN_TRAVEL_PX && Math.abs(dy) < MIN_TRAVEL_PX) continue;

      // Not every environment has the Web Animations API — jsdom has no `animate` at
      // all — and a feed that cannot animate its placements must still show them
      // placed, which it already does.
      if (typeof child.animate !== "function") continue;

      const animation = child.animate(
        [
          { transform: `translate(${dx}px, ${dy}px)` },
          { transform: "translate(0px, 0px)" },
        ],
        { duration: FEED_ITEM_PLACEMENT_MS, easing: FEED_ITEM_EASING },
      );
      playing.set(child, animation);
      animation.onfinish = () => {
        // Guarded against the entry having been replaced by a later travel: dropping
        // someone else's animation from the map would leave it uncancellable.
        if (playing.get(child) === animation) playing.delete(child);
      };
    }
  });

  // Detached children keep no animation running, but the map would keep the nodes
  // themselves alive for as long as anything held the ref.
  useEffect(
    () => () => {
      const playing = playingRef.current;
      for (const animation of playing.values()) animation.cancel();
      playing.clear();
    },
    [],
  );

  return containerRef;
}

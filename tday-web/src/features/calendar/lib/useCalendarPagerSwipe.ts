import { useCallback, useRef, type PointerEvent as ReactPointerEvent } from "react";
import { prefersReducedMotion } from "@/lib/prefersReducedMotion";
import {
  AXIS_SLOP_PX,
  createSwipeSampler,
  projectedRest,
  rubberBand,
  SWIPE_SETTLE_HOME,
  type SwipeSampler,
} from "@/lib/swipeGesture";

/** What one gesture has to remember about itself. */
type PagerGesture = {
  /** Where the finger went down, in client coordinates. */
  x: number;
  y: number;
  /** Where the page was when this gesture found it — at rest, or still on its way home. */
  startOffset: number;
  /** Locked once the finger has moved far enough to say what it meant. */
  axis: "x" | "y" | null;
  /** This gesture's readings, and only this gesture's. */
  sampler: SwipeSampler;
};

/**
 * How much further the page can be pulled once the swipe has already passed the
 * threshold, and how far it can be pulled at all in a direction the floor has
 * refused — both as multiples of the threshold, which is the only length this
 * gesture has of its own.
 *
 * Past the threshold the question is answered and there is nothing left to
 * uncover: web keeps ONE page in the DOM, which is what lets `AnimatedHeight`
 * measure a single height for a card whose four pages are four heights. So the
 * travel goes on, at a price, and stops promising a neighbour that is not there.
 * The refused direction never tracks at all for the same reason read twice over:
 * there is no page that way and there never will be, so the grid gives half a
 * threshold and stops — visibly less than the gesture that would have turned it.
 */
const OVERDRAG_GIVE = 1;
const REFUSED_GIVE = 0.5;

/**
 * Where the page actually is at this instant, mid-return or at rest.
 *
 * Asked rather than remembered, because the two answers differ precisely when it
 * matters: a finger that lands during the return home finds the page part-way
 * back, and the offset this hook last *wrote* is where it was let go, not where
 * it is. Starting a fresh gesture from the remembered number would jump the grid
 * back out from under the finger that came to catch it.
 *
 * Where `DOMMatrixReadOnly` is missing there is no transition engine either
 * (jsdom), so nothing can be caught in flight and rest is the true answer.
 */
function pageOffset(track: HTMLElement | null): number {
  if (!track) return 0;
  if (typeof DOMMatrixReadOnly !== "function") return 0;
  const { transform } = getComputedStyle(track);
  if (!transform || transform === "none") return 0;
  try {
    return new DOMMatrixReadOnly(transform).m41;
  } catch {
    // A transform the matrix constructor will not parse is one this hook did not
    // write; treating it as rest is the only reading that cannot make things worse.
    return 0;
  }
}

/**
 * How far the page has travelled for a finger that has pulled it `offset`.
 *
 * One to one while the swipe is still a question — every pixel up to the
 * threshold is the user deciding, and a page that lags there is the app arguing
 * with a finger it should be following. After that the pull is spent against a
 * limit it approaches and never reaches, so the grid keeps answering without
 * ever claiming to be most of the way to a page that is not rendered.
 *
 * @param offset - What the finger has asked for: this gesture's travel, plus
 *   whatever was left of the last one.
 * @param threshold - The distance that turns a page, which is also the scale
 *   everything here is measured in.
 * @param refused - Whether the floor has already declined the direction being
 *   pulled, in which case there is no free travel at all.
 */
function pageFollow(offset: number, threshold: number, refused: boolean): number {
  const distance = Math.abs(offset);
  const tracked = refused ? 0 : Math.min(distance, threshold);
  const give = threshold * (refused ? REFUSED_GIVE : OVERDRAG_GIVE);
  return Math.sign(offset) * (tracked + rubberBand(distance - tracked, give));
}

/**
 * The month/week/day card's horizontal page swipe: a gesture the page follows,
 * and one that cannot be left half-finished.
 *
 * The card used to record an x on `pointerdown` and jump a page on `pointerup`,
 * with nothing whatever moving in between — a slideshow being operated rather
 * than a surface being dragged. Android fixed the same defect on its task rows
 * in Phase 3 and states the rule it was fixed to: a finger and a clock of the
 * app's own are two clocks, and only one of them belongs to the app. The page
 * now writes the finger's translation straight onto the grid, and the app's own
 * clocks run only for what happens after the finger leaves.
 *
 * There are two of those. A swipe that turned the page hands over to the slide
 * the incoming page arrives on — Emphasis, on this same Gesture curve, which is
 * why a release can hand over to it at all — and the grid's own offset is simply
 * dropped, because the element carrying it is replaced by the page that
 * displaced it. A swipe that did not turn the page has nothing to hand over to,
 * so it glides home on [SWIPE_SETTLE_HOME], the same settle the three task
 * rows below the card go home on.
 *
 * The drag is written to a child of the element that slides, and that is
 * structural rather than tidy — the same rule the refusal wrapper above it is
 * built on. A filling CSS animation outranks an inline style, so a page that
 * arrived on `cal-native-slide-from-*` holds its own `transform` at
 * `translateX(0)` for as long as it lives, and a drag written there would be
 * silently ignored on every page but the first. One element, one owner of
 * `transform`.
 *
 * Reduced motion keeps the tracking and loses the return's trip. The preference
 * is about motion the app plays, not about the movement a finger is making — the
 * same reason it does not switch scrolling off — and the instant the finger
 * leaves, everything it moved is put back in the frame that asks for it.
 *
 * The gesture's other half is its exits, which predate the tracking. Every
 * gesture that did not end with a `pointerup` *on the card* used to leak the
 * tracking refs, so they went on saying "a swipe is in progress and it began at
 * x = 300" long after the finger was gone. Two things then went wrong, in this
 * order:
 *
 *  1. the swipe the user actually made was dropped — no `pointerup` reached the
 *     card, so nothing was measured and the calendar did not page; and
 *  2. the *next* `pointerup` the card saw was measured against that dead
 *     origin. That release need not belong to a swipe at all: a drag that began
 *     somewhere else entirely and happened to finish over the calendar arrives
 *     as a bare `pointerup` with no `pointerdown` of its own, and against a
 *     stale origin 300 px away it pages the calendar on its own.
 *
 * `setPointerCapture` fixes (1): it routes the remainder of the gesture back to
 * the card, so a release beyond the card's edge is still *this* card's
 * `pointerup` and the swipe is honoured instead of lost. `pointercancel` — the
 * event the platform sends precisely when it takes the pointer away — fixes (2)
 * by resetting exactly as an ordinary release does, minus the navigation, so an
 * interrupted gesture ends interrupted rather than ending armed.
 *
 * Capture is taken at the axis lock and not on the way down, which is what lets
 * the gesture start on a day cell. In month view a day cell is where nearly
 * every finger lands — seven columns of buttons, 8px of gap between the rows and
 * none whatever between the columns — so a pager that refused to begin on one
 * would be a pager the thumb could hardly ever find. The reason it used to
 * refuse is real but narrower than the refusal was: capture retargets the click
 * that follows a press, so a press that took capture would cost the cell its
 * tap. Taking it at the lock answers both. Before the lock the gesture has
 * claimed nothing and written nothing, so a tap is still a tap; after it, the
 * press is a swipe and the cell's click is one the user no longer means.
 *
 * Every reset goes through one `endGesture`, and the tracked `pointerId` is
 * what makes a release count: an event carrying another pointer's id — a second
 * finger, or the stray release above — can neither end nor commit a gesture it
 * never started.
 *
 * @param threshold - How far a finger travels before the release turns a page.
 * @param onNavigate - Where a decided swipe is reported. The screen owns the
 *   floor rule, so a back swipe is reported there even when it will be refused.
 * @param canGoBack - Whether the page behind this one exists. Used for the
 *   resistance only: what the refusal *is* stays with the screen.
 * @returns The ref for the element the drag is written to, and the card's
 *   pointer handlers.
 */
export function useCalendarPagerSwipe<T extends HTMLElement = HTMLDivElement>(
  threshold: number,
  onNavigate: (offset: -1 | 1) => void,
  canGoBack: boolean,
) {
  const trackRef = useRef<T | null>(null);
  const gestureRef = useRef<PagerGesture | null>(null);
  const pointerIdRef = useRef<number | null>(null);
  // The element capture was taken on, so the release always targets the node
  // that holds it rather than whatever the ending event happens to land on.
  const captureTargetRef = useRef<HTMLElement | null>(null);

  /** Puts the page where the finger has it, with no clock in between the two. */
  const holdAt = useCallback((offset: number) => {
    const track = trackRef.current;
    if (!track) return;
    track.style.transition = "none";
    // Hundredths, because the resistance curve returns a full double and no
    // screen can spend the rest of it: what the extra digits buy is a
    // seventeen-character transform rewritten sixty times a second.
    track.style.transform = `translateX(${Math.round(offset * 100) / 100}px)`;
  }, []);

  /**
   * Returns the page to rest — the only place a page has, unlike the row swipe
   * beside it, which can be resting open.
   *
   * `glide` is false when something else is about to move this element anyway:
   * a turned page replaces it and plays its own arrival, and a trip home
   * underneath that would be a second page turn nobody asked for.
   */
  const restHome = useCallback((glide: boolean) => {
    const track = trackRef.current;
    // A page nothing moved has nothing to put back, and a tap on the grid — the
    // commonest gesture this card gets — should leave no declaration behind it.
    if (!track || track.style.transform === "") return;
    track.style.transition = glide && !prefersReducedMotion() ? SWIPE_SETTLE_HOME : "none";
    track.style.transform = "";
  }, []);

  const endGesture = useCallback(() => {
    const target = captureTargetRef.current;
    const pointerId = pointerIdRef.current;
    gestureRef.current = null;
    pointerIdRef.current = null;
    captureTargetRef.current = null;
    if (!target || pointerId == null) return;
    try {
      // Optional-call because jsdom implements neither capture method, and
      // try/catch because `releasePointerCapture` throws `NotFoundError` when
      // the pointer is already gone — which is exactly the case this handler
      // exists to clean up after. The refs above are cleared first for that
      // reason: they are the state that matters, and they are cleared whether
      // or not the browser still has a capture to give back.
      target.releasePointerCapture?.(pointerId);
    } catch {
      // Already released by the platform; nothing left to do.
    }
  }, []);

  /**
   * Makes this pointer the gesture's own, and the card the element its
   * remainder is delivered to.
   *
   * Deliberately not called on the way down — see the note on capture in the
   * hook's own comment. Capture is an enhancement rather than a precondition:
   * without it the gesture still commits on a release over the card, and
   * `pointercancel` still cleans up after one that ends anywhere else.
   */
  const claimPointer = useCallback((target: HTMLElement, pointerId: number) => {
    captureTargetRef.current = target;
    try {
      target.setPointerCapture?.(pointerId);
    } catch {
      // A pointer the platform has already taken back cannot be captured, and
      // the gesture it belonged to is one of the ones `pointercancel` ends.
    }
  }, []);

  const onPointerDown = useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      // Every press is tracked, day cells included. Which button the press
      // landed on decides nothing here; what it eventually turns out to be —
      // tap or swipe — is decided by where the finger goes, which is the only
      // place a pager can decide it and still be draggable across its own cells.
      const startOffset = pageOffset(trackRef.current);
      // A second finger arriving mid-drag takes the page over rather than
      // fighting the first one for it: the capture in flight is handed back, and
      // a fresh gesture starts from wherever the page currently stands. Starting
      // one at all is the load-bearing half. An arrival that merely ended the
      // gesture underneath it would leave the page parked at the offset that
      // gesture had reached with nothing left listening to put it back — a
      // strand the page cannot come out of, because selecting a date does not
      // re-key the pager and so never replaces the element holding the offset.
      endGesture();
      const sampler = createSwipeSampler();
      sampler.sample(event.clientX, event.timeStamp);
      gestureRef.current = {
        x: event.clientX,
        y: event.clientY,
        startOffset,
        axis: null,
        sampler,
      };
      pointerIdRef.current = event.pointerId;
      // A page that is still on its way home is pinned where this finger found
      // it, which stops that return and makes its own position the starting
      // point of the gesture that interrupted it. A page already at rest is left
      // alone: a tap on the grid is not a drag, and should leave nothing behind.
      if (startOffset !== 0) holdAt(startOffset);
    },
    [endGesture, holdAt],
  );

  const onPointerMove = useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      if (pointerIdRef.current !== event.pointerId) return;
      const gesture = gestureRef.current;
      if (!gesture) return;

      const dx = event.clientX - gesture.x;
      const dy = event.clientY - gesture.y;
      if (gesture.axis === null) {
        if (Math.abs(dx) <= AXIS_SLOP_PX && Math.abs(dy) <= AXIS_SLOP_PX) return;
        gesture.axis = Math.abs(dx) > Math.abs(dy) ? "x" : "y";
        // The horizontal lock is the instant the press stops being a tap, so it
        // is the instant the card claims the pointer: capture routes the rest of
        // the gesture back here when the finger leaves the card, and it
        // retargets the click away from whatever day cell the drag began on. A
        // vertical lock claims neither — that gesture belongs to the scroller
        // below, and the `pointercancel` the platform sends when it takes the
        // scroll over is what ends it.
        if (gesture.axis === "x") claimPointer(event.currentTarget, event.pointerId);
      }
      // A vertical drag belongs to the page's scroller. The gesture is kept open
      // rather than ended, so the release that follows can be recognised as this
      // pointer's and refused a page of its own.
      if (gesture.axis === "y") return;

      gesture.sampler.sample(event.clientX, event.timeStamp);
      const pulled = gesture.startOffset + dx;
      holdAt(pageFollow(pulled, threshold, pulled > 0 && !canGoBack));
    },
    [canGoBack, claimPointer, holdAt, threshold],
  );

  const onPointerUp = useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      // Not our pointer — including the "nothing is being tracked" case, where
      // the ref is null and no real pointer id can match it.
      if (pointerIdRef.current !== event.pointerId) return;
      const gesture = gestureRef.current;
      endGesture();
      if (!gesture) return;

      // Where the finger was going, not where it stopped. A short flick was the
      // gesture this pager refused hardest — it is how most people turn a page
      // that is a whole grid wide — and a long drag already being walked back is
      // the one it used to turn anyway. Both are the same mistake: the last pixel
      // of a gesture is the part that says least about it.
      const velocity = gesture.sampler.release(event.clientX, event.timeStamp);
      const projected = projectedRest(event.clientX - gesture.x, velocity);
      const direction: -1 | 1 = projected < 0 ? 1 : -1;
      const decided = gesture.axis !== "y" && Math.abs(projected) >= threshold;
      // What the screen will do with a decided swipe, worked out here only to
      // know whether this element is about to be replaced. The floor rule itself
      // stays where it is enforced: a refused swipe is still reported, because
      // the answer to one is the screen's to play.
      const turning = decided && (direction === 1 || canGoBack);
      // A turn drops the offset rather than handing it on, and the cut that
      // leaves is a choice rather than an oversight. Web keeps ONE page in the
      // DOM, so the content standing at, say, -80px *is* the outgoing month; the
      // incoming one has to finish at 0, and a continuation from -80px could
      // only reach 0 by travelling RIGHT — which is the exact movement the
      // undecided release below makes, and the one a user reads as "the swipe
      // was refused". Direction is the half of this that carries meaning, so the
      // arrival keeps it: the new page starts from `translateX(5%)` on the far
      // side and moves the way the finger was going, at the price of one frame
      // where the grid jumps back across the offset the drag had built up. There
      // is no third option while one page is in the DOM, and the frame is listed
      // for the device pass to judge in `docs/verification/phase-7-device-pass.md`.
      restHome(!turning);
      if (decided) onNavigate(direction);
    },
    [canGoBack, endGesture, onNavigate, restHome, threshold],
  );

  const onPointerCancel = useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      if (pointerIdRef.current !== event.pointerId) return;
      endGesture();
      // A cancelled gesture is an abandoned one, not a quiet commit: the page
      // goes home the way an undecided release does, and turns nothing.
      restHome(true);
    },
    [endGesture, restHome],
  );

  const onLostPointerCapture = useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      // Capture can also end without a `pointercancel`: removing the capturing
      // node from the document drops it silently, and this card's inner
      // element is keyed by its animation key, so it is replaced on every
      // navigation. After a normal release this is a no-op — `pointerup` has
      // already cleared the id, so the guard below returns.
      if (pointerIdRef.current !== event.pointerId) return;
      endGesture();
      restHome(true);
    },
    [endGesture, restHome],
  );

  return {
    trackRef,
    swipeHandlers: {
      onPointerDown,
      onPointerMove,
      onPointerUp,
      onPointerCancel,
      onLostPointerCapture,
    },
  };
}

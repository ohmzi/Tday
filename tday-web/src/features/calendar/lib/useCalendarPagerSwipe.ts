import { useCallback, useRef, type PointerEvent as ReactPointerEvent } from "react";

/**
 * The month/week/day card's horizontal page swipe, as a gesture that cannot be
 * left half-finished.
 *
 * The card used to track a swipe with two bare refs and nothing but `pointerup`
 * to clear them, so every gesture that did not end with a `pointerup` *on the
 * card* leaked. Drag past the card's edge and lift there, or let the platform
 * claim the pointer for a scroll or an edge back-swipe, and the refs were still
 * saying "a swipe is in progress and it began at x = 300" long after the finger
 * was gone. Two things then went wrong, in this order:
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
 * Every reset goes through one `endGesture`, and the tracked `pointerId` is
 * what makes a release count: an event carrying another pointer's id — a second
 * finger, or the stray release above — can neither end nor commit a gesture it
 * never started.
 */
export function useCalendarPagerSwipe(
  threshold: number,
  onNavigate: (offset: -1 | 1) => void,
) {
  const originXRef = useRef<number | null>(null);
  const pointerIdRef = useRef<number | null>(null);
  // The element capture was taken on, so the release always targets the node
  // that holds it rather than whatever the ending event happens to land on.
  const captureTargetRef = useRef<HTMLElement | null>(null);

  const endGesture = useCallback(() => {
    const target = captureTargetRef.current;
    const pointerId = pointerIdRef.current;
    originXRef.current = null;
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

  const onPointerDown = useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      const target = event.target as HTMLElement;
      // A press on a chevron or a day cell is a tap, not a page swipe. Bail
      // before capturing anything: pointer capture retargets the click that
      // follows, so capturing here would cost the button its own click.
      // `endGesture` rather than a bare `return`, so pressing a button also
      // clears any gesture that was somehow still open.
      if (target.closest("button")) {
        endGesture();
        return;
      }
      originXRef.current = event.clientX;
      pointerIdRef.current = event.pointerId;
      captureTargetRef.current = event.currentTarget;
      try {
        event.currentTarget.setPointerCapture?.(event.pointerId);
      } catch {
        // Capture is an enhancement, not a precondition: without it the
        // gesture still commits on a release over the card, and `pointercancel`
        // still cleans up after one that ends anywhere else.
      }
    },
    [endGesture],
  );

  const onPointerUp = useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      // Not our pointer — including the "nothing is being tracked" case, where
      // the ref is null and no real pointer id can match it.
      if (pointerIdRef.current !== event.pointerId) return;
      const originX = originXRef.current;
      endGesture();
      if (originX == null) return;

      const delta = event.clientX - originX;
      if (Math.abs(delta) < threshold) return;
      onNavigate(delta < 0 ? 1 : -1);
    },
    [endGesture, onNavigate, threshold],
  );

  const onPointerCancel = useCallback(
    (event: ReactPointerEvent<HTMLElement>) => {
      if (pointerIdRef.current !== event.pointerId) return;
      endGesture();
    },
    [endGesture],
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
    },
    [endGesture],
  );

  return { onPointerDown, onPointerUp, onPointerCancel, onLostPointerCapture };
}

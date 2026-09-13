import { useCallback, useRef, useState, type TouchEvent as ReactTouchEvent } from "react";

type SwipeTouch = {
  /** Where the finger went down, in client coordinates. */
  x: number;
  y: number;
  /** Where the row was resting when this gesture began — 0, or -actionsWidth. */
  startX: number;
  /** Locked once the finger has moved far enough to say what it meant. */
  axis: "x" | "y" | null;
};

/**
 * The calendar row's swipe-to-reveal, with the one exit it was missing.
 *
 * `swiping` exists to switch the row's CSS transition off while a finger is on
 * it, so the row tracks that finger instead of easing along a frame behind it.
 * The gesture therefore has to be *ended* by something, and until now the only
 * thing that ended it was `touchend` — a finger that lifts. A touch that is
 * taken away rather than lifted (the platform claiming it for a scroll, an edge
 * back-swipe, an incoming call, the tab being hidden mid-drag) fires
 * `touchcancel` and no `touchend` at all, which left the row parked at whatever
 * offset the finger last reached, with `swiping` still true and its transition
 * still switched off. That is not a slow animation home — it is a row frozen
 * half-open, and it stays frozen until the user happens to touch it again.
 *
 * `cancelSwipe` is that missing exit. It clears `swiping` first, which is the
 * whole fix: the transition has to be back on before `swipeX` moves, or the row
 * teleports to its resting place instead of gliding there. Then it puts the row
 * back where this gesture *found* it rather than settling it to the nearer edge
 * the way a real lift does — a cancelled gesture is an abandoned one, not a
 * quiet commit, and the user never finished choosing.
 *
 * Note that no pointer capture is needed here, unlike the pager swipe: touch
 * events are implicitly captured by the spec — every `touchmove`, `touchend`
 * and `touchcancel` in a gesture is dispatched to the element the `touchstart`
 * hit, whether or not the finger is still over it — so the row never loses the
 * end of its own gesture the way a pointer-driven one can. What it lacked was
 * only the cancel. `pointercancel` is wired to the same reset because the two
 * event families report the same interruption and a device may deliver either:
 * the reset is idempotent, so receiving both costs nothing.
 */
export function useCalendarRowSwipe(
  actionsWidth: number,
  onHorizontalLock: () => void,
) {
  const [swipeX, setSwipeX] = useState(0);
  const [swiping, setSwiping] = useState(false);
  const swipeTouch = useRef<SwipeTouch | null>(null);

  const closeSwipe = useCallback(() => setSwipeX(0), []);

  const onTouchStart = useCallback(
    (event: ReactTouchEvent) => {
      const touch = event.touches[0];
      if (!touch) return;
      swipeTouch.current = { x: touch.clientX, y: touch.clientY, startX: swipeX, axis: null };
      setSwiping(true);
    },
    [swipeX],
  );

  const onTouchMove = useCallback(
    (event: ReactTouchEvent) => {
      const data = swipeTouch.current;
      if (!data) return;
      const touch = event.touches[0];
      if (!touch) return;
      const dx = touch.clientX - data.x;
      const dy = touch.clientY - data.y;
      if (data.axis === null && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) {
        data.axis = Math.abs(dx) > Math.abs(dy) ? "x" : "y";
        if (data.axis === "x") onHorizontalLock();
      }
      if (data.axis === "x") {
        setSwipeX(Math.min(0, Math.max(-actionsWidth, data.startX + dx)));
      }
    },
    [actionsWidth, onHorizontalLock],
  );

  const onTouchEnd = useCallback(() => {
    const data = swipeTouch.current;
    swipeTouch.current = null;
    setSwiping(false);
    if (data?.axis === "x") {
      setSwipeX((prev) => (prev < -actionsWidth / 2 ? -actionsWidth : 0));
    }
  }, [actionsWidth]);

  const cancelSwipe = useCallback(() => {
    const data = swipeTouch.current;
    swipeTouch.current = null;
    // Order matters: `swiping` false restores the row's transition, so the
    // return below is animated rather than a jump cut.
    setSwiping(false);
    if (!data) return;
    setSwipeX(data.startX);
  }, []);

  return {
    swipeX,
    swiping,
    closeSwipe,
    swipeHandlers: {
      onTouchStart,
      onTouchMove,
      onTouchEnd,
      onTouchCancel: cancelSwipe,
      onPointerCancel: cancelSwipe,
    },
  };
}

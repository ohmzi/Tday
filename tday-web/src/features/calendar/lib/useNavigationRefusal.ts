import { useCallback, useEffect, useRef, useState } from "react";
import { DURATION_MS } from "@/lib/motion";
import { prefersReducedMotion } from "@/lib/prefersReducedMotion";

/**
 * Whether the calendar is currently answering a navigation it turned down.
 *
 * The calendar cannot page before the current month, and a refused back
 * navigation used to be silent: the gesture was received, measured against the
 * rule and dropped, which on screen is indistinguishable from a gesture that
 * never arrived at all. The card plays a short resistance instead, and this
 * holds the flag that class is drawn from.
 *
 * A timer rather than the `animationend` the wrapper could listen for. Every
 * one-shot animation in this app is already timed this way — `useFadeUnmount`,
 * `useDuckPresence`, `taskCompletionTiming` — and the number is not a second
 * copy of the stylesheet's: the CSS names `var(--tday-duration-quick)` and this
 * names the same rung from the same generated source, so there is no pair to
 * drift apart. An event listener would also be the only one in the tree, and it
 * would silently never fire in any environment where the animation does not
 * play.
 *
 * Repeat refusals inside one answer restart the timer rather than the
 * animation. That is deliberate: the class never comes off, so the resistance
 * already on screen simply runs to its end, and a user swiping at a wall twice
 * in 150ms is making one complaint rather than two — the same reset the search
 * highlight's timer makes in `CalendarClient`.
 */
export function useNavigationRefusal(): { refusing: boolean; refuse: () => void } {
  const [refusing, setRefusing] = useState(false);
  const timer = useRef<number | null>(null);

  // A refusal outliving the screen would be a `setState` on a component that is
  // gone, and this one is armed by a gesture that can very easily be the last
  // thing a user does before navigating away.
  useEffect(
    () => () => {
      if (timer.current != null) window.clearTimeout(timer.current);
    },
    [],
  );

  const refuse = useCallback(() => {
    // The imperative read, for the reason `useFadeUnmount` gives for its own:
    // the answer is wanted only at the instant the timer arms. And a refusal is
    // the one motion with no finished state to draw instead of playing — it
    // ends where it began, because nothing happened — so under the preference
    // there is nothing to raise the flag for, and raising it anyway would leave
    // a class in the DOM describing an animation that never ran.
    if (prefersReducedMotion()) return;

    if (timer.current != null) window.clearTimeout(timer.current);
    setRefusing(true);
    timer.current = window.setTimeout(() => {
      setRefusing(false);
      timer.current = null;
    }, DURATION_MS.quick);
  }, []);

  return { refusing, refuse };
}

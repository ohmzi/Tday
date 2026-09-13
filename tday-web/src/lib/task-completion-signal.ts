import { useEffect, useReducer } from "react";

/**
 * When the user last ticked something off.
 *
 * An empty list means two different things: one the user just finished, and one
 * that was never filled. Only the first earns confetti, and the completion that
 * caused it is staged in a react-query mutation while the empty state is
 * rendered somewhere else entirely — so the two meet through this, rather than
 * through a prop threaded down every container.
 *
 * Deliberately module state and not a store. The window OPENING needs no
 * subscription: the completion that opens it is the same mutation that empties
 * the cache, so the render that notices the list is empty is already on its
 * way. Only the window CLOSING needs a clock, and it gets one below — a store
 * would have bought the half that was never the problem.
 */
let lastCompletionAt = 0;

/**
 * How long a completion stays recent enough to be the reason a list is empty.
 *
 * Not a motion rung and not a candidate for one: nothing animates for four
 * seconds. It is a wait — the same category `docs/motion.md` puts the 600–620ms
 * band in — and it is wider than the burst's own flight (`Confetti`'s
 * `FLIGHT_MS` plus whatever lead its host hands over) precisely so a re-render
 * mid-flight cannot cut the paper off in mid-air.
 */
export const CELEBRATION_WINDOW_MS = 4000;

/** Called by every complete mutation, as it stages the row out of the cache. */
export function markTaskCompleted() {
  lastCompletionAt = Date.now();
}

/**
 * Whether a completion is recent enough that the list emptying now is the same
 * event.
 *
 * A read, not a subscription: it answers for the instant it is called and tells
 * nobody when that answer expires. Correct for a caller whose screen does not
 * change shape when the window shuts — the two floater feeds, where the scene
 * is drawn whether or not anything was celebrated and the only thing riding
 * this is confetti that finished seconds ago. Anything the window actually
 * KEEPS on screen wants `useTaskJustCompleted` below instead.
 */
export function taskJustCompleted(windowMs = CELEBRATION_WINDOW_MS) {
  return lastCompletionAt !== 0 && Date.now() - lastCompletionAt < windowMs;
}

/**
 * Re-renders the caller at the instant an open window runs out.
 *
 * A window read as `Date.now() - openedAt < windowMs` has no end of its own: it
 * stays open until something unrelated re-renders the component and the
 * comparison happens to come out false. Which means the thing the window was
 * holding on screen leaves on a keystroke, a refetch or a route change —
 * whenever one next happens to arrive — and leaves by disappearing, because the
 * render it disappears on is not about it at all.
 *
 * Exported rather than folded into the hook below because `celebrate` is the
 * OR of two windows of the same length, opened by two different signals (this
 * module's own completion marker and `useCelebrateEmptyTransition`'s
 * remote-emptied ref), and an end that only one of them observes is not an end.
 *
 * Arming nothing for a window that is already shut is deliberate: a `setTimeout`
 * per render, for a deadline in the past, is how a hook like this becomes a
 * timer leak.
 */
export function useCelebrationWindowExpiry(
  openedAt: number,
  windowMs = CELEBRATION_WINDOW_MS,
): void {
  // The dispatch, not its count: nothing reads the number. A re-render is the
  // whole product, because every caller derives the window from `Date.now()`
  // during its own render and needs only to be asked again.
  const [, retest] = useReducer((count: number) => count + 1, 0);

  useEffect(() => {
    if (openedAt === 0) return;
    const remaining = openedAt + windowMs - Date.now();
    if (remaining <= 0) return;
    const timer = setTimeout(retest, remaining);
    return () => clearTimeout(timer);
  }, [openedAt, windowMs]);
}

/**
 * `taskJustCompleted`, plus the clock that closes it. For the callers whose
 * screen is a different shape while the window is open — the scope and list
 * screens, where a celebration keeps the empty scene on a slot that Earlier's
 * own rows otherwise own.
 */
export function useTaskJustCompleted(windowMs = CELEBRATION_WINDOW_MS): boolean {
  useCelebrationWindowExpiry(lastCompletionAt, windowMs);
  return taskJustCompleted(windowMs);
}

let lastLocalDeleteAt = 0;

/**
 * Called by every mutation that can remove a row from the list a screen is
 * showing *without completing it* — every delete, and a bulk move that takes
 * rows out of the list currently open. The non-completion sibling of
 * `markTaskCompleted`.
 *
 * Read by `useCelebrateEmptyTransition` (`@/hooks/use-celebrate-empty-transition`),
 * which otherwise cannot tell "the list just emptied because the last task
 * left it" from "...because it was completed": both shrink the same cached
 * array the same way, and that hook watches the array, not the reason.
 * Deleting (or moving away) the last task gets the plain arrival, never
 * confetti — the same rule `taskJustCompleted` already enforces for a local
 * completion by only ever being set from a *complete* mutation. A *remote*
 * delete/move-away of the last task still slips through as a false
 * celebration: this tab has no marker for a mutation that happened on
 * someone else's. See that hook's own doc comment for the full trade-off.
 */
export function markTaskDeletedLocally() {
  lastLocalDeleteAt = Date.now();
}

/**
 * Whether one of those mutations landed recently enough that an empty
 * transition observed right now is that same mutation, not an unrelated
 * later event. Kept short (well under the confetti window): this only needs
 * to bridge the gap between `setQueryData` staging the rows out and the
 * render that notices the list is now empty, not to keep suppressing
 * celebration long after.
 */
export function wasDeletedLocallyJustNow(withinMs = 1000) {
  return lastLocalDeleteAt !== 0 && Date.now() - lastLocalDeleteAt < withinMs;
}

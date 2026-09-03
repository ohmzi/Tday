/**
 * When the user last ticked something off.
 *
 * An empty list means two different things: one the user just finished, and one
 * that was never filled. Only the first earns confetti, and the completion that
 * caused it is staged in a react-query mutation while the empty state is
 * rendered somewhere else entirely — so the two meet through this, rather than
 * through a prop threaded down every container.
 *
 * Deliberately module state and not a store: nothing renders off it (the empty
 * state reads it on the render the emptied cache already triggered), so a
 * subscription would only add a re-render nobody needs.
 */
let lastCompletionAt = 0;

/** Called by every complete mutation, as it stages the row out of the cache. */
export function markTaskCompleted() {
  lastCompletionAt = Date.now();
}

/**
 * Whether a completion is recent enough that the list emptying now is the same
 * event. The window is wider than the burst's own flight, so a re-render
 * mid-flight cannot cut the paper off in mid-air.
 */
export function taskJustCompleted(windowMs = 4000) {
  return lastCompletionAt !== 0 && Date.now() - lastCompletionAt < windowMs;
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

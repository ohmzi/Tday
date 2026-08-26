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

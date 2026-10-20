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
    return () => {
      clearTimeout(timer);
    };
  }, [openedAt, windowMs]);
}

/**
 * WHEN this module's window was opened, or 0 while it is shut — plus the clock
 * that closes it. For the callers whose screen is a different shape while the
 * window is open — the scope and list screens, where a celebration keeps the
 * empty scene on a slot that Earlier's own rows otherwise own.
 *
 * A stamp rather than the boolean, because `celebrate` is the OR of two windows
 * and the cancel below has to be compared against whichever of them opened LAST
 * (see `shouldCelebrateEmptyState`). Zero while shut rather than the raw
 * `lastCompletionAt`, so a caller cannot accidentally read a long-expired
 * opening as the one still in play: the "is the window open" question is
 * answered here, once, where the clock that answers it already lives.
 */
export function useCompletionOpenedAt(windowMs = CELEBRATION_WINDOW_MS): number {
  useCelebrationWindowExpiry(lastCompletionAt, windowMs);
  return taskJustCompleted(windowMs) ? lastCompletionAt : 0;
}

/**
 * `taskJustCompleted`, plus the clock that closes it — the same window as
 * `useCompletionOpenedAt` above, for the callers that only need to know whether
 * it is open.
 */
export function useTaskJustCompleted(windowMs = CELEBRATION_WINDOW_MS): boolean {
  return useCompletionOpenedAt(windowMs) !== 0;
}

/**
 * When a pending task last ARRIVED on a screen that could be celebrating.
 *
 * The third stamp, and the one neither of the other two could ever be. A
 * celebration is OPENED by a transition — a completion, or a list going empty
 * under a collaborator's hands — and until this existed it was only ever CLOSED
 * by re-reading a static predicate ("is this scope finished right now?") plus
 * the four-second window above. Nothing anywhere observed the opposite
 * transition, so an undo put the row back through the query cache and the paper
 * went on flying over it.
 *
 * Deliberately "a pending task arrived" and not "an undo happened". Three
 * different events write it — an undo of a completion, a task the user creates
 * while the paper is still up, and a task arriving from a collaborator or a
 * sync — and all three say the same thing about the list: it is not finished
 * any more. Naming it after the first of them would make the other two look
 * like abuses of a signal that is in fact doing exactly its job.
 *
 * Module state for `lastCompletionAt`'s reason exactly: the mutation that
 * restores the row and the empty scene that has to stop celebrating are in
 * different trees, and a prop threaded down every container would be a
 * subscription bought for a question the render already answers.
 */
let celebrationCancelledAt = 0;

/**
 * Called wherever a pending row lands on a screen — the completion-undo
 * closures, the create mutations, and `useArrivalCancel`
 * (`@/hooks/use-celebrate-empty-transition`), which is the backstop for every
 * arrival that reaches the cache without anybody remembering to call this.
 *
 * Nothing clears it, on purpose: see `shouldCelebrateEmptyState` below for why
 * a later completion re-opens the window without anything being reset.
 */
export function markCelebrationCancelled() {
  celebrationCancelledAt = Date.now();
}

/** The cancel stamp as it stands, for the gate below. 0 = nothing ever arrived. */
export function celebrationCancelledAtMs(): number {
  return celebrationCancelledAt;
}

/**
 * The whole celebration decision, as arithmetic on three stamps.
 *
 * Pure, and exported for that reason: `celebrate` used to be an OR of two
 * windows read straight out of two hooks, which meant the one thing worth
 * proving about it — that an undo ends it and a LATER completion re-opens it —
 * could only be proven by driving a container. This is the same shape Android's
 * `shouldCelebrateEmptyState` and iOS's have, for the same reason.
 *
 * The two opening stamps collapse into one `max` because they are windows of
 * the same length: the later opening is the later expiry, so "either window is
 * open" and "the newer stamp's window is open" are one question. The hooks hand
 * them in already zeroed once their own clock has shut them
 * (`useCompletionOpenedAt`, `useEmptyTransitionOpenedAt`), so what is left here
 * is purely the ordering rule.
 *
 * `>=` and not `>`. An undo always follows the completion it undoes, and the
 * two can land in the same millisecond — the toast's Undo is a click on a row
 * that was ticked moments ago, but a test, a fake clock or a fast machine will
 * put both on one tick. A same-tick arrival must lose to nothing.
 *
 * Nothing is ever cleared, and that is the design rather than an omission. A
 * completion that lands AFTER a cancel re-opens the window for free by being
 * the newer stamp, so there is no reset to forget, no mutation from an effect,
 * and the ordering is a unit test instead of a claim.
 */
export function shouldCelebrateEmptyState({
  completionOpenedAtMs,
  remoteEmptiedAtMs,
  cancelledAtMs,
}: {
  /** This tab's own completion window, or 0 if shut — `useCompletionOpenedAt`. */
  completionOpenedAtMs: number;
  /** The has-tasks→empty window, or 0 if shut — `useEmptyTransitionOpenedAt`. */
  remoteEmptiedAtMs: number;
  /** When a pending row last arrived — `celebrationCancelledAtMs`. */
  cancelledAtMs: number;
}): boolean {
  const openedAt = Math.max(completionOpenedAtMs, remoteEmptiedAtMs);
  if (openedAt === 0) return false;
  return cancelledAtMs === 0 || cancelledAtMs < openedAt;
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

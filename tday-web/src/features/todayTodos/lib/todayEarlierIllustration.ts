/**
 * Today's own "Earlier" bucket (overdue tasks tucked under an otherwise-empty
 * or non-empty Today, collapsed by default) and its interaction with the
 * "all done for today" illustration.
 *
 * Mirrors the validated Android/iOS mechanism (`shouldShowTodayEarlierIllustration`
 * / `shouldShowTodayEarlierExpandedCelebration` in `TodoListScreen.kt`, the
 * equivalent in `TodoListScreen.swift`) rather than a fresh web-only design.
 */

/**
 * Requirement 3's hand-off duration: the illustration's own exit animation
 * (`.tday-empty-exit` in globals.css, given this exact value inline via
 * `animationDuration`) and the delay before Earlier's rows are actually told
 * to appear (`useEarlierExpandHandoff`) are driven by this ONE constant, not
 * two numbers independently tuned to look close enough — so the two are
 * sequenced by construction.
 *
 * Matches `.tday-empty-enter`'s own 520ms arrival (globals.css): the exit
 * mirrors the scene's entrance rather than inventing an unrelated number.
 */
export const TODAY_EARLIER_EXIT_MS = 520;

/**
 * Requirements 1-3's full interaction for Today's empty-state illustration
 * once an "Earlier" bucket of overdue tasks exists on the screen.
 *
 * Deliberately takes no opinion on anything upstream of `showEmpty` (pending
 * count, loading state, search) — that gate is unchanged by this feature (see
 * the `hasScopedTasks`/`showEmpty` note in `AllTasksTimelineContainer.tsx`):
 * `earlierItems` is a wholly separate array from whatever `showEmpty` reads,
 * so this only ever decides who owns the visual slot once `showEmpty` is
 * already true — it can never itself be the reason `showEmpty` was true or
 * false. That separation is the whole point: folding Earlier into the same
 * array `showEmpty` reads would silently break requirement 1 by making "zero
 * pending" also require "zero overdue".
 */
export function shouldShowTodayEmptyIllustration({
  showEmpty,
  hasEarlierItems,
  earlierExpanded,
  earlierHandoffPending,
  celebrate,
}: {
  /** Zero pending-today tasks, not loading, not mid-search. Unchanged by this feature. */
  showEmpty: boolean;
  /** Today's own "Earlier" bucket actually holds overdue tasks. */
  hasEarlierItems: boolean;
  /** Earlier is expanded (its rows are visible) rather than collapsed. */
  earlierExpanded: boolean;
  /** Requirement 3's two-phase hand-off is mid-exit (see `useEarlierExpandHandoff`). */
  earlierHandoffPending: boolean;
  /** A completion (this tab's or a remote one) just emptied Today. */
  celebrate: boolean;
}): boolean {
  if (!showEmpty) return false;

  // No Earlier bucket at all: unchanged, pre-existing behaviour — the plain
  // empty scene, exactly as it worked before this feature existed.
  if (!hasEarlierItems) return true;

  // Requirement 3: still exiting. Stays on screen (playing `.tday-empty-exit`)
  // until the hand-off's own timer actually flips `earlierExpanded`.
  if (earlierHandoffPending) return true;

  // Requirement 2: collapsed — the illustration owns the slot, Earlier's
  // header sits reachable right underneath it.
  if (!earlierExpanded) return true;

  // Requirement 3: expanded — Earlier's own rows own the slot instead of the
  // illustration, UNLESS requirement 1's own window is open (`celebrate`): a
  // completion that just emptied Today still needs its confetti moment even
  // though the user already happened to have Earlier open. Scoped to
  // `celebrate`'s own window, so this hands the slot back to Earlier's rows
  // the instant that window closes, same as if no completion had just
  // happened here.
  return celebrate;
}

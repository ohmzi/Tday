/**
 * The "Earlier" bucket (overdue tasks tucked under an otherwise-empty or
 * non-empty screen, collapsed by default) and its interaction with the "all
 * done" illustration — originally built for Today, since generalized to
 * every other scope whose own Earlier bucket can hold tasks while its
 * "current" set is empty: All, Priority, custom Lists (Scheduled's own
 * Earlier bucket never has tasks to begin with — its `buildTimelineSections`
 * call passes `futureOnly: true` — so this is a no-op there; the standalone
 * Overdue screen has no nested Earlier concept). `AllTasksTimelineContainer`
 * (`useTimelineEmptyState`) and `ListContainer` are the two call sites.
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
 * sequenced by construction. Shared by every scope below, not retuned per
 * screen — see this module's own doc comment for why that is deliberate.
 *
 * Matches `.tday-empty-enter`'s own 520ms arrival (globals.css): the exit
 * mirrors the scene's entrance rather than inventing an unrelated number.
 */
export const TODAY_EARLIER_EXIT_MS = 520;

/**
 * The Overdue/Earlier ROWS' own fade duration — deliberately a separate,
 * shorter number from `TODAY_EARLIER_EXIT_MS` above. That constant times the
 * illustration's own exit AND the hand-off delay before these rows are
 * revealed AT ALL (requirement 3's ordering guarantee); this one only times
 * how long the rows themselves take to fade once whether-and-when has already
 * been decided elsewhere — a purely presentational polish on top, read by
 * `useFadeUnmount` (`src/hooks/useFadeUnmount.ts`, shared by
 * `TodayEarlierSection` and `TimelineSectionDroppable`) and by the CSS
 * `.tday-rows-enter`/`.tday-rows-exit` pair (globals.css) it hands the
 * duration to via `animationDuration`, so the fade and the moment the DOM
 * node actually goes away line up exactly, the same one-number-read-twice
 * approach as `TODAY_EARLIER_EXIT_MS` itself.
 */
export const OVERDUE_ROWS_FADE_MS = 260;

/**
 * Requirements 1-3's full interaction for a scope's empty-state illustration
 * once its own "Earlier" bucket of overdue tasks exists on the screen.
 *
 * Deliberately takes no opinion on anything upstream of `showEmpty` (pending
 * count, loading state, search) — that gate is a caller concern (see
 * `useTimelineEmptyState`'s `hasNonEarlierScopedTasks`/`showEmpty`, and
 * `ListContainer`'s own equivalent): `hasEarlierItems` is read from a wholly
 * separate reduction than whatever `showEmpty` reads, so this only ever
 * decides who owns the visual slot once `showEmpty` is already true — it can
 * never itself be the reason `showEmpty` was true or false. That separation
 * is the whole point: folding Earlier into the same count `showEmpty` reads
 * would silently break requirement 1 by making "zero current tasks" also
 * require "zero overdue".
 */
export function shouldShowTodayEmptyIllustration({
  showEmpty,
  hasEarlierItems,
  earlierExpanded,
  earlierHandoffPending,
  celebrate,
}: {
  /** Zero current (non-Earlier) tasks for this scope, not loading, not mid-search. */
  showEmpty: boolean;
  /** This scope's own "Earlier" bucket actually holds overdue tasks. */
  hasEarlierItems: boolean;
  /** Earlier is expanded (its rows are visible) rather than collapsed. */
  earlierExpanded: boolean;
  /** Requirement 3's two-phase hand-off is mid-exit (see `useEarlierExpandHandoff`). */
  earlierHandoffPending: boolean;
  /** A completion (this tab's or a remote one) just emptied this scope's current tasks. */
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
  // completion that just emptied this scope's current tasks still needs its
  // confetti moment even though the user already happened to have Earlier
  // open. Scoped to `celebrate`'s own window, so this hands the slot back to
  // Earlier's rows the instant that window closes, same as if no completion
  // had just happened here.
  return celebrate;
}

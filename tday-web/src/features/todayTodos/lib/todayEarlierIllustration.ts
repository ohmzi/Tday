import { DURATION_MS } from "@/lib/motion";
import type { EarlierHandoff } from "./useEarlierExpandHandoff";

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
 * Requirement 3's hand-off duration: the illustration's own exit — the scene
 * sinking and the slot under it closing, one motion on one rung
 * (`.tday-empty-slot` / `.tday-empty-exit` in globals.css) — and the delay
 * before Earlier's rows are actually told to appear
 * (`useEarlierExpandHandoff`) are driven by this ONE rung, not by numbers
 * independently tuned to look close enough, so they are sequenced by
 * construction. Shared by every scope below, not retuned per screen — see this
 * module's own doc comment for why that is deliberate.
 *
 * The stylesheet names the rung rather than taking this number: a duration
 * bound for CSS wants `var(--tday-duration-*)` and only a timer wants the
 * integer (`src/lib/motion.ts` says so, and `taskCompletionTiming.ts` splits
 * the same value the same way). Both spellings resolve to the one generated
 * token, so the timer and the exit it is waiting for cannot drift apart.
 *
 * `Enter`, and it used to be `Scene` — 520, on the reasoning that an exit
 * should mirror the entrance it undoes. That is the one thing this exit does
 * not do. It is not the scene being taken back; it is the slot being cleared
 * for Earlier's rows, and it spent every one of those 520ms holding content the
 * user had just asked for behind an illustration they had just dismissed.
 *
 * Two rules pick the rung and they meet on this one. Geometry says `Emphasis`,
 * because the slot is a size and the exit now closes it (`docs/motion.md`'s
 * second idiom rule). The first idiom rule says an exit is never longer than
 * the enter it undoes, and a hand-off answers to the enter it hands over to as
 * well — `OVERDUE_ROWS_FADE_MS` below, the 260 Earlier's own rows fade in on —
 * for the reason that rule gives: a departure that outlasts the arrival it is
 * making room for reads as the app hesitating. That rules out 320, and it rules
 * out 260 too, since matching the arrival exactly would make the two read as a
 * swap between equals rather than as one thing leaving before another lands.
 * The rung below is `Enter`. iOS reaches the same asymmetry from the other end
 * and off the ladder — `EarlierIllustrationHandoff.exitDuration` 0.22 against
 * `enterDuration` 0.30 (`TodoListScreen.swift`), kept off its rungs because it
 * times a sequence leg there; web's legs land on rungs, so this one takes the
 * rung under the arrival it leads.
 */
export const TODAY_EARLIER_EXIT_MS = DURATION_MS.enter;

/**
 * The Overdue/Earlier ROWS' own fade duration — deliberately a separate,
 * longer number from `TODAY_EARLIER_EXIT_MS` above, which is the scene's exit
 * and not this one.
 *
 * It is read in three places that have to agree exactly, all of them one
 * departure seen from a different side: `useFadeUnmount`
 * (`src/hooks/useFadeUnmount.ts`, shared by `TodayEarlierSection` and
 * `TimelineSectionDroppable`) keeps the collapsing body in the DOM for it, the
 * CSS `.tday-rows-enter`/`.tday-rows-exit` pair (globals.css) is handed it via
 * `animationDuration` so the fade ends on the frame the node goes away, and
 * `useEarlierExpandHandoff` holds the scene off the slot for it on a collapse
 * so the scene arrives as those rows leave rather than on top of them. Same
 * one-number-read-N-times approach as `TODAY_EARLIER_EXIT_MS` itself: a
 * sequence tuned by three numbers that look close is a sequence that drifts.
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
  earlierHandoff,
  celebrate,
}: {
  /** Zero current (non-Earlier) tasks for this scope, not loading, not mid-search. */
  showEmpty: boolean;
  /** This scope's own "Earlier" bucket actually holds overdue tasks. */
  hasEarlierItems: boolean;
  /** Earlier is expanded (its rows are visible) rather than collapsed. */
  earlierExpanded: boolean;
  /** Which half of the swap is mid-exit, if either (see `useEarlierExpandHandoff`). */
  earlierHandoff: EarlierHandoff;
  /** A completion (this tab's or a remote one) just emptied this scope's current tasks. */
  celebrate: boolean;
}): boolean {
  if (!showEmpty) return false;

  // No Earlier bucket at all: unchanged, pre-existing behaviour — the plain
  // empty scene, exactly as it worked before this feature existed.
  if (!hasEarlierItems) return true;

  // A collapse hands the slot the other way round, so it is the one state
  // `earlierExpanded` cannot be read for: that flag has already gone false —
  // it is what arms the rows' own fade — while the rows are still on screen
  // playing it. The scene waits them out rather than landing on a body that is
  // still leaving, which is the pile-up this hand-off exists to undo.
  if (earlierHandoff === "rows-leaving") return false;

  // Requirement 2: collapsed — the illustration owns the slot, Earlier's
  // header sits reachable right underneath it. Requirement 3's expand beat is
  // the same answer for the same reason and needs no branch of its own: the
  // hand-off holds `earlierExpanded` false until the scene's exit has played,
  // so the scene is still the occupant and still draws itself, mid-exit.
  if (!earlierExpanded) return true;

  // Requirement 3: expanded — Earlier's own rows own the slot instead of the
  // illustration, UNLESS requirement 1's own window is open (`celebrate`): a
  // completion that just emptied this scope's current tasks still needs its
  // confetti moment even though the user already happened to have Earlier
  // open. Scoped to `celebrate`'s own window, so this hands the slot back to
  // Earlier's rows the instant that window closes, same as if no completion
  // had just happened here.
  //
  // `earlierHandoffVacatesSlot` below is this same branch, asked one beat
  // early: anything that joins this condition has to join that one too, and
  // the test pairing them is what says so.
  return celebrate;
}

/**
 * Whether the hand-off in flight is handing the SLOT over, as opposed to only
 * fading the ink sitting on it.
 *
 * They are usually the same thing and once were: the illustration exits, the
 * 42vh it held closes under the fade, Earlier's rows land in the space
 * (`.tday-empty-slot` / `.tday-empty-exit` in globals.css). Not on the
 * celebrating path. A tap that lands inside requirement 1's window hands
 * nothing over — `shouldShowTodayEmptyIllustration`'s last line keeps the scene
 * on screen for the rest of that window, above Earlier's rows rather than
 * instead of them — so the track it holds must not close, or the beat ends by
 * taking 42vh out from under those rows and then giving it straight back.
 *
 * The ink is left alone here on purpose: the fade-then-snap that path plays is
 * a known defect with a row of its own
 * (`web-illustration-pops-back-inside-celebrate-window`), and it is a defect in
 * paint. This is only about whether the page under it moves.
 */
export function earlierHandoffVacatesSlot({
  earlierHandoff,
  celebrate,
}: {
  /** Which half of the swap is mid-exit, if either (see `useEarlierExpandHandoff`). */
  earlierHandoff: EarlierHandoff;
  /** A completion (this tab's or a remote one) just emptied this scope's current tasks. */
  celebrate: boolean;
}): boolean {
  return earlierHandoff === "scene-leaving" && !celebrate;
}

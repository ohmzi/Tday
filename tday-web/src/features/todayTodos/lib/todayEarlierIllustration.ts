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
  // `earlierSlotChangesHands` below is this same line read one beat early:
  // because this keeps the scene, a tap that lands inside the window swaps
  // nothing and is not sequenced at all. Anything that joins this condition
  // has to join that one too, and the test pairing them is what says so.
  return celebrate;
}

/**
 * Whether a tap on Earlier's header actually swaps who occupies the slot the
 * scene and Earlier's rows share — the one thing `useEarlierExpandHandoff`
 * cannot work out for itself, since it knows which way its own toggle is going
 * and nothing about what is on the other side of it.
 *
 * One expression covers both directions because it describes the swap rather
 * than a direction: the scene occupies this slot exactly while Earlier is
 * closed, so a tap trades the two whenever the scope is empty and Earlier has
 * rows to trade with. A screen with current tasks on it has no scene in the
 * swap at all and stays the plain immediate toggle it always was.
 *
 * `!celebrate` is the whole of the fade-then-snap defect. Inside requirement
 * 1's window the scene is not going anywhere —
 * `shouldShowTodayEmptyIllustration`'s last line keeps it on the slot until the
 * window closes — so a tap there swaps nothing, and sequencing it anyway made
 * the scene play a departure it was not making: `.tday-empty-exit` sank it, and
 * the confetti still crossing it, to nothing; then the class came off with the
 * beat and snapped both back to full opacity, where they stayed for the rest of
 * the window. There is no exit to shorten here and no faded state to hold — the
 * fix is that the departure was never happening. It is also the same rule
 * `TODAY_EARLIER_EXIT_MS` argues above, read from the other side: a beat held
 * open in front of a screen where nothing is moving is a dead wait, and
 * removing the trip has to remove the wait with it.
 */
export function earlierSlotChangesHands({
  showEmpty,
  hasEarlierItems,
  celebrate,
}: {
  /** Zero current (non-Earlier) tasks for this scope, not loading, not mid-search. */
  showEmpty: boolean;
  /** This scope's own "Earlier" bucket actually holds overdue tasks. */
  hasEarlierItems: boolean;
  /** A completion (this tab's or a remote one) just emptied this scope's current tasks. */
  celebrate: boolean;
}): boolean {
  return showEmpty && hasEarlierItems && !celebrate;
}

/**
 * Whether the scene is LEAVING right now — the ink sinking and the track under
 * it closing, one departure asked once.
 *
 * The two were a class each and a question each for exactly as long as they
 * could disagree: a hand-off inside the celebration window closed no track,
 * because the scene was still there when the beat ended, but faded its ink
 * anyway. That asymmetry is gone — such a hand-off no longer starts, see
 * `earlierSlotChangesHands` — and with it the reason for two questions. Two
 * class names survive because the stylesheet needs them (the ink is an
 * animation, the track is a transition on a grid), but they go on together or
 * not at all: a scene whose ink leaves without its box, or the other way
 * round, reads as two things happening to it rather than as it leaving.
 */
export function emptySceneIsLeaving({
  earlierHandoff,
}: {
  /** Which half of the swap is mid-exit, if either (see `useEarlierExpandHandoff`). */
  earlierHandoff: EarlierHandoff;
}): boolean {
  return earlierHandoff === "scene-leaving";
}

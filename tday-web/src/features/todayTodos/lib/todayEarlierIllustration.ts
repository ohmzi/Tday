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
 * It times that departure by whichever route it is reached: a tap that swaps
 * the slot, and the celebration window running out under a scene that was only
 * on the slot for its length (`useCelebrationSceneExit`). One departure, one
 * length — a window expiry that faded the scene out over some other number
 * would read as a different thing happening to it.
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
  sceneHeldForExit = false,
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
  /**
   * The window has just shut and the scene's departure is decided but not yet
   * started — one render long, see `useCelebrationSceneExit`. Optional because
   * it only ever matters on the last line below; a caller with no celebration
   * window in play has nothing to hold.
   */
  sceneHeldForExit?: boolean;
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

  // The mirror, and the branch that says who is drawing rather than who will
  // own the slot: the scene is on its way off it and is still the thing on it.
  // Redundant for an expand, which holds `earlierExpanded` false for the whole
  // beat — and not redundant at all for the other way this beat is reached. A
  // celebration window running out plays it with Earlier already expanded, and
  // without this line the two branches below would hand the slot over a frame
  // before the scene had finished leaving it, which is the untimed cut this
  // beat exists to replace.
  if (earlierHandoff === "scene-leaving") return true;

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
  //
  // `earlierSlotChangesHands` below is this same line read one beat early:
  // because this keeps the scene, a tap that lands inside the window swaps
  // nothing and is not sequenced at all. Anything that joins this condition
  // has to join that one too, and the test pairing them is what says so.
  //
  // `sceneHeldForExit` is the seam between this line and the `"scene-leaving"`
  // one above, and it exists because the two are one render apart. The window
  // closing is what makes `celebrate` false, so on that render the beat it
  // causes has not been armed yet and this line would hand the slot over a
  // render early — taking the scene's own node out of the tree, which costs the
  // departure its track transition and hands the replacement node the Scene
  // arrival to replay. Held here for exactly the one render it takes the beat
  // to arm. It is not a third reason to show the scene; it is the same reason,
  // one render before the hand-off can say so.
  return celebrate || sceneHeldForExit;
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

/**
 * Whether the scene is leaving because the scope REFILLED under it — the other
 * way off this slot, and the one `emptySceneIsLeaving` above is not.
 *
 * Two departures, and telling them apart is the whole of this function. The
 * hand-off above plays while the scope is still EMPTY and gives the slot to
 * Earlier's rows; this one plays because there is no longer an empty scope to
 * draw. They want different rungs (that one answers to the arrival it leads,
 * this one to the paper fading inside it), they can never both be true, and a
 * linger written for one of them and applied to both is a second departure
 * stacked on a departure.
 *
 * `sceneStillMounted` comes from `useFadeUnmount`, which is what actually keeps
 * the node in the tree past the frame the container stopped asking for it — and
 * which already returns false outright under reduced motion, so this is false
 * there too and the scene goes on the cancel frame with no wait in front of the
 * restored row. The other two are read live, not remembered: the moment the
 * scope is empty again (a re-complete landing inside the same beat) this is no
 * longer a departure and the class has to come off the node it is on.
 */
export function emptySceneLeavesOnCancel({
  sceneStillMounted,
  showEmptyIllustration,
  showEmpty,
}: {
  /** `useFadeUnmount(showEmptyIllustration, DURATION_MS.quick)`. */
  sceneStillMounted: boolean;
  /** Who owns the slot right now — `shouldShowTodayEmptyIllustration`. */
  showEmptyIllustration: boolean;
  /** Zero current (non-Earlier) tasks for this scope, not loading, not mid-search. */
  showEmpty: boolean;
}): boolean {
  return sceneStillMounted && !showEmptyIllustration && !showEmpty;
}

/**
 * Whether Earlier is ON ITS WAY OPEN — the beat its own `expanded` flag is
 * deliberately false for.
 *
 * The header needs this and nothing else does. An expand holds `expanded`
 * false for the length of the scene's exit (`useEarlierExpandHandoff`), which
 * is right for everything that draws the slot and wrong for the chevron the
 * finger just landed on: read `expanded` alone and the header is identical for
 * the whole wait, so the tap reads as ignored and gets made again. A collapse
 * needs no equivalent — `expanded` goes false on the tap there, and the
 * chevron already turns with it.
 *
 * Derived from `emptySceneIsLeaving` rather than re-comparing the state,
 * because it is not a second beat: the scene only ever leaves this slot to
 * hand it to Earlier's rows, so "the scene is leaving" and "Earlier is opening"
 * are one moment seen from either end, and two independent comparisons of the
 * same value are two chances to disagree. Which is also why this covers the
 * departure a tap did not start — a celebration window running out under an
 * expanded bucket ends with Earlier's rows on the slot exactly as a tap does,
 * and the chevron should have turned by then either way.
 */
export function earlierIsExpanding(args: {
  /** Which half of the swap is mid-exit, if either (see `useEarlierExpandHandoff`). */
  earlierHandoff: EarlierHandoff;
}): boolean {
  return emptySceneIsLeaving(args);
}

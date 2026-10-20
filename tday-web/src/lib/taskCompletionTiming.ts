import { DURATION_MS } from "@/lib/motion";

/**
 * Timings for the staged "checking off" animation — the one clock every row in the app leaves
 * its list on, in either direction.
 *
 * The first three legs mirror the native constants one-for-one so web, Android and iOS complete a
 * task with the same rhythm — see `TASK_COMPLETION_*_MS` in `TodoListScreen.kt` and
 * `CALENDAR_TASK_COMPLETION_*_MS` in `CalendarScreen.kt`.
 *
 * "Every row" is the part that had to be earned. Five row types play this sequence — the
 * scheduled row, the Anytime row, the calendar row and the two Completed rows, which play it
 * backwards to un-tick a task — and until this module reached all five, three of them ran
 * 280 / 620 / 960: the same four beats a third slower, so the same task ticked off on two
 * screens finished at two different speeds. The offsets below are gaps rather than motions,
 * which is why they are plain integers and not rungs: nobody watches the wait between the tick
 * and the strike, they watch the tick and they watch the strike, and each of those is on a rung
 * of its own where it is drawn.
 *
 * The sequence is deliberately independent of the undo toast, which lives for 5s on its own
 * schedule: the row finishes its animation and leaves the list in well under a second, and Undo
 * puts it back afterwards.
 */

/** Green tick lands, then the strike begins. */
export const TASK_COMPLETION_CHECK_TO_STRIKE_MS = 160;

/** The strike holds — title and notes both — before the row starts fading. */
export const TASK_COMPLETION_STRIKE_TO_FADE_MS = 360;

/**
 * The ink leaving: the row's content fades where it stands. Nothing is moving yet, which is what
 * puts it on the Change rung. Read from the vocabulary rather than re-typed — it was already
 * exactly 260 by hand, and `docs/motion.md` cites this line as one of the rung's anchors.
 */
export const TASK_COMPLETION_FADE_MS = DURATION_MS.change;

/**
 * The box closing: the row's height runs to zero underneath that fade, so the rows below travel
 * into the space while the finished task is still on screen instead of jumping into a gap the
 * instant it is pruned.
 *
 * Emphasis and not Change because the boundary is geometry rather than importance
 * (`docs/motion.md`'s second idiom rule): a height is a size, so the longer rung. Outlasting the
 * fade is the point and not an accident — the ink goes first and the box shuts behind it.
 */
export const TASK_COMPLETION_COLLAPSE_MS = DURATION_MS.emphasis;

/**
 * When the row is pruned from the caches.
 *
 * The last leg is the collapse and not the fade, which is where web's clock parts company with
 * the native one. Android and iOS hand a removal to the list itself and it closes the gap on its
 * own schedule; web has no list-removal animation, so the row's own collapse *is* the removal,
 * and pruning while it is still running would put back the jump this whole sequence exists to
 * take out. That costs web the difference between the two rungs — still comfortably inside the
 * undo toast's 5s, which is the only other clock this one has to stay clear of.
 *
 * Which also makes this total conditional, and both rows cut it short under reduced motion: with
 * no collapse to outlast there is nothing for the last leg to be waiting for, and a wait left
 * standing in front of a destination already drawn is the fifth idiom rule broken from the other
 * side. The cut is made where the timers arm, not here — this is the length of the animation, and
 * the animation is what is missing.
 */
export const TASK_COMPLETION_TOTAL_MS =
  TASK_COMPLETION_CHECK_TO_STRIKE_MS +
  TASK_COMPLETION_STRIKE_TO_FADE_MS +
  TASK_COMPLETION_COLLAPSE_MS;

/**
 * The removal, as a `transition` shorthand for the row containers to hand the DOM.
 *
 * Spelled with the custom properties rather than the millisecond exports above because a duration
 * that ends up inside a CSS string wants `var(--tday-duration-*)` — `src/lib/motion.ts` says so,
 * and the two spellings resolve to the same generated token, so the timer that prunes the row and
 * the transition that closes it cannot drift apart. Standard is the unmarked curve and both legs
 * take it: one curve is what makes a fade and a collapse read as a single departure rather than
 * as two things happening to the same row.
 *
 * Lives here rather than in either row component because both of them play it and a transition
 * copied into two files is a timing with two owners.
 */
export const TASK_COMPLETION_REMOVING_TRANSITION =
  "opacity var(--tday-duration-change) var(--tday-ease-standard), " +
  "grid-template-rows var(--tday-duration-emphasis) var(--tday-ease-standard)";

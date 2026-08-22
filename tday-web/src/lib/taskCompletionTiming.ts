/**
 * Timings for the staged "checking off" animation on task and floater rows.
 *
 * These mirror the native constants one-for-one so web, Android and iOS complete a task with the
 * same rhythm — see `TASK_COMPLETION_*_MS` in `TodoListScreen.kt` and
 * `CALENDAR_TASK_COMPLETION_*_MS` in `CalendarScreen.kt`.
 *
 * The sequence is deliberately independent of the undo toast, which lives for 5s on its own
 * schedule: the row finishes its animation and leaves the list in well under a second, and Undo
 * puts it back afterwards.
 */

/** Green tick lands, then the strike begins. */
export const TASK_COMPLETION_CHECK_TO_STRIKE_MS = 160;

/** Title sweep + notes line-through hold before the row starts fading. */
export const TASK_COMPLETION_STRIKE_TO_FADE_MS = 360;

/** Fade-out, after which the row leaves the list. */
export const TASK_COMPLETION_FADE_MS = 260;

/** When the row is pruned from the caches and the rows below close up. */
export const TASK_COMPLETION_TOTAL_MS =
  TASK_COMPLETION_CHECK_TO_STRIKE_MS +
  TASK_COMPLETION_STRIKE_TO_FADE_MS +
  TASK_COMPLETION_FADE_MS;

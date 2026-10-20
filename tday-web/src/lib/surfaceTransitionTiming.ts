import { TASK_COMPLETION_FADE_MS } from "./taskCompletionTiming";

/**
 * How long a surface that a plain conditional puts on the screen — the mobile
 * search-results panel (`MobileSearchHeader`), the selection action bar
 * (`BulkSelectionBar`) — is given to arrive, and to leave.
 *
 * Deliberately NOT a new number. Web's motion vocabulary is small on purpose
 * and is being made smaller, so a surface that needs "long enough to read as a
 * move, short enough not to sit between the user and what they asked for"
 * reads the beat the completion clock already uses for exactly that:
 * `TASK_COMPLETION_FADE_MS`, the 260 ms a ticked row takes to leave the list.
 * Adding a fourth duration nobody else shares is how a handful of timings
 * becomes a dozen that all look almost the same.
 *
 * Read twice, the way `MODAL_EXIT_MS` and `OVERDUE_ROWS_FADE_MS` already are:
 * once by `useFadeUnmount`, which is what actually keeps the node in the DOM
 * long enough for the exit to have frames to play in, and once by the CSS
 * (`.tday-surface-enter` / `.tday-surface-exit` in globals.css), which is
 * handed this value inline via `animationDuration`. Two numbers tuned to look
 * alike is how an exit ends up half-played; one number read twice cannot drift.
 */
export const SURFACE_TRANSITION_MS = TASK_COMPLETION_FADE_MS;

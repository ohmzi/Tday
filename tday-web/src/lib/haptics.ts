import { isHapticsEnabled } from "./feedbackPreferences";

/**
 * Thin haptic feedback helpers. Uses `navigator.vibrate()` on Android Chrome;
 * no-ops silently everywhere else (iOS, desktop, unsupported browsers).
 */

/**
 * Whether this browser can vibrate at all — the question Settings asks before
 * drawing a switch for it, since a switch over a vibrator that does not exist is
 * a control that does nothing on every desktop and every iPhone.
 *
 * Resolved per call rather than captured at module scope, for the reason
 * `prefersReducedMotion.ts` gives about `matchMedia`: a module-scope capture binds
 * to whatever `navigator` was at import time, which in jsdom is before a test has
 * installed its own stub. In a browser the value cannot change anyway, so the
 * per-call form costs nothing and is honest in both.
 */
export function hapticsSupported(): boolean {
  return typeof navigator !== "undefined" && typeof navigator.vibrate === "function";
}

function vibrate(pattern: number | number[]): void {
  if (!hapticsSupported()) return;
  // The user's preference is read here, at the one chokepoint the eight verbs
  // below already share, rather than at the seventy-odd call sites that spell
  // them. A gate the caller has to remember is a gate that is eventually
  // forgotten, and the next haptic added to this file gets it for free.
  if (!isHapticsEnabled()) return;
  navigator.vibrate(pattern);
}

/** Ultra-short tick — tab switch, minor UI state changes. */
export function hapticTick(): void {
  vibrate(10);
}

/** Medium pulse — task completed, successful action. */
export function hapticSuccess(): void {
  vibrate([30, 40, 60]);
}

/** Short tap — drag pickup. */
export function hapticDragStart(): void {
  vibrate(50);
}

/** Micro-tap — drag over a new target. */
export function hapticDragOver(): void {
  vibrate(15);
}

/** Satisfying drop — drag ended on a valid target. */
export function hapticDrop(): void {
  vibrate([20, 30, 50]);
}

/** Light tap — button press, search, add/create actions. */
export function hapticButtonTap(): void {
  vibrate(15);
}

/** Confirm action — sheet confirm, form submit, accept edit. */
export function hapticConfirm(): void {
  vibrate([20, 30, 40]);
}

/** Dismiss — sheet close, cancel, clear. */
export function hapticDismiss(): void {
  vibrate(8);
}

/**
 * A hidden surface came out — a task row slid aside far enough to uncover the
 * actions behind it.
 *
 * The event both natives already spell: Android's `TdayHaptics.reveal`
 * (CONTEXT_CLICK) and iOS's `HapticManager.reveal` (`.rigid` at 0.7), each
 * documented in those files as exactly this sentence. Web has neither a style nor
 * an intensity to say it with, only a length, so "sharper" has to become
 * "distinct", and the length is chosen against the ones already in this file
 * rather than against a device.
 *
 * 25 ms sits in the gap between the two bands the file already has. Above the
 * 8–15 acknowledgement band — [hapticDismiss] at 8, [hapticTick] at 10,
 * [hapticButtonTap] and [hapticDragOver] at 15 — because those are the cost of
 * using the app answering a touch that was already deliberate, and this is an
 * event: something appeared that was not there a moment ago. Below
 * [hapticDragStart]'s 50, because picking a row up and carrying it is a heavier
 * commitment than uncovering what is behind it. 25 is the round value in that gap
 * and is confusable with neither neighbour.
 *
 * A single pulse and not a pattern, which is the other half of the vocabulary:
 * the patterns here ([hapticSuccess], [hapticDrop], [hapticConfirm]) are compound
 * outcomes, a thing done and its result. A reveal is one event.
 */
export function hapticReveal(): void {
  vibrate(25);
}

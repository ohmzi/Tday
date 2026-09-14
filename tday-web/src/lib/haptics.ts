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

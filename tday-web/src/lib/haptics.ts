/**
 * Thin haptic feedback helpers. Uses `navigator.vibrate()` on Android Chrome;
 * no-ops silently everywhere else (iOS, desktop, unsupported browsers).
 */

const canVibrate =
  typeof navigator !== "undefined" && typeof navigator.vibrate === "function";

function vibrate(pattern: number | number[]): void {
  if (canVibrate) navigator.vibrate(pattern);
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

import { useSyncExternalStore } from "react";

/**
 * The OS/browser "reduce motion" preference, in the two shapes web code needs it.
 *
 * It serves the vocabulary's fifth idiom rule (`docs/motion.md`): reduced motion
 * removes the trip, never the destination. `globals.css` is the CSS half of that
 * rule — every animation the app declares has a `@media (prefers-reduced-motion:
 * reduce)` counterpart pinning the finished frame — and this module is the JS
 * half, for the part CSS cannot see. Switching an animation off says nothing to
 * the `setTimeout` that was waiting for it to finish: the trip disappears and the
 * wait in front of the destination stays, which is the worst of both.
 *
 * Two exports, because the two callers are asking different questions. A timer
 * that is about to be armed asks once, at the instant it arms, and wants the
 * answer as a value. A component whose rendered output depends on the preference
 * has to be told when it changes — a user who turns reduce-motion on mid-session,
 * or an OS that flips it with a battery-saver mode, must not be stuck with a
 * decision this app made at mount.
 */

const QUERY = "(prefers-reduced-motion: reduce)";

/**
 * The live `MediaQueryList`, or `null` where there is none to have: SSR, and any
 * environment without `matchMedia`. Resolved per call rather than captured once
 * at module scope, because a module-scope capture would bind to whatever
 * `window.matchMedia` was at import time — which in jsdom is before a test has
 * installed its own stub, and in the browser is a list nobody can replace anyway,
 * so the per-call form costs nothing and is honest in both.
 */
function reduceMotionQuery(): MediaQueryList | null {
  if (typeof window === "undefined") return null;
  if (typeof window.matchMedia !== "function") return null;
  return window.matchMedia(QUERY);
}

/**
 * Reads the preference right now. Falls back to "motion is fine" wherever the
 * question cannot be asked, which is the same way round `globals.css` falls: a
 * browser that cannot report the preference gets the animated build, not a
 * permanently still one.
 */
export function prefersReducedMotion(): boolean {
  return reduceMotionQuery()?.matches ?? false;
}

/**
 * The store half of [usePrefersReducedMotion]: calls back whenever the platform
 * preference flips, and hands back the detach.
 *
 * Where the query cannot be asked there is nothing to subscribe to and the value
 * can never change, so the unsubscribe is a deliberate no-op rather than an
 * oversight.
 *
 * @param onChange - Called whenever the platform preference flips.
 * @returns The detach for that listener.
 */
function subscribe(onChange: () => void): () => void {
  const query = reduceMotionQuery();
  if (!query) return () => undefined;
  query.addEventListener("change", onChange);
  return () => query.removeEventListener("change", onChange);
}

/**
 * The same preference as a subscribed value, re-rendering the caller when the
 * media query flips.
 *
 * `useSyncExternalStore` rather than a `useState` seeded in an effect — the same
 * choice `useAppMode` and `bulk-selection-signal` already made — so the first
 * render already has the real answer instead of one frame of the wrong one, and
 * so the server snapshot is a named third argument rather than a `typeof window`
 * branch inside a render.
 */
export function usePrefersReducedMotion(): boolean {
  return useSyncExternalStore(subscribe, prefersReducedMotion, () => false);
}

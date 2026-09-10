import { useEffect, useRef, useState } from "react";

/**
 * Reads the OS/browser "reduce motion" preference directly (rather than
 * subscribing to it) because this hook only ever consults it once, at the
 * moment a collapse starts — see the call site below.
 */
const prefersReducedMotion = () =>
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(prefers-reduced-motion: reduce)").matches;

/**
 * Keeps a collapsible body mounted for `durationMs` past the moment
 * `expanded` flips from true to false, so its own CSS fade-out
 * (`.tday-rows-exit` in globals.css) has time to actually finish playing
 * before the caller stops rendering it — the mirror of the fade-in a fresh
 * mount already gets for free from `.tday-rows-enter`. Returns only whether
 * the body should still be rendered right now; the caller picks
 * `.tday-rows-enter` vs `.tday-rows-exit` straight from its OWN `expanded`
 * value, never from anything stored in here, so a rapid re-expand mid
 * fade-out can never leave a stale "still exiting" class on a body that has
 * actually gone back to entering — see `TodayEarlierSection` /
 * `TimelineSectionDroppable` for exactly that.
 *
 * Purely a presentation-layer delay for the Overdue/Earlier ROWS themselves.
 * It never reads or writes `useEarlierExpandHandoff`'s own
 * `expanded`/`handoffPending` state, and it never changes WHEN a caller's
 * `expanded` input flips — only how long the DOM node for its own body
 * lingers afterwards. Shared by every scope's own Overdue/Earlier body
 * (`TodayEarlierSection`, `TimelineSectionDroppable`) so the same fade plays
 * identically everywhere, rather than a fresh copy per call site.
 *
 * Skips the delay outright under reduced motion or a non-positive duration —
 * nothing animates the exit in that case, so lingering in the DOM a beat
 * longer than before would only be a regression, not a kindness.
 */
export function useFadeUnmount(expanded: boolean, durationMs: number): boolean {
  const [mounted, setMounted] = useState(expanded);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const wasExpandedRef = useRef(expanded);

  useEffect(() => {
    const wasExpanded = wasExpandedRef.current;
    wasExpandedRef.current = expanded;

    if (timeoutRef.current != null) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }

    if (expanded) {
      setMounted(true);
      return;
    }

    // Nothing was actually showing — the initial "starts collapsed" render,
    // or a later "still collapsed" pass with no real transition — so there is
    // nothing to fade out.
    if (!wasExpanded) return;

    if (durationMs <= 0 || prefersReducedMotion()) {
      setMounted(false);
      return;
    }

    timeoutRef.current = setTimeout(() => {
      setMounted(false);
      timeoutRef.current = null;
    }, durationMs);
  }, [expanded, durationMs]);

  // Cleared on unmount so a scheduled close never calls setState on a gone
  // component — mirrors `useEarlierExpandHandoff`'s own cleanup.
  useEffect(
    () => () => {
      if (timeoutRef.current != null) clearTimeout(timeoutRef.current);
    },
    [],
  );

  return mounted;
}

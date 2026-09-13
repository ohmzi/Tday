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

/**
 * The other half of the same problem. `useFadeUnmount` keeps a node in the DOM
 * for the length of its exit; this keeps the node's CONTENT worth looking at
 * while it is there.
 *
 * The two surfaces that needed it both paint from state their own dismissal
 * clears on the very tick it starts. The selection bar reads `selectedRows`,
 * and `exitSelection` empties that in the same call that leaves selection mode,
 * so without this the bar would spend its whole exit saying "0 selected" with
 * all four actions greyed out — announcing that it has nothing to do, on its
 * way off the screen. The calendar's search-results panel is the same shape:
 * clearing the query empties the caller's result list, so the panel would flip
 * to "No matching tasks" and fade THAT out instead.
 *
 * Returns `value` while `visible`, and the last value it saw while visible once
 * that goes false. The snapshot is taken in an effect rather than during render
 * so this stays a pure read — on the frame `visible` flips, the ref still holds
 * what the previous render's effect put there, which is exactly the frame that
 * needs it.
 */
export function useExitSnapshot<T>(value: T, visible: boolean): T {
  const lastVisible = useRef(value);

  // No dependency array on purpose: `value` is routinely a freshly built array
  // or string, so a dependency list would either re-run every render anyway or
  // quietly miss a change behind a stable identity.
  useEffect(() => {
    if (visible) lastVisible.current = value;
  });

  return visible ? value : lastVisible.current;
}

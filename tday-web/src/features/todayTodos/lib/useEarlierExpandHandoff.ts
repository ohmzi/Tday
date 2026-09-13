import { useCallback, useEffect, useRef, useState } from "react";
import { usePrefersReducedMotion } from "@/lib/prefersReducedMotion";

/**
 * The single collapse/expand state machine backing every scope's "Earlier"
 * bucket. Every call site passes its own scope's `showEmptyIllustration` —
 * "is the empty-state illustration currently on screen" — as the `toggle`
 * argument below, so an expand engages the extra requirement-3 hand-off
 * exactly when there is an illustration to hand off from, and stays the
 * plain immediate toggle it always was otherwise (a scope with no Earlier
 * bucket, or one whose illustration isn't showing right now, always passes
 * `false`).
 *
 * `handoffPending` is true for exactly the window between that tap and the
 * moment `expanded` actually flips. It is driven by `exitMs` — the SAME rung
 * the illustration's own exit is drawn on, ink and slot together (see
 * `TODAY_EARLIER_EXIT_MS` / `.tday-empty-slot` / `.tday-empty-exit` in
 * globals.css) — so the visual exit and the hand-off are sequenced by
 * construction: one number, read twice, not two guesses tuned to land close
 * together.
 *
 * Under reduced motion there is no such window: the hand-off collapses to the
 * plain immediate toggle and `handoffPending` never goes true at all. See the
 * expand branch for why that is the only honest reading of the preference.
 */
export function useEarlierExpandHandoff(exitMs: number) {
  const [expanded, setExpandedState] = useState(false);
  const [handoffPending, setHandoffPending] = useState(false);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Subscribed rather than read at tap time: the preference decides what this
  // hook's own returned `toggle` will do, so a mid-session flip has to reach
  // the callback the header is already holding.
  const reducedMotion = usePrefersReducedMotion();

  const clearPending = useCallback(() => {
    if (timeoutRef.current != null) {
      clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
  }, []);

  // Cleared on unmount so a scheduled hand-off never calls setState on a gone
  // component — this container remounts per scope/route, so this also covers
  // navigating away mid-exit.
  useEffect(() => clearPending, [clearPending]);

  /**
   * @param illustrationShowing whether the empty-state illustration is
   *   currently occupying the slot Earlier's rows are about to take. Only
   *   matters for an *expand* (`!expanded`); a collapse is always immediate.
   */
  const toggle = useCallback(
    (illustrationShowing: boolean) => {
      // A tap landing inside an already-running hand-off: ignored rather than
      // starting a second one or racing the one in flight — the exact race
      // the Android review caught from a second tap during the exit beat.
      if (handoffPending) return;

      if (!expanded && illustrationShowing && !reducedMotion) {
        // Requirement 3: hide the illustration first (it starts exiting the
        // instant `handoffPending` flips true — see
        // `shouldShowTodayEmptyIllustration`) and only reveal Earlier's rows
        // once that exit has actually finished playing.
        //
        // `!reducedMotion` is load-bearing, not defensive. `.tday-empty-exit`
        // is `animation: none` under the preference (globals.css), so there is
        // nothing left for this timer to wait for: the beat it holds open stops
        // being a sequenced exit and becomes `exitMs` of a static illustration
        // followed by the whole screen changing in one frame — the half-drawn
        // pause rule 5 of `docs/motion.md` exists to forbid, arrived at from
        // the other side. Removing the trip has to remove the wait with it.
        setHandoffPending(true);
        clearPending();
        timeoutRef.current = setTimeout(() => {
          setExpandedState(true);
          setHandoffPending(false);
          timeoutRef.current = null;
        }, exitMs);
        return;
      }

      // Collapsing, expanding with no illustration to hand off from, or any
      // toggle at all under reduced motion: immediate. Also defensively clears
      // any stale pending flag/timer — the exact class of bug the iOS review
      // found, where a hand-off flag set on expand was never cleared back to
      // false on the paths that should have reset it.
      clearPending();
      setHandoffPending(false);
      setExpandedState((value) => !value);
    },
    [clearPending, expanded, exitMs, handoffPending, reducedMotion],
  );

  /** Expands or collapses immediately, bypassing the hand-off entirely — for
   * deep-link/focus navigation, where there is nothing to visibly sequence
   * against. */
  const setExpandedImmediately = useCallback(
    (value: boolean) => {
      clearPending();
      setHandoffPending(false);
      setExpandedState(value);
    },
    [clearPending],
  );

  return { expanded, handoffPending, toggle, setExpandedImmediately };
}

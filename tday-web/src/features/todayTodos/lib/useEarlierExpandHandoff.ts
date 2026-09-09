import { useCallback, useEffect, useRef, useState } from "react";

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
 * moment `expanded` actually flips. It is driven by `exitMs` — the SAME
 * duration the caller also applies to the illustration's own exit animation
 * (see `TODAY_EARLIER_EXIT_MS` / `.tday-empty-exit` in globals.css) — so the
 * visual exit and the hand-off are sequenced by construction: one number,
 * read twice, not two guesses tuned to land close together.
 */
export function useEarlierExpandHandoff(exitMs: number) {
  const [expanded, setExpandedState] = useState(false);
  const [handoffPending, setHandoffPending] = useState(false);
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

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

      if (!expanded && illustrationShowing) {
        // Requirement 3: hide the illustration first (it starts exiting the
        // instant `handoffPending` flips true — see
        // `shouldShowTodayEmptyIllustration`) and only reveal Earlier's rows
        // once that exit has actually finished playing.
        setHandoffPending(true);
        clearPending();
        timeoutRef.current = setTimeout(() => {
          setExpandedState(true);
          setHandoffPending(false);
          timeoutRef.current = null;
        }, exitMs);
        return;
      }

      // Collapsing, or expanding with no illustration to hand off from:
      // immediate. Also defensively clears any stale pending flag/timer —
      // the exact class of bug the iOS review found, where a hand-off flag
      // set on expand was never cleared back to false on the paths that
      // should have reset it.
      clearPending();
      setHandoffPending(false);
      setExpandedState((value) => !value);
    },
    [clearPending, expanded, exitMs, handoffPending],
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

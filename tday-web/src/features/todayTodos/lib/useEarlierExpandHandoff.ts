import { useCallback, useEffect, useRef, useState } from "react";
import { usePrefersReducedMotion } from "@/lib/prefersReducedMotion";

/**
 * Which side of the swap is on screen and leaving, while a tap plays out.
 *
 * Named for whoever is LEAVING rather than for the direction of the tap,
 * because that is the question every consumer actually asks: who still owns
 * the slot right now, and what is drawing its departure on it. `"idle"` covers
 * both settled states — the scene owning the slot, and Earlier's rows owning
 * it — since neither of them is a beat anybody is sequencing against.
 */
export type EarlierHandoff = "idle" | "scene-leaving" | "rows-leaving";

/**
 * The single collapse/expand state machine backing every scope's "Earlier"
 * bucket. Every call site passes its own scope's `earlierSlotChangesHands` —
 * "does this tap swap who owns the empty-state slot" — as the `toggle`
 * argument below, so a tap engages a hand-off exactly when there is something
 * to sequence, and stays the plain immediate toggle it always was otherwise (a
 * scope with no Earlier bucket, or one whose screen has current tasks on it,
 * always passes `false`).
 *
 * Both directions are sequenced, and they are not the same beat. An expand has
 * the scene leaving and Earlier's rows waiting for the slot; a collapse has
 * Earlier's rows leaving and the scene waiting for it. Which is why `expanded`
 * flips at opposite ends of the two:
 *
 * - **Expanding** holds `expanded` false for `sceneExitMs`. The scene is still
 *   the thing on screen, so the flag that says "the rows have the slot" must
 *   not go true until its exit has actually played.
 * - **Collapsing** flips `expanded` false immediately, because that flag is
 *   what starts the rows' own fade (`useFadeUnmount` /`.tday-rows-exit`); what
 *   waits here is the scene, held off for `rowsExitMs` by `"rows-leaving"`
 *   rather than landing on top of a body that is still on its way out.
 *
 * Collapsing used to be the immediate branch, which cost a tap two jumps: the
 * scene remounted into an already-open track and claimed its 42vh in one
 * frame, while the rows it landed on held their own height behind it for the
 * whole of their fade. Sequencing it spends the same beat the rows were
 * already fading for, and gets a swap instead of a pile-up.
 *
 * The two durations are the two exits, and each is the SAME number the thing
 * it waits on is drawn with — `TODAY_EARLIER_EXIT_MS` for `.tday-empty-exit`,
 * `OVERDUE_ROWS_FADE_MS` for `.tday-rows-exit` and the `useFadeUnmount` that
 * keeps those rows in the DOM. One number read twice on each side, not two
 * guesses tuned to land close together.
 *
 * Under reduced motion there is no beat at all: both directions collapse to
 * the plain immediate toggle and `handoff` never leaves `"idle"`. See the
 * sequenced branch for why that is the only honest reading of the preference.
 */
export function useEarlierExpandHandoff(sceneExitMs: number, rowsExitMs: number) {
  const [expanded, setExpandedState] = useState(false);
  const [handoff, setHandoff] = useState<EarlierHandoff>("idle");
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
   * @param slotChangesHands whether this tap actually swaps who occupies the
   *   slot Earlier's rows and the empty-state scene share. False whenever
   *   there is no scene in the swap at all — a screen with current tasks on
   *   it — in which case there is nothing to sequence and this is the plain
   *   toggle it always was.
   */
  const toggle = useCallback(
    (slotChangesHands: boolean) => {
      // A tap landing inside a running beat: ignored rather than starting a
      // second one or racing the one in flight — the exact race the Android
      // review caught from a second tap during the exit beat.
      if (handoff !== "idle") return;

      if (slotChangesHands && !reducedMotion) {
        // `!reducedMotion` is load-bearing, not defensive. Both exits are
        // `animation: none` under the preference (globals.css), so there is
        // nothing left for either timer to wait for: the beat stops being a
        // sequenced exit and becomes a static picture followed by the whole
        // screen changing in one frame — the half-drawn pause rule 5 of
        // `docs/motion.md` exists to forbid, arrived at from the other side.
        // Removing the trip has to remove the wait with it.
        clearPending();

        if (!expanded) {
          // Expanding: hide the scene first (it starts exiting the instant
          // `handoff` flips — see `shouldShowTodayEmptyIllustration`) and only
          // reveal Earlier's rows once that exit has actually finished.
          setHandoff("scene-leaving");
          timeoutRef.current = setTimeout(() => {
            setExpandedState(true);
            setHandoff("idle");
            timeoutRef.current = null;
          }, sceneExitMs);
          return;
        }

        // Collapsing: the rows are the ones leaving, so `expanded` goes false
        // now — it is what arms their fade — and the scene is what waits.
        setExpandedState(false);
        setHandoff("rows-leaving");
        timeoutRef.current = setTimeout(() => {
          setHandoff("idle");
          timeoutRef.current = null;
        }, rowsExitMs);
        return;
      }

      // Nothing swaps (a screen with tasks on it), or any toggle at all under
      // reduced motion: immediate. Also defensively clears any stale beat —
      // the exact class of bug the iOS review found, where a hand-off flag set
      // on expand was never cleared back on the paths that should have reset
      // it.
      clearPending();
      setHandoff("idle");
      setExpandedState((value) => !value);
    },
    [clearPending, expanded, handoff, reducedMotion, rowsExitMs, sceneExitMs],
  );

  /** Expands or collapses immediately, bypassing the hand-off entirely — for
   * deep-link/focus navigation, where there is nothing to visibly sequence
   * against. */
  const setExpandedImmediately = useCallback(
    (value: boolean) => {
      clearPending();
      setHandoff("idle");
      setExpandedState(value);
    },
    [clearPending],
  );

  return { expanded, handoff, toggle, setExpandedImmediately };
}

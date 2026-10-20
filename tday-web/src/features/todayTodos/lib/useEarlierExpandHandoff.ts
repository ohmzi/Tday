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

 * Both directions survive the empty scene moving BELOW Earlier's rows, and
 * neither survives by inheritance: Android retired its `EarlierExpandDeferMillis`
 * on exactly that move, so the question was reopened here rather than answered
 * by copying. The two directions came out differently, on this implementation's
 * own mechanism.
 *
 * COLLAPSE — kept, on an argument Android's does not reach. `.tday-empty-slot`
 * is declared at `grid-template-rows: 1fr` with a transition, and a transition
 * has no start value on mount: the scene's 42vh appears in ONE frame whenever
 * the node mounts, with no track to open through. Reordering the blocks does
 * not touch that. So without the wait the scene would still hard-mount its full
 * height under rows that are holding their own height for the whole of
 * `.tday-rows-exit` — the pile-up above, unchanged. Android gets "the rows
 * leave and the scene rises behind them" for free because its scene is a list
 * item with placement animation; web's is a hard mount into an already-open
 * track.
 *
 * EXPAND — the one the reorder genuinely reopens, and kept. What the 200 ms
 * buys is no longer a dead gap: the scene is visibly sinking and its track
 * closing for the whole of it, and the rows that follow are a played entrance
 * rather than a jump. Rule 5 of `docs/motion.md` asks that removing the motion
 * remove the wait, and the reduced-motion branch below does precisely that —
 * it is the branch that would be wrong to keep, not this one. iOS is the direct
 * precedent and the interesting one: its scene has been anchored below the
 * Earlier header since before any of this, drawn as an overlay with a reserved
 * top height, and it still runs `EarlierIllustrationHandoff.exitDuration`.
 * Anchoring the header does not by itself compel retiring the beat.
 *
 * Retiring it is coherent all the same, so the door is left open with nobody
 * through it — it is not the deletion it looks like.
 * `shouldShowTodayEmptyIllustration` answers false on the frame `earlierExpanded`
 * goes true, so `"scene-leaving"` is the ONLY thing holding the node through the
 * beat: drop the timer and the scene is removed outright with no exit played, a
 * blank cut rather than a faster hand-off. It would need the
 * mount-outlives-visibility split Android has, which here means a SECOND
 * `useFadeUnmount` linger on the node that already carries the cancel one, on a
 * different rung and a different class, with the two never allowed to apply at
 * once — `emptySceneLeavesOnCancel` argues at length why a linger written for
 * one departure must not be spent on both. And it would strand
 * `earlierIsExpanding`, whose only readers are the two chevrons, leaving exactly
 * the unreachable branch this repo keeps having to come back for.
 *
 * The two durations are the two exits, and each is the SAME number the thing
 * it waits on is drawn with — `TODAY_EARLIER_EXIT_MS` for `.tday-empty-exit`,
 * `OVERDUE_ROWS_FADE_MS` for `.tday-rows-exit` and the `useFadeUnmount` that
 * keeps those rows in the DOM. One number read twice on each side, not two
 * guesses tuned to land close together.
 *
 * A tap is not the only thing that plays the scene's exit: `beginSceneExit`
 * below hands the same beat to `useCelebrationSceneExit`, for the window that
 * was holding the scene above Earlier's rows running out. Same class, same
 * rung, same ending — one beat with two ways in rather than two that look alike.
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

  // The scene's own exit beat, factored out because two different things play
  // it: a tap that swaps the slot, and a celebration window running out under a
  // scene that was only on the slot because of it. Deliberately ONE beat rather
  // than two that look alike — same class, same rung, same ending, and the
  // "a tap landing inside a running beat is ignored" rule falls out of the two
  // of them sharing this one timer instead of having to be restated.
  const startSceneExit = useCallback(() => {
    setHandoff("scene-leaving");
    timeoutRef.current = setTimeout(() => {
      setExpandedState(true);
      setHandoff("idle");
      timeoutRef.current = null;
    }, sceneExitMs);
  }, [sceneExitMs]);

  /**
   * Plays the scene off the slot for a reason that is not a tap — see
   * `useCelebrationSceneExit` below, which is the only caller.
   */
  const beginSceneExit = useCallback(() => {
    // `timeoutRef` and not `handoff`: the two say the same thing (a beat is in
    // flight exactly while a timer is armed) and only one of them leaves this
    // callback's identity stable, which is load-bearing — the effect that calls
    // it lists it as a dependency, and a callback rebuilt on every state change
    // would re-run that effect mid-beat.
    if (timeoutRef.current != null) return;
    // No trip, so no wait: under the preference the scene goes on the frame its
    // reason for being there did, which is the finished state rule 5 of
    // `docs/motion.md` asks for rather than a beat of a picture that cannot
    // animate.
    if (reducedMotion) return;
    startSceneExit();
  }, [reducedMotion, startSceneExit]);

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
          startSceneExit();
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
    [clearPending, expanded, handoff, reducedMotion, rowsExitMs, startSceneExit],
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

  return { expanded, handoff, toggle, beginSceneExit, setExpandedImmediately };
}

/**
 * Plays the scene off the slot when the celebration window that was keeping it
 * there runs out.
 *
 * The window closing is the scene's cue to leave on exactly one screen shape:
 * an empty scope whose Earlier bucket is open, where the rows below own the
 * slot and the scene is sitting on it for the length of the celebration and
 * nothing else (`shouldShowTodayEmptyIllustration`'s last line). Everywhere
 * else the window shutting takes nothing off the screen, so there is nothing to
 * play out and this stays silent.
 *
 * Reusing the hand-off's own beat rather than inventing a second one is the
 * whole design: this departure ends the same way an expand's does — the scene
 * gone, Earlier's rows holding the slot — so it is the same beat arriving by a
 * different route, and a tap that lands inside it is ignored by the rule that
 * already ignores one.
 *
 * The falling edge is read from a ref rather than from a `celebrate` that
 * lingers, because the alternative is to hold the flag true through the exit
 * and then work out which of its two meanings is live. `celebrate` means the
 * window is open; the beat means the scene is leaving; conflating them is how a
 * later reader gets the scene back at full opacity on the frame the beat ends.
 *
 * It is read DURING RENDER, and the returned flag is why. The edge and the
 * thing it is an edge of arrive on the same render — `celebrate` going false is
 * what closes the window — and on that render nothing has armed the beat yet,
 * so `shouldShowTodayEmptyIllustration` falls through to its last line and
 * answers false. Discovering the edge one commit later, in an effect, therefore
 * costs the scene its node: React unmounts the slot on the closing render and
 * the effect mounts a fresh one carrying the exit classes. That is three
 * defects, not one — a blank frame between the two commits; a track whose FIRST
 * computed style is already `0fr`, so the 42vh is reclaimed in a single frame
 * instead of over `.tday-empty-slot`'s transition; and `.tday-empty-enter`
 * restarting on the element that is supposed to be leaving, a 520ms Scene
 * arrival playing underneath a 200ms departure.
 *
 * So the closing render holds the scene itself. `holdingForExit` is set during
 * that render (the standard "adjust state while rendering" pattern — React
 * re-runs this component before committing, so the false answer is never
 * committed at all), and the effect below turns it into the beat one commit
 * later, by which point the slot node the exit classes land on is the same node
 * that has been sitting there for the whole window. One held frame, drawn
 * exactly as the frame before it, buys a departure that is actually played.
 *
 * The tap path needs none of this and is the contrast that proves the shape:
 * `toggle` sets the hand-off synchronously inside the event handler, so the
 * scene's `true` never lapses and there is no gap to hold across.
 *
 * @returns whether the scene is being held on the slot for a departure that has
 *   been decided and not yet started — feed it to
 *   `shouldShowTodayEmptyIllustration`.
 */
export function useCelebrationSceneExit({
  celebrate,
  sceneLeavesWithTheWindow,
  beginSceneExit,
}: {
  /** A completion (this tab's or a remote one) just emptied this scope's current tasks. */
  celebrate: boolean;
  /** Everything but the window itself that puts the scene on the slot right now. */
  sceneLeavesWithTheWindow: boolean;
  /** `useEarlierExpandHandoff`'s own `beginSceneExit`. */
  beginSceneExit: () => void;
}): boolean {
  const wasCelebrating = useRef(celebrate);
  const [holdingForExit, setHoldingForExit] = useState(false);

  const windowJustClosed = wasCelebrating.current && !celebrate;
  wasCelebrating.current = celebrate;
  // Read at the edge and not on every pass: the scene also stops being held
  // when the user collapses Earlier, or when a task arrives and the scope is
  // no longer empty. Neither is this departure — in both, something else is
  // already taking the slot — and playing the scene out of a slot it is about
  // to be given back would end the beat by putting it straight back on.
  if (windowJustClosed && sceneLeavesWithTheWindow && !holdingForExit) {
    setHoldingForExit(true);
  }

  useEffect(() => {
    if (!holdingForExit) return;
    // Both in one effect pass, so React batches them into the single render
    // that swaps the hold for the beat. Dropped first and unconditionally:
    // under reduced motion `beginSceneExit` starts nothing, and a hold nobody
    // ever releases is the scene sitting there until an unrelated render
    // happens to notice — the exact ending this whole unit replaced.
    setHoldingForExit(false);
    beginSceneExit();
  }, [beginSceneExit, holdingForExit]);

  return holdingForExit;
}

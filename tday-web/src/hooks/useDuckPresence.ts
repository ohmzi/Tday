import { useRef } from "react";
import { useFadeUnmount } from "./useFadeUnmount";
import { DURATION_MS } from "@/lib/motion";

/** What a caller needs to render one ducking control. */
export type DuckPresence = {
  /** Whether the control should be rendered at all right now. */
  mounted: boolean;
  /**
   * The `.tday-duck-*` class for this frame, or `""` for a control that has
   * simply always been there. Belongs on the control's own fixed positioning
   * wrapper — the element the travel has to move.
   */
  className: string;
  /**
   * Whether this control's own hit targets should be live. False for exactly
   * the frames of the exit: what is on the screen then is a picture of a dock,
   * and the thing that took its slot is underneath it.
   */
  interactive: boolean;
};

/**
 * The bottom chrome ducking: `RootDock` and `TaskFloatingActionButton` travel
 * down and out through the bottom edge instead of ceasing to exist, and travel
 * back up when their slot is theirs again.
 *
 * Two things have to be true for that, and neither is free on web. The node has
 * to outlive the flag that took it away, or the exit has no frames to play in —
 * that half is `useFadeUnmount`, on the Emphasis rung the stylesheet's
 * `.tday-duck-*` pair also names, so the class and the timer cannot drift.
 *
 * The other half is knowing that an arrival is an arrival. Android's
 * `AnimatedVisibility` plays no enter for a `visible` that was already true on
 * the first composition, and `TdayApp.kt` argues that is the behaviour wanted
 * rather than a limitation worked around: a first paint has nothing to hand over
 * from, so chrome that was there from the start is drawn in its slot. iOS gets
 * the same for free — `.animation(_:value:)` opens no transaction for a value
 * that has not changed. Web mounts and unmounts rather than holding a transition
 * object across the flip, so it has to remember for itself which arrivals had a
 * departure in front of them; `wasEverAbsent` is that memory, and without it
 * opening the app would play as the dock arriving from somewhere.
 *
 * Lingering has a cost the native clients do not pay, and it is the third
 * thing this hook reports. `BulkSelectionBar` rises into the slot the dock is
 * leaving, and the dock is painted above it — `NativeAppShell` renders it
 * outside the `relative z-0` stacking context the bar lives in — so for the
 * whole Emphasis rung a tap aimed at Delete would land on a tab instead. The
 * bar already gates its own buttons on the live flag for the mirror of this
 * reason; `interactive` is the same gate for the controls, and the reason it is
 * a web-only concern is that Android and iOS duck their chrome for the search
 * field alone, where nothing arrives in the vacated row to be tapped.
 *
 * @param present - Whether this control's slot is currently its own.
 * @returns Whether to render it, the class its wrapper should carry, and
 *   whether it may still be tapped.
 */
export function useDuckPresence(present: boolean): DuckPresence {
  const mounted = useFadeUnmount(present, DURATION_MS.emphasis);

  // Written during render rather than in an effect, because the frame that
  // needs the answer is the frame the flag flips on: an effect would leave the
  // control one paint at its finished position before the exit started, which
  // is the jump this hook exists to remove. The write is idempotent and only
  // ever false → true, so a double render decides the same thing twice.
  const wasEverAbsent = useRef(false);
  if (!present) wasEverAbsent.current = true;

  return {
    mounted,
    className: wasEverAbsent.current
      ? present
        ? "tday-duck-enter"
        : "tday-duck-exit"
      : "",
    // Read off `present` rather than off the class, so a control that is
    // arriving — or one that never left — is live from its first frame: it is
    // going to stay, and a dock that ignores the first 320 ms of taps after a
    // selection is dismissed would be its own bug.
    interactive: present,
  };
}

import { useFadeUnmount } from "@/hooks/useFadeUnmount";
import { DURATION_MS } from "@/lib/motion";

export type SkeletonCrossfade = {
  /** Whether the placeholder should still be rendered this frame. */
  showSkeleton: boolean;
  /**
   * The class the placeholder's WRAPPER carries while it is on borrowed time,
   * and nothing while the content is genuinely still loading — a skeleton that
   * is still the truth has nothing to fade.
   */
  skeletonClassName: string | undefined;
};

/**
 * Keeps a loading placeholder on screen for the length of its own fade-out, so
 * the hand-over to the content reads as one motion instead of a swap between
 * two frames.
 *
 * No timer of its own: `useFadeUnmount` already keeps a body mounted past the
 * flip that removes it, already reads the reduced-motion preference imperatively
 * at the moment it arms (so a user who asked for no motion gets the content on
 * the same frame, with no lingering ghost and no wait bought in place of the
 * animation), and already clears itself on unmount. A second copy of that timer
 * living here would be a second set of those three decisions.
 *
 * `DURATION_MS.enter` is the JS half of the one number `.tday-skeleton-exit`
 * spells as `var(--tday-duration-enter)`: the CSS draws the fade and this decides
 * when the node actually goes, and the two line up because both read the
 * generated rung rather than two integers tuned to look alike. The same
 * one-number-read-twice shape `TODAY_EARLIER_EXIT_MS` and `.tday-empty-exit`
 * already use, with the JS side here holding no `animationDuration` of its own —
 * unlike `.tday-rows-exit`, whose rule deliberately names no duration and leaves
 * every caller to inline one.
 *
 * Callers put `skeletonClassName` on a wrapper ABOVE the pulsing bars, never on
 * the bars: `.animate-pulse` is exempt from the blanket reduced-motion floor and
 * an element carrying both classes would carry the exemption into the fade. The
 * class argues that at length in `globals.css`.
 */
export function useSkeletonCrossfade(loading: boolean): SkeletonCrossfade {
  const showSkeleton = useFadeUnmount(loading, DURATION_MS.enter);

  return {
    showSkeleton,
    // Read from `loading` and not from anything stored in here, the rule
    // `useFadeUnmount`'s own doc comment states: a load that restarts mid-fade
    // is back to telling the truth, and a stale "still leaving" class on it
    // would fade the placeholder out from under content that has not arrived.
    skeletonClassName: showSkeleton && !loading ? "tday-skeleton-exit" : undefined,
  };
}

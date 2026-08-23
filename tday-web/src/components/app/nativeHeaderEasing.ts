export const clamp01 = (value: number) => Math.min(Math.max(value, 0), 1);

/**
 * Septic (7th-order) smootherstep: `35t^4-84t^5+70t^6-20t^7`.
 *
 * Its derivative is `140t^3(1-t)^3`, so the first three derivatives are all zero
 * at both ends — one order flatter than quintic, two flatter than the usual
 * cubic smoothstep. That is what takes the sting out of the start and the stop;
 * the peak is correspondingly quicker so the middle does not turn sluggish.
 *
 * Shared by both header families — the root feeds' pinned header and the
 * per-page collapsing one — so the two feel like one thing. It is the same
 * curve `RootFeedHeroHeaderMetrics.stagger` uses on iOS and Android; keep the
 * three platforms in step.
 */
export function smootherstep(progress: number) {
  const t = clamp01(progress);
  return t * t * t * t * (35 + t * (-84 + t * (70 - 20 * t)));
}

/** [smootherstep] compressed into the leading `end` fraction of the range. */
export function stagger(progress: number, end: number) {
  if (end <= 0) return progress > 0 ? 1 : 0;
  return smootherstep(progress / end);
}

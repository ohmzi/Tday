/**
 * How much of the pinned bar the docked title may not have, so that it stays
 * clear of the controls either side of it — and whether what is left is worth
 * drawing a title into at all.
 *
 * Its own module, and pure, for the reason `TdayBarTitleReserveTest` exists one
 * client over: this is a layout claim, there is no device here to settle one on,
 * and the arithmetic IS the behaviour. Everything the browser supplies — the
 * bar's width, the two clusters' widths, what the name wants at the docked size
 * — arrives as a plain number, so the rule can be checked without rendering
 * anything and without a layout engine. `NativePageHeader` does the reading;
 * this does the deciding. The sibling `nativeHeaderEasing.ts` is split out of
 * the same file for the same reason.
 *
 * The constants live here rather than in `nativePageHeaderMetrics` because
 * neither of them describes anything else: both exist only to serve the rule
 * below, and a floor whose doc argues about the reserve should sit next to the
 * reserve.
 */
export const nativePageBarTitleMetrics = {
  /** Breathing room each side of the title once it has docked in the bar. */
  sideGap: 8,

  /**
   * Least width worth docking a title into — one initial and the ellipsis,
   * which at the docked size is nearly all ellipsis: Nunito at 2.1rem/900 draws
   * that alone 27.4px wide, against 23.1px for a "C". (Those two numbers were
   * 28.7 and 21.4; re-measured against the `Nunito.ttf` this repo actually
   * ships, instantiated at wght 900 — advances plus GPOS kerning. The
   * conclusion survives, since "C…" is 50.9px and still under this floor.) It
   * is not raised past this because there is nothing to raise it into: the
   * busiest bar in the app, the floater list's five controls, leaves the title
   * exactly 64px at 390 and 70px is all the sibling custom list has at 360.
   * Below this the bar keeps no title at all.
   *
   * It gates whether a title is shown AT ALL. It used ALSO to gate whether one
   * fits — see [nativePageBarTitleReserve], where that confusion is now undone
   * rather than merely warned about.
   */
  minWidth: 56,
} as const;

/** What the collapse writes onto the docked title, and whether it draws one. */
export type NativePageBarTitleReserve = {
  /** Left padding, in px, for the docked title's full-width box. */
  leadingReserve: number;
  /** Right padding, in px, for the same box. */
  trailingReserve: number;
  /** False when the bar's own controls have eaten the row: draw no title. */
  hasRoom: boolean;
};

/**
 * Mirror the WIDER side on both sides while the title actually fits in what
 * that leaves; otherwise reserve only what each side really holds.
 *
 * The mirrored branch is first and stays first: it keeps the title centred on
 * the BAR rather than on the leftovers, which is load-bearing for the crossfade
 * — the block's own copy of the title is centred on the page, and the docked
 * copy has to be the same element arriving at the same place or the name
 * appears to slide sideways as it docks.
 *
 * The bug this function was extracted to fix lived in one term. The gate used to
 * read `barWidth - symmetric * 2 >= minWidth`, which asks whether a STUMP would
 * fit, not whether the TITLE would — two different questions, and the second is
 * the one the branch is choosing on behalf of. Android ran the identical rule
 * and it produced a reported bug; this bar had it worse, because its Calendar
 * "Today" control is a text pill rather than a collapsing circle. At a 412px
 * viewport the bar is 380px, leading is one 56px back button, trailing is a 56px
 * search button plus a 10px gap plus the pill — about 150px — so the old gate
 * saw 380 − 316 = 64px left, called that "a title fits", and handed "Calendar"
 * 64px for a word that wants 146.6px at 2.1rem/900: more than half of it
 * clipped, on a bar where the per-side fallback would have given it 158px.
 *
 * Both terms of the `max` are load-bearing and neither subsumes the other. The
 * title term is the fix. The [nativePageBarTitleMetrics.minWidth] term is the
 * OLD gate, kept: without it a title narrower than the floor — or one not yet
 * measured — would hold the mirrored branch on a bar with no room for any title
 * at all, and `hasRoom` below would then answer against a reserve chosen for the
 * wrong reason.
 *
 * Why the change cannot cost any bar width, which is what makes it safe for the
 * six screens that were already fitting. Per-side leaves `W − L − T − 2g` and
 * mirrored leaves `W − 2·max(L,T) − 2g`, so per-side beats mirrored by exactly
 * `|T − L|` — never negative. This function only ever moves a bar from mirrored
 * to per-side, never the other way, so the room it hands back is greater than or
 * equal to the room the old rule handed back, for every bar in the app, at every
 * width, whatever the device measures. `native-page-bar-title-reserve.test.ts`
 * pins that as a property rather than as a handful of examples.
 *
 * Deliberately NOT ported from Android in this pass: `tdayBarTitleScale`, the
 * bounded shrink that runs after this. It is a separate mechanism, and here it
 * would have to multiply into the `scale()` the reveal already writes every
 * frame — a restyle folded into a layout repair. The bars it would rescue are
 * the ones where mirrored is already negative, which this function has already
 * moved to per-side; what it would buy on top is a whole name at 0.72 instead of
 * an ellipsized one, and that is a typography decision, not this one.
 *
 * @param titleWidth what the title wants at the full docked size. Zero when it
 *   has not been measured yet — and zero fits everything, so the first frame
 *   takes the mirrored branch exactly as it always did, then settles onto the
 *   real answer when the measurement lands. That is what makes the measurement
 *   safe to be late (see `document.fonts.ready` in `NativePageHeader`).
 */
export function nativePageBarTitleReserve({
  barWidth,
  leadingWidth,
  trailingWidth,
  titleWidth = 0,
}: {
  barWidth: number;
  leadingWidth: number;
  trailingWidth: number;
  titleWidth?: number;
}): NativePageBarTitleReserve {
  const m = nativePageBarTitleMetrics;

  // Before the bar has a box — the very first frame, or a bar that is not
  // displayed — reserve what is actually there: never centred, but never
  // overlapping either, and replaced on the very next frame. Answering
  // `hasRoom` here is moot in practice, since a bar with no rect also has no
  // collapse fraction and the title is at zero opacity regardless; it matches
  // Android's degenerate case so the two functions stay readable against each
  // other.
  if (barWidth <= 0) {
    return { leadingReserve: leadingWidth, trailingReserve: trailingWidth, hasRoom: true };
  }

  const symmetric = Math.max(leadingWidth, trailingWidth) + m.sideGap;
  const centred = barWidth - symmetric * 2 >= Math.max(titleWidth, m.minWidth);
  const leadingReserve = centred ? symmetric : leadingWidth + m.sideGap;
  const trailingReserve = centred ? symmetric : trailingWidth + m.sideGap;
  return {
    leadingReserve,
    trailingReserve,
    // `truncate` clips to the PADDING box rather than the content box, so a
    // reserve wider than the bar does not hide the title — it paints it across
    // the controls and off the end of the screen. This is the step that refuses
    // instead: no title, and the block's own copy is the page's real heading.
    hasRoom: barWidth - leadingReserve - trailingReserve >= m.minWidth,
  };
}

import type { CSSProperties, ElementType } from "react";
import { CalendarCheck, Check, Leaf } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * How much of the box each glyph behind the check is drawn in, and where the leaf
 * sits inside it.
 *
 * Two things had to be true of the back plate at once: the two rear glyphs have to
 * read as two rather than fuse into one fringe, and each has to be nameable at the
 * size the mark is actually drawn. Drawn concentric at one size the three fused;
 * graduated by scale alone — leaf 0.62, calendar 0.88, the first arrangement —
 * the leaf still did not name, because at 0.62 its contour runs through the
 * calendar's header rule and *within* both frame walls, so the calendar's own
 * straight lines cut its silhouette at every crossing. Rasterised, that leaf kept
 * 59.3% of its ink, in six disconnected pieces: the "scratch" the mark was
 * reported as, and the one glyph of the three that was present, paid for and not
 * nameable.
 *
 * So the leaf is drawn small enough to sit *inside* the calendar's body — under
 * the header rule, above the frame's foot, and inside both walls — and shifted
 * right, out from under the front check's own lower arm. At 0.335 of the box its
 * outline clears the calendar's frame by 0.88 of a unit on every side, against
 * the 0.84 the first arrangement recorded: 1.61pt at the hero's 44pt, 1.17pt at
 * the badge's 32, 7.77dp at Android's 212dp watermark, 10.56px at web's 288px.
 * Rasterised, the same leaf now keeps 88.6% of its ink, in a single piece.
 *
 * The one contour it cannot avoid is the calendar's own inner tick, which sits in
 * the middle of the body the leaf now occupies: the leaf is drawn *over* it, so
 * the tick is covered rather than cut. That tick was already unreadable behind the
 * front check — its arms pass within the strokes' half-widths of the check's arms
 * at every pair of scales these two glyphs allow — so nothing legible is lost, and
 * the leaf's silhouette survives whole.
 *
 * The binding pair is now the leaf's topmost point against the header rule and its
 * foot against the frame's, both 0.88 of a unit. A scaled glyph scales its stroke
 * with it, so the leaf carries 0.67 of a unit of stroke against the calendar's
 * 1.76: the pair behind reads as *behind* partly by being drawn in a finer line
 * than the check's 2.
 */
const rearScale = { leaf: 0.335, calendar: 0.88 } as const;

/**
 * Where the leaf sits inside the box, as a fraction of it — lucide draws in a
 * 24-unit box, so these are 2.5 and 3.69 of those units. Down and to the right:
 * down is what puts the leaf under the calendar's header rule, and right is what
 * takes it out from under the front check's lower arm. The rightward half is
 * worth a third of the leaf's ink — at the box's centre, at this scale, the same
 * leaf keeps 60.1% where it keeps 88.6% here.
 */
const rearLeafOffset = { x: 2.5 / 24, y: 3.69 / 24 } as const;

/**
 * The Completion-history page's mark: one green check, with the Scheduled board's
 * `calendar-check` and the Floater's leaf behind it as a single faint plate, at
 * the graduated sizes and the leaf offset [rearScale] and [rearLeafOffset]
 * describe. The two behind read as depth under the check rather than as three
 * icons: the calendar is the page the leaf is drawn on, and the check is over
 * both. At the three sizes this mark is drawn, all three are nameable.
 *
 * A component and not an asset, because there is no compositing primitive to
 * reach for: three `lucide-react` icons in one relative box is the whole drawing.
 *
 * The check is drawn at full strength in `currentColor`, which is how every other
 * mark here takes its page's accent; the two behind it are that same colour at
 * `rearOpacity`, so they are a fainter *same* colour and never a second one. On
 * the Tasks tab of the history `currentColor` resolves to `#719F84` —
 * `nativeScreenAccentColors.completed`, the value Android's `TdayCompletedTileAccent`
 * and iOS's `tdayCompletedGreen` hold — which is deliberately the colour of the
 * Completed tile the user arrives through.
 *
 * This supersedes the single `calendar-check` this page's three mark sites
 * carried for one commit: the glyph is still here, behind the check, and no
 * longer alone.
 */
export default function CompletedMark({
  className,
  style,
  strokeWidth = 2,
  rearOpacity = 0.17,
}: {
  className?: string;
  style?: CSSProperties;
  strokeWidth?: number;
  /**
   * Alpha of each glyph behind the check, as a fraction of the front one — so it
   * survives the opacity a host puts on the whole mark. 0.17 is the hero disc's
   * own echo (`nativePageHeaderMetrics.markEchoAlpha`), which puts the back plate
   * on the same depth plane as the bleed beside it; the page watermark passes
   * more, because everything there sits under a 0.045 veil.
   */
  rearOpacity?: number;
}) {
  return (
    <span aria-hidden className={cn("relative inline-block", className)} style={style}>
      <CompletedMarkLayer
        Icon={CalendarCheck}
        scale={rearScale.calendar}
        strokeWidth={strokeWidth}
        opacity={rearOpacity}
      />
      <CompletedMarkLayer
        Icon={Leaf}
        scale={rearScale.leaf}
        offsetX={rearLeafOffset.x}
        offsetY={rearLeafOffset.y}
        strokeWidth={strokeWidth}
        opacity={rearOpacity}
      />
      <CompletedMarkLayer Icon={Check} scale={1} strokeWidth={strokeWidth} opacity={1} />
    </span>
  );
}

/**
 * One glyph of the stack, centred in the box and sized as a fraction of it. The
 * percentage does both jobs at once: it sets the scale, and because a lucide
 * glyph's stroke is drawn in the same 24-unit space, it thins the stroke in the
 * same proportion — which is what [rearScale] wants.
 */
function CompletedMarkLayer({
  Icon,
  scale,
  strokeWidth,
  opacity,
  offsetX = 0,
  offsetY = 0,
}: {
  Icon: ElementType;
  scale: number;
  strokeWidth: number;
  opacity: number;
  /** Displacement from the box's centre, as a fraction of the box. */
  offsetX?: number;
  offsetY?: number;
}) {
  return (
    <Icon
      aria-hidden
      strokeWidth={strokeWidth}
      className="absolute -translate-x-1/2 -translate-y-1/2"
      style={{
        width: `${scale * 100}%`,
        height: `${scale * 100}%`,
        opacity,
        // `top`/`left` rather than a transform, because `translate` is already
        // doing the centring and a percentage of an absolutely positioned box
        // resolves against its containing block on both axes.
        left: `calc(50% + ${offsetX * 100}%)`,
        top: `calc(50% + ${offsetY * 100}%)`,
      }}
    />
  );
}

type MarkProps = {
  className?: string;
  style?: CSSProperties;
  strokeWidth?: number;
};

/**
 * The mark as the page watermark draws it. The pair behind the check is stronger
 * here than the hero's 0.17 because nothing in the drawing is stronger: the whole
 * mark sits under `ScreenWatermark`'s 0.045 veil, and at the hero's ratio the back
 * plate would not survive it — the watermark would show the check alone, and the
 * page would be drawing two different marks.
 */
export function CompletedWatermarkMark(props: MarkProps) {
  return <CompletedMark {...props} rearOpacity={0.45} />;
}

/**
 * The mark as the empty state's badge draws it. Stronger than the hero's 0.17
 * again, for the opposite reason: the badge is a white glyph on the accent disc,
 * where a 0.17 ghost goes missing, and it is drawn at 32px rather than 44.
 */
export function CompletedBadgeMark(props: MarkProps) {
  return <CompletedMark {...props} rearOpacity={0.25} />;
}

import type { CSSProperties, ElementType } from "react";
import { CalendarCheck, Check, Leaf } from "lucide-react";
import { cn } from "@/lib/utils";

/**
 * How much of the box each glyph behind the check is drawn in.
 *
 * The three used to be drawn at one size and concentric, and the leaf stopped
 * reading: at 1:1 its contour runs *inside* the calendar's frame by 0–1 of
 * lucide's 24 units — its left arc 1 unit inside the left wall, its rightmost
 * point (21,10) exactly on the right wall at the header rule's own y, its tip
 * level with the calendar's own binding ticks. Two strokes need a full stroke
 * width between their centrelines to read as two, so the leaf fused into a fringe
 * along the frame and the back plate became one grey box.
 *
 * Different sizes are what separate them, and the binding pair is the leaf's
 * rightmost point against the calendar's right wall: both sit at 12 + 9 × scale,
 * so they move apart by 9 × (calendar − leaf) = 9 × 0.26 = 2.34 units. The two
 * strokes carry 0.88 + 0.62 = 1.50 units of half-width between them, because a
 * scaled glyph scales its stroke with it, so the outlines clear by 0.84 of a unit
 * — over half a stroke width — at every size the mark is drawn.
 *
 * The scaling also thins the strokes, which is the right direction: the mark is
 * one green, and the pair behind reads as *behind* partly because it is drawn in
 * a finer line (1.24 units for the leaf, 1.76 for the calendar) than the check
 * (2 units).
 *
 * One pair is not fully cleared and it is worth naming: the leaf's tip passes
 * within ~0.82 units of the calendar's 1.76-unit right binding tick, which is
 * inside the 1.50 the two carry, so the tip grazes that tick. It is the one
 * contour that cannot be separated at any pair of scales these shapes allow —
 * clearing it needs the leaf below 0.375 of the calendar, where it stops reading
 * at 44pt, or a plate moved off-centre, which is visibly lopsided at the hero's
 * 96pt disc. It is a ~1pt graze at 44pt, under a 0.17 ghost.
 */
const rearScale = { leaf: 0.62, calendar: 0.88 } as const;

/**
 * The Completion-history page's mark: one green check, with the Floater's leaf
 * and the Scheduled board's `calendar-check` stacked behind it as a single faint
 * plate, at the graduated sizes [rearScale] describes. The two behind read as
 * depth under the check rather than as three icons, which is the arrangement the
 * page was asked for.
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
        Icon={Leaf}
        scale={rearScale.leaf}
        strokeWidth={strokeWidth}
        opacity={rearOpacity}
      />
      <CompletedMarkLayer
        Icon={CalendarCheck}
        scale={rearScale.calendar}
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
}: {
  Icon: ElementType;
  scale: number;
  strokeWidth: number;
  opacity: number;
}) {
  return (
    <Icon
      aria-hidden
      strokeWidth={strokeWidth}
      className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2"
      style={{ width: `${scale * 100}%`, height: `${scale * 100}%`, opacity }}
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

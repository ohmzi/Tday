import type { CSSProperties, ElementType } from "react";
import { CalendarCheck, CircleCheckBig, Leaf } from "lucide-react";
import { cn } from "@/lib/utils";

type CompletedMarkVariant = "scheduled" | "floater";

/**
 * Size and position of the floater variant's checkmark badge, as a fraction of
 * the box — bottom-right corner, small enough to read as a badge on the leaf
 * rather than a second icon competing with it. `offset` is displacement from
 * the box's centre, matching [CompletedMarkLayer]'s own fraction convention.
 */
const badgeScale = 0.42;
const badgeOffset = { x: 0.3, y: 0.3 } as const;

/**
 * The Completion-history page's mark. The Scheduled board draws a plain
 * calendar-check; the Floater board draws its own leaf with that same
 * checkmark as a small badge over its corner. Both are a single, fully opaque
 * glyph (or glyph pair) — not a front check over a faint ghost pair, which is
 * what this drew before: the same calendar-check-plus-leaf-plus-check stack,
 * unconditionally, for both boards, so the mark never actually differed by
 * variant despite the visual complexity. Android and iOS carry the identical
 * two-variant design (`CompletedMark`/`CompletedMarkLayer` there too).
 */
export default function CompletedMark({
  variant,
  className,
  style,
  strokeWidth = 2,
}: {
  variant: CompletedMarkVariant;
  className?: string;
  style?: CSSProperties;
  strokeWidth?: number;
}) {
  return (
    <span aria-hidden className={cn("relative inline-block", className)} style={style}>
      {variant === "scheduled" ? (
        <CompletedMarkLayer Icon={CalendarCheck} scale={1} strokeWidth={strokeWidth} />
      ) : (
        <>
          <CompletedMarkLayer Icon={Leaf} scale={1} strokeWidth={strokeWidth} />
          <CompletedMarkLayer
            Icon={CircleCheckBig}
            scale={badgeScale}
            offsetX={badgeOffset.x}
            offsetY={badgeOffset.y}
            strokeWidth={strokeWidth}
          />
        </>
      )}
    </span>
  );
}

/**
 * One glyph of the mark, centred in the box and sized as a fraction of it. The
 * percentage does both jobs at once: it sets the scale, and because a lucide
 * glyph's stroke is drawn in the same 24-unit space, it thins the stroke in
 * the same proportion.
 */
function CompletedMarkLayer({
  Icon,
  scale,
  strokeWidth,
  offsetX = 0,
  offsetY = 0,
}: {
  Icon: ElementType;
  scale: number;
  strokeWidth: number;
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
 * Variant-bound marks, for the call sites (`NativePageHeader`'s icon,
 * `ScreenWatermark`'s icon, `EmptyState`'s badge icon) that take a bare
 * `ElementType` and render it themselves, with no way to pass a `variant`
 * prop through. One mark, referenced by identity, per board.
 *
 * There is no separate watermark/badge variant any more: the old
 * `CompletedWatermarkMark`/`CompletedBadgeMark` existed only to hand the ghost
 * layers a stronger `rearOpacity` for those two hosts. `ScreenWatermark`
 * already applies its own fixed dimming (`opacity-[0.045]`) around whatever
 * icon it's given, and `EmptyState` draws its badge icon in solid white on an
 * accent-coloured disc with no extra fade — both already dim or colour the
 * mark as a whole from the outside. With no ghost layer left to tune
 * separately, the plain mark is correct at all three sizes.
 */
export function ScheduledCompletedMark(props: MarkProps) {
  return <CompletedMark {...props} variant="scheduled" />;
}

export function FloaterCompletedMark(props: MarkProps) {
  return <CompletedMark {...props} variant="floater" />;
}

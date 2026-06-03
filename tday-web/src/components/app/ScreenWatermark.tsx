import type { ElementType } from "react";

/**
 * Large faint icon sitting in the screen background, matching the native app's
 * per-screen watermark. Fixed + behind content so it stays put while scrolling
 * and never interferes with taps.
 */
export default function ScreenWatermark({
  icon: Icon,
  color,
}: {
  icon: ElementType;
  color?: string;
}) {
  return (
    <Icon
      aria-hidden
      strokeWidth={1.5}
      className="pointer-events-none fixed right-[-3rem] top-1/2 -z-10 h-72 w-72 -translate-y-1/2 opacity-[0.045] sm:right-[-1.5rem] sm:h-[28rem] sm:w-[28rem]"
      style={color ? { color } : undefined}
    />
  );
}

import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export const nativeAppHorizontalPaddingClassName = "px-4 sm:px-6 lg:px-10";

export const nativeAppScrollClassName = cn(
  // `overflow-x-hidden` is not redundant beside `overflow-y-auto`, which is the
  // trap: CSS says a `visible` overflow on one axis computes to `auto` when the
  // other axis is not visible, so asking for a vertical scroller silently asks
  // for a horizontal one too. Nothing here is wide enough to scroll, but a
  // horizontally scrollable box still takes a sideways drag and rubber-bands —
  // so every screen could be dragged off centre, which no native screen does.
  // Also stops a sideways flick chaining out to the browser's back gesture.
  // `overscroll-none`, not just `-x`: iOS bounces a nested scroller past its
  // own end, and everything inside translates with it — `position: sticky`
  // included, so the pinned toolbar rides the bounce off the top of the screen.
  // It never leaves its sticky range, which is why it measures as pinned at
  // every scroll offset in both engines and still looked wrong on the device.
  // On a screen whose content only just exceeds the viewport you reach that end
  // constantly, which is why it showed up on some screens and not others.
  "h-full w-full overflow-y-auto overflow-x-hidden overscroll-none scrollbar-none",
  "pb-[calc(7rem+env(safe-area-inset-bottom))] pt-4 sm:pt-6",
  nativeAppHorizontalPaddingClassName,
);

export const nativeAppContentClassName = "mx-auto w-full max-w-6xl";

// Marks the active screen's scroll container so the root dock can scroll it to
// the top when the already-selected tab is tapped again.
export const nativeAppScrollAttribute = "data-native-scroll";

export function NativeAppPageLayout({ children }: { children: ReactNode }) {
  return (
    <div className={nativeAppScrollClassName} {...{ [nativeAppScrollAttribute]: "" }}>
      <div className={nativeAppContentClassName}>{children}</div>
    </div>
  );
}

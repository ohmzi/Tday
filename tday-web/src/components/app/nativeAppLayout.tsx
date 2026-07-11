import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export const nativeAppHorizontalPaddingClassName = "px-4 sm:px-6 lg:px-10";

export const nativeAppScrollClassName = cn(
  "h-full w-full overflow-y-auto scrollbar-none pb-[calc(7rem+env(safe-area-inset-bottom))] pt-4 sm:pt-6",
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

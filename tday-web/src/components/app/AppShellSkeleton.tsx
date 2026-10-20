import { Skeleton } from "@/components/ui/skeleton";
import { TaskRowSkeletonGroup } from "@/components/ui/TaskRowSkeleton";
import {
  nativeAppContentClassName,
  nativeAppHorizontalPaddingClassName,
} from "./nativeAppLayout";
import { cn } from "@/lib/utils";

/**
 * Route-level skeleton shown while lazy chunks load.
 * Renders a shell that matches the NativeAppShell layout: header + content + dock.
 *
 * This is the one loading surface on web that does not crossfade, and the reason is
 * structural rather than a decision: `useSkeletonCrossfade` holds a placeholder on screen
 * past the frame a `loading` flag flips, and there is no such flag here. React swaps a
 * Suspense fallback for the resolved chunk without rendering this component again, so it
 * never gets the frame it would need to fade itself out. What it can do instead — and what
 * it now does — is promise the SHAPE of the row the route is about to deliver: flat, flush,
 * and the height the real one stands at. Not its position. One shell stands in for every
 * lazy route, so its 56 px header is a stand-in for headers it cannot know — the root feed's
 * is a 64 px toolbar over a 78 px hero block below the safe-area inset, which puts the
 * column about 100 px lower than this draws it, and the gutters differ by 4 px besides.
 * Closing that would mean a shell per route. What is fixed here is the half that was a
 * claim about the row itself.
 *
 * One thing it no longer gets wrong is the WIDTH. The skeleton used to be as wide as the
 * window while every real screen is a `max-w-6xl` column centred in it, so on a desktop the
 * shimmer ran edge to edge and then snapped inward as the real UI arrived (issue: "the
 * shimmer should happen exactly where the actual ui is"). It is drawn inside the same
 * centred column and horizontal gutters as `NativeAppPageLayout` now, so the only thing that
 * changes on hand-off is what is inside those bounds.
 */
export default function AppShellSkeleton() {
  return (
    <div className="flex h-screen w-full flex-col bg-background">
      <div
        className={cn(
          nativeAppContentClassName,
          nativeAppHorizontalPaddingClassName,
          "flex min-h-0 flex-1 flex-col",
        )}
      >
        {/* Header placeholder */}
        <div className="flex h-14 shrink-0 items-center justify-between pt-[env(safe-area-inset-top)]">
          <Skeleton className="h-7 w-24 rounded-full bg-muted" />
          <div className="flex gap-2">
            <Skeleton className="h-10 w-10 rounded-full bg-muted" />
            <Skeleton className="h-10 w-10 rounded-full bg-muted" />
          </div>
        </div>

        {/* Content placeholder */}
        <div className="flex-1 space-y-4 pt-4">
          {/* Hero tile */}
          <Skeleton className="h-[70px] rounded-[26px] bg-muted" />
          {/* Task rows, drawn by the feed's own placeholder rather than by this file's guess
              at it. The three 62 px `rounded-2xl` cards that stood here were exactly the
              shape `TaskRowSkeleton` was written to retire — the real row is flat,
              transparent and borderless — and a cold start is the first time most users
              ever see the mismatch. */}
          <TaskRowSkeletonGroup count={3} />
          {/* Category tiles */}
          <div className="grid grid-cols-2 gap-2.5 pt-2">
            <Skeleton className="h-[94px] rounded-[26px] bg-muted/60" />
            <Skeleton className="h-[94px] rounded-[26px] bg-muted/60" />
            <Skeleton className="h-[94px] rounded-[26px] bg-muted/60" />
            <Skeleton className="h-[94px] rounded-[26px] bg-muted/60" />
          </div>
        </div>
      </div>

      {/* Dock placeholder — centred in the window, exactly as the real dock is, so it does
          not move when the column above it hands over. */}
      <div className="flex shrink-0 justify-center pb-[calc(18px+env(safe-area-inset-bottom))]">
        <div className="h-16 w-44 animate-pulse rounded-[25px] bg-muted/60" />
      </div>
    </div>
  );
}

import { Skeleton } from "@/components/ui/skeleton";
import { TaskRowSkeletonGroup } from "@/components/ui/TaskRowSkeleton";

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
 */
export default function AppShellSkeleton() {
  return (
    <div className="flex h-screen w-full flex-col bg-background">
      {/* Header placeholder */}
      <div className="flex h-14 items-center justify-between px-5 pt-[env(safe-area-inset-top)]">
        <Skeleton className="h-7 w-24 rounded-full bg-muted" />
        <div className="flex gap-2">
          <Skeleton className="h-10 w-10 rounded-full bg-muted" />
          <Skeleton className="h-10 w-10 rounded-full bg-muted" />
        </div>
      </div>

      {/* Content placeholder */}
      <div className="flex-1 space-y-4 px-5 pt-4">
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

      {/* Dock placeholder */}
      <div className="flex justify-center pb-[calc(18px+env(safe-area-inset-bottom))]">
        <div className="h-16 w-44 animate-pulse rounded-[25px] bg-muted/60" />
      </div>
    </div>
  );
}

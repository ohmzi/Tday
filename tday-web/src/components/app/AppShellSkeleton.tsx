/**
 * Route-level skeleton shown while lazy chunks load.
 * Renders a shell that matches the NativeAppShell layout: header + content + dock.
 */
export default function AppShellSkeleton() {
  return (
    <div className="flex h-screen w-full flex-col bg-background">
      {/* Header placeholder */}
      <div className="flex h-14 items-center justify-between px-5 pt-[env(safe-area-inset-top)]">
        <div className="h-7 w-24 animate-pulse rounded-full bg-muted" />
        <div className="flex gap-2">
          <div className="h-10 w-10 animate-pulse rounded-full bg-muted" />
          <div className="h-10 w-10 animate-pulse rounded-full bg-muted" />
        </div>
      </div>

      {/* Content placeholder */}
      <div className="flex-1 space-y-4 px-5 pt-4">
        {/* Hero tile */}
        <div className="h-[70px] animate-pulse rounded-[26px] bg-muted" />
        {/* Task rows */}
        <div className="space-y-2.5">
          <div className="h-[62px] animate-pulse rounded-2xl bg-muted/70" />
          <div className="h-[62px] animate-pulse rounded-2xl bg-muted/70" />
          <div className="h-[62px] animate-pulse rounded-2xl bg-muted/70" />
        </div>
        {/* Category tiles */}
        <div className="grid grid-cols-2 gap-2.5 pt-2">
          <div className="h-[94px] animate-pulse rounded-[26px] bg-muted/60" />
          <div className="h-[94px] animate-pulse rounded-[26px] bg-muted/60" />
          <div className="h-[94px] animate-pulse rounded-[26px] bg-muted/60" />
          <div className="h-[94px] animate-pulse rounded-[26px] bg-muted/60" />
        </div>
      </div>

      {/* Dock placeholder */}
      <div className="flex justify-center pb-[calc(18px+env(safe-area-inset-bottom))]">
        <div className="h-16 w-44 animate-pulse rounded-[25px] bg-muted/60" />
      </div>
    </div>
  );
}

/** Reusable skeleton primitives for native-like loading states. */

export function TaskRowSkeleton({ count = 3 }: { count?: number }) {
  return (
    <div className="space-y-2">
      {Array.from({ length: count }, (_, i) => (
        <div
          key={i}
          className="flex h-[62px] items-center gap-3 rounded-2xl bg-muted/70 px-4"
        >
          <div className="h-5 w-5 animate-pulse rounded-full bg-muted-foreground/15" />
          <div className="flex-1 space-y-1.5">
            <div className="h-3.5 w-3/5 animate-pulse rounded-full bg-muted-foreground/12" />
            <div className="h-2.5 w-2/5 animate-pulse rounded-full bg-muted-foreground/8" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function TodaySkeleton() {
  return (
    <div className="flex w-full flex-col gap-4 sm:gap-5">
      {/* Header */}
      <div className="flex min-h-14 items-center justify-between gap-3">
        <div className="h-7 w-24 animate-pulse rounded-full bg-muted" />
        <div className="flex gap-2">
          <div className="h-14 w-14 animate-pulse rounded-full bg-muted" />
          <div className="h-14 w-14 animate-pulse rounded-full bg-muted" />
          <div className="h-14 w-14 animate-pulse rounded-full bg-muted" />
        </div>
      </div>
      {/* Hero tile */}
      <div className="h-[70px] animate-pulse rounded-[26px] bg-muted" />
      {/* Today tasks */}
      <div className="space-y-1">
        <div className="h-8 w-20 animate-pulse rounded-full bg-muted px-1" />
        <TaskRowSkeleton count={3} />
      </div>
      {/* Category grid */}
      <div className="grid grid-cols-2 gap-2.5">
        {Array.from({ length: 6 }, (_, i) => (
          <div key={i} className="h-[94px] animate-pulse rounded-[26px] bg-muted/60" />
        ))}
      </div>
    </div>
  );
}

export function FloaterListSkeleton() {
  return (
    <div className="space-y-4">
      {/* Title */}
      <div className="h-8 w-40 animate-pulse rounded-full bg-muted" />
      {/* Search */}
      <div className="h-12 animate-pulse rounded-2xl bg-muted/60" />
      {/* Items */}
      <TaskRowSkeleton count={4} />
    </div>
  );
}

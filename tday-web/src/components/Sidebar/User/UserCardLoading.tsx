import React from "react";
import { Skeleton } from "@/components/ui/skeleton";

const railSkeletonClass =
  "h-10 w-10 shrink-0 rounded-sm border border-border/40 bg-border/70";

/**
 * The sidebar's account block before the user is known.
 *
 * It was four static blocks: the one loading state in the app that did not move. A
 * placeholder that never pulses does not read as "on its way", it reads as a control that
 * has finished rendering empty — which is the thing the sidebar's two real buttons would
 * look like if they had failed. `Skeleton` is the whole fix; the geometry below is
 * unchanged, and the classes still override its own rounding and fill so the blocks keep
 * the rail's 12 px corners rather than the primitive's default.
 */
const UserCardLoading = ({ collapsed = false }: { collapsed?: boolean }) => {
  if (collapsed) {
    return (
      <div className="flex flex-col gap-2">
        <Skeleton className={railSkeletonClass} />
        <Skeleton className={railSkeletonClass} />
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <Skeleton className="h-10 w-full rounded-sm border border-border/40 bg-border/70" />
      <Skeleton className="h-10 w-full rounded-sm border border-border/40 bg-border/70" />
    </div>
  );
};

export default UserCardLoading;

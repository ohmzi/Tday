import React from "react";

const railSkeletonClass =
  "h-10 w-10 shrink-0 rounded-xl border border-border/40 bg-border/70";

const UserCardLoading = ({ collapsed = false }: { collapsed?: boolean }) => {
  if (collapsed) {
    return (
      <div className="flex flex-col gap-2">
        <div className={railSkeletonClass} />
        <div className={railSkeletonClass} />
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <div className="h-10 w-full rounded-xl border border-border/40 bg-border/70" />
      <div className="h-10 w-full rounded-xl border border-border/40 bg-border/70" />
    </div>
  );
};

export default UserCardLoading;

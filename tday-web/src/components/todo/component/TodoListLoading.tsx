import React from "react";
import { TaskRowSkeletonGroup } from "@/components/ui/TaskRowSkeleton";
import { cn } from "@/lib/utils";

type TodoListLoadingProps = {
  className?: string;
  heading?: string;
};

/**
 * A feed waiting for its tasks: the caller's own title, then rows at the real rows' shape.
 *
 * The heading is drawn rather than blocked out because it is already known — the caller
 * passes it — and a bar of grey standing in for a word already in hand is a placeholder for
 * nothing. It is not, though, the section header the tasks land under, and the comment that
 * said so was wrong: the sections spell theirs `text-2xl font-black` at `mt-2` / `mb-1.5`
 * (`timelineDndClasses.ts`), this block is `text-lg font-semibold` at `mt-5` / `mb-4 mt-1`,
 * and the difference measures 18 px of vertical step at the handover. On the one caller that
 * passes a heading the text is the page title `NativePageHeader` has already drawn an inch
 * above, so nothing replaces this h3 in place — the sections' own labels arrive instead.
 *
 * That step is older than the row geometry below it and is not the row geometry's to close,
 * so it is named in the Phase 9 device pass rather than left for a human to discover and
 * report as the defect this file just fixed. What the rows look like is
 * `TaskRowSkeleton`'s argument.
 */
const TodoListLoading = ({ className, heading }: TodoListLoadingProps) => {
  return (
    <div className={cn("mt-5", className)}>
      {heading && (
        <div className="mb-4 mt-1 flex items-center gap-2">
          <h3 className="select-none text-lg font-semibold tracking-tight">
            {heading}
          </h3>
          <div className="h-px flex-1 bg-border/70" />
        </div>
      )}
      <TaskRowSkeletonGroup count={3} />
    </div>
  );
};

export default TodoListLoading;

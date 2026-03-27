import React from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

type TodoListLoadingProps = {
  className?: string;
  heading?: string;
};

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
      <div className="space-y-3">
        {Array.from({ length: 3 }).map((_, index) => (
          <div
            key={`todo-loading-${index}`}
            className="rounded-2xl border border-border/65 bg-card/95 px-3 py-3 shadow-[0_1px_2px_hsl(var(--shadow)/0.08)]"
          >
            <div className="flex items-start gap-3">
              <div className="mt-1 h-5 w-5 rounded-full border-2 border-border/70" />
              <div className="min-w-0 flex-1">
                <Skeleton className="mb-3 h-6 w-1/2" />
                <Skeleton className="mb-2 h-4 w-[92%]" />
                <Skeleton className="mb-3 h-4 w-[65%]" />
                <div className="flex items-center gap-2">
                  <Skeleton className="h-6 w-24 rounded-full" />
                  <Skeleton className="h-6 w-16 rounded-full" />
                </div>
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default TodoListLoading;

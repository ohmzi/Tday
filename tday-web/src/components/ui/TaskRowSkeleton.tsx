import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

/**
 * A task row that has not arrived yet, drawn at the geometry of the one that will.
 *
 * The classes below are `TodoItemContainer`'s own — `px-1 py-2.5 sm:rounded-lg` on a
 * transparent row, the `shrink-0` leading slot, the column beside it — rather than a second
 * set tuned to look like them. That is the rule `FormBodyPlaceholder` states for the sheet
 * surfaces, and the feed is where it was most needed: the skeleton this replaced drew a
 * `rounded-2xl` bordered card with `px-3 py-3` and a shadow, which the flat native-style row
 * has never been. Every real row landing moved the page.
 *
 * One class is deliberately not copied, and it is argued at the group below: the real row is
 * width-driven by the text it holds, and this one holds none.
 *
 * The group stacks on `space-y-0` because `TodoGroup` does. A gapped skeleton is the same bug
 * a second time: three rows' worth of gap disappears in the frame the content lands, and the
 * feed reflows under whatever the user was already reaching for.
 */
export function TaskRowSkeleton({ titleWidth = "w-1/2" }: { titleWidth?: string }) {
  return (
    <div className="relative z-10 flex items-center justify-between gap-3 px-1 py-2.5 sm:rounded-lg">
      {/* `flex-1` here and on the column is the one place this stops copying the row, and
          the reason is that a bar is not text. The real column is shrink-to-fit and its
          content sizes it; a percentage width contributes nothing to intrinsic sizing, so
          copying `max-w-full` alone left the column standing on its widest real child — the
          96 px meta bar — and every title bar rendered as a fraction of that (48 px for
          `w-1/2`, against the 188 px a four-word title measures). Stretching the column is
          what makes `titleWidth` mean the thing its name says. The row's own box is
          untouched by it: there is no second child to take space from. */}
      <div className="flex min-w-0 flex-1 items-start gap-3">
        {/* The 20 px round complete toggle's slot, at its size and not at its border. */}
        <div className="shrink-0">
          <Skeleton className="h-5 w-5 rounded-full" />
        </div>
        <div className="min-w-0 max-w-full flex-1">
          {/* The title's own box: `leading-5` tall, with the `mb-1.5` the real row puts
              between the title line and everything under it, so the meta bar below sits
              where the due time will. */}
          <Skeleton className={cn("mb-1.5 h-5", titleWidth)} />
          {/* One meta bar, because one `text-xs` due time is what the row actually has.
              Notes are optional and usually absent — drawing them would be inventing a
              taller row than most of the feed is about to be. */}
          <Skeleton className="h-4 w-24" />
        </div>
      </div>
    </div>
  );
}

/**
 * Three title widths, cycled, so a stack of rows does not read as a table. The real feed's
 * titles are not one length; a column of identical bars says "grid", which is the one thing
 * the thing arriving is not.
 */
const TITLE_WIDTHS = ["w-1/2", "w-2/3", "w-2/5"];

export function TaskRowSkeletonGroup({
  count = 3,
  className,
}: {
  count?: number;
  className?: string;
}) {
  return (
    // No copy: there is nothing to say about a feed in flight that the user can act on, so
    // `aria-busy` is the whole announcement — the same call `FormBodyPlaceholder` makes.
    <div className={cn("space-y-0", className)} aria-busy="true">
      {Array.from({ length: count }).map((_, index) => (
        <TaskRowSkeleton
          key={`task-row-skeleton-${index}`}
          titleWidth={TITLE_WIDTHS[index % TITLE_WIDTHS.length]}
        />
      ))}
    </div>
  );
}

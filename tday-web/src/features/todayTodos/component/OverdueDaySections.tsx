import { cn } from "@/lib/utils";
import TodoGroup from "@/components/todo/component/TodoGroup";
import {
  headerToBodyGap,
  sectionTopGapFilled,
  sectionTopGapFirst,
} from "@/components/todo/dnd/timelineDndClasses";
import { getTodoDateSectionId } from "@/lib/todoToastNavigation";
import type { TimelineSection } from "./AllTasksTimelineContainer";

/**
 * The standalone Overdue screen's two day-grouped blocks: today's own
 * still-pending tasks (`regularSections`, `dayDiff >= 0`) above the
 * genuinely overdue days (`earlierSections`, `dayDiff < 0`) — see
 * `AllTasksTimelineContainer`'s `sections`/`earlierSections`/
 * `regularSections` split (via `useTimelinePaging`) for how these are built.
 * Unchanged by this PR — pulled out verbatim to keep the container's own
 * complexity down.
 */
export default function OverdueDaySections({
  regularSections,
  earlierSections,
  focusedDateKey,
  focusedTaskId,
}: {
  regularSections: TimelineSection[];
  earlierSections: TimelineSection[];
  focusedDateKey: string | null;
  focusedTaskId: string | null;
}) {
  return (
    <>
      {regularSections.map((section) => (
        <section
          id={getTodoDateSectionId(section.key)}
          key={section.key}
          className={cn(
            "scroll-mt-24",
            section.dayDiff === 0 ? sectionTopGapFirst : sectionTopGapFilled,
          )}
        >
          <div className={cn(headerToBodyGap, "flex items-center gap-2")}>
            <h3
              className={cn(
                "select-none text-2xl font-black tracking-tight",
                focusedDateKey === section.key ? "text-accent" : "text-muted-foreground",
              )}
            >
              {section.label}
            </h3>
          </div>
          <TodoGroup
            todos={section.todos}
            overdue={section.dayDiff < 0}
            perTaskOverdue={section.dayDiff === 0}
            highlightedTodoId={focusedTaskId}
            showOverdueTag={false}
            className="border-b border-border/60 pb-1"
          />
        </section>
      ))}

      {earlierSections.map((section) => (
        <section
          id={getTodoDateSectionId(section.key)}
          key={section.key}
          className={cn("scroll-mt-24", sectionTopGapFilled)}
        >
          <div className={cn(headerToBodyGap, "flex items-center gap-2")}>
            <h3
              className={cn(
                "select-none text-2xl font-black tracking-tight",
                focusedDateKey === section.key ? "text-accent" : "text-muted-foreground",
              )}
            >
              {section.label}
            </h3>
          </div>
          <TodoGroup
            todos={section.todos}
            overdue={true}
            highlightedTodoId={focusedTaskId}
            showOverdueTag={false}
            className="border-b border-border/60 pb-1"
          />
        </section>
      ))}
    </>
  );
}

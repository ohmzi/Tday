import { ChevronDown, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";
import TodoGroup from "@/components/todo/component/TodoGroup";
import { headerToBodyGap, sectionTopGapFilled } from "@/components/todo/dnd/timelineDndClasses";
import { TodoItemType } from "@/types";

/**
 * Today's own collapsible "Earlier" bucket (requirement 2): the same overdue
 * tasks the All/Priority/Scheduled timelines already collect into their own
 * "Earlier" section (`buildTimelineSections`'s `kind: "earlier"`), tucked
 * under Today, collapsed by default.
 *
 * Deliberately NOT wired into the drag-and-drop timeline machinery
 * (`TimelineSections`/`TimelineSectionDroppable`) those scopes use for their
 * own Earlier bucket — that machinery registers a dnd-kit `useDroppable`
 * inside `TimelineDndContext`, and Today already runs its own, differently-
 * shaped drag context (`TodayBucketDndContext`, for the Morning/Afternoon/
 * Tonight time-of-day buckets). Nesting a second droppable inside it would
 * register "earlier" as a drop target under the wrong context, so a task
 * dropped on it would be read against `TodayBucketDndContext`'s bucket/hour
 * shape instead of a day target. This reuses that other component's header
 * markup and spacing classes instead, so it reads as the same kind of
 * section, just without reschedule-by-drag.
 */
export default function TodayEarlierSection({
  todos,
  label,
  expanded,
  onToggle,
  highlightedTodoId,
}: {
  todos: TodoItemType[];
  label: string;
  expanded: boolean;
  onToggle: () => void;
  highlightedTodoId?: string | null;
}) {
  return (
    <section className={cn("scroll-mt-24 rounded-3xl px-1", sectionTopGapFilled)}>
      <button
        type="button"
        onClick={onToggle}
        className={cn(headerToBodyGap, "flex w-full items-center gap-2")}
      >
        {expanded ? (
          <ChevronDown className="h-4 w-4 text-muted-foreground" />
        ) : (
          <ChevronRight className="h-4 w-4 text-muted-foreground" />
        )}
        {/* Same translated `app.overdue` string the caller feeds
            `buildTimelineSections`'s own Earlier bucket label for
            All/Priority/List, so every scope's collapsible bucket reads the
            same word in whichever language the app is in. */}
        <h3 className="select-none text-2xl font-black tracking-tight text-muted-foreground">
          {label}
        </h3>
        <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
          {todos.length}
        </span>
      </button>

      {expanded && (
        <TodoGroup
          todos={todos}
          overdue
          highlightedTodoId={highlightedTodoId}
          showOverdueTag={false}
          className="border-b border-border/60 pb-1"
        />
      )}
    </section>
  );
}

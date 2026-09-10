import React from "react";
import { useDroppable } from "@dnd-kit/core";
import { ChevronDown, ChevronRight } from "lucide-react";
import { cn } from "@/lib/utils";
import { getTodoDateSectionId } from "@/lib/todoToastNavigation";
import type { TimelineSection } from "@/lib/timeline/buildTimelineSections";
import { useFadeUnmount } from "@/hooks/useFadeUnmount";
import { OVERDUE_ROWS_FADE_MS } from "@/features/todayTodos/lib/todayEarlierIllustration";
import { useTimelineOverSection } from "./TimelineDndContext";
import {
  headerActiveClass,
  headerToBodyGap,
  placeholderActiveClass,
  placeholderBaseClass,
  placeholderRestClass,
  sectionActiveClass,
  sectionTopGapEmpty,
  sectionTopGapFilled,
  sectionTopGapFirst,
} from "./timelineDndClasses";

function TimelineDropPlaceholder({ active }: { active: boolean }) {
  return (
    <div
      aria-hidden
      className={cn(placeholderBaseClass, active ? placeholderActiveClass : placeholderRestClass)}
    />
  );
}

/**
 * One date bucket of the timeline, registered as a drop target. An empty bucket
 * exists only for the length of a drag — see `buildTimelineSections`, which is
 * what stops a list of three overdue tasks drawing a dozen bare headers down to
 * December — so the dashed "drop here" slot below is only ever reachable with a
 * task in hand. The header turns destructive and the bucket highlights while a
 * valid drop hovers it.
 */
export default function TimelineSectionDroppable({
  section,
  focusedDateKey,
  collapsed = false,
  onToggleCollapse,
  children,
}: {
  section: TimelineSection;
  focusedDateKey?: string | null;
  collapsed?: boolean;
  onToggleCollapse?: () => void;
  children: React.ReactNode;
}) {
  const overSectionKey = useTimelineOverSection();
  const isActive = overSectionKey === section.key;
  const isEmpty = section.todos.length === 0;
  const showBody = !section.collapsible || !collapsed;

  // Only the collapsible bucket (Overdue/Earlier) ever toggles `showBody`; every
  // other section always passes `expanded: true` here — a permanent no-op for
  // this hook (`mounted` starts and stays `true`, nothing ever fades) — so
  // `bodyMounted`/the fade classes below are gated on `section.collapsible`
  // and every other section renders exactly as it always has, untouched. See
  // `useFadeUnmount`'s own doc comment.
  const bodyMounted = useFadeUnmount(showBody, OVERDUE_ROWS_FADE_MS);
  const showCollapsibleBody = section.collapsible ? bodyMounted : showBody;

  const { setNodeRef } = useDroppable({
    id: `section:${section.key}`,
    data: { sectionKey: section.key, targetDayKey: section.targetDayKey },
    disabled: section.targetDayKey == null,
  });

  const headingClass = cn(
    "select-none text-2xl font-black tracking-tight transition-colors duration-200",
    isActive
      ? headerActiveClass
      : focusedDateKey === section.key
        ? "text-accent"
        : "text-muted-foreground",
  );

  const sectionTopGap =
    section.dayDiff === 0
      ? sectionTopGapFirst
      : isEmpty
        ? sectionTopGapEmpty
        : sectionTopGapFilled;

  return (
    <section
      ref={setNodeRef}
      id={getTodoDateSectionId(section.key)}
      className={cn(
        "scroll-mt-24 rounded-3xl px-1 transition-all duration-200",
        sectionTopGap,
        isActive && sectionActiveClass,
      )}
    >
      {section.collapsible && onToggleCollapse ? (
        <button
          type="button"
          onClick={onToggleCollapse}
          className={cn(headerToBodyGap, "flex w-full items-center gap-2")}
        >
          {collapsed ? (
            <ChevronRight className="h-4 w-4 text-muted-foreground" />
          ) : (
            <ChevronDown className="h-4 w-4 text-muted-foreground" />
          )}
          <h3 className={headingClass}>{section.label}</h3>
          <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
            {section.todos.length}
          </span>
        </button>
      ) : (
        <div className={cn(headerToBodyGap, "flex items-center gap-2")}>
          <h3 className={headingClass}>{section.label}</h3>
        </div>
      )}

      {showCollapsibleBody &&
        (isEmpty ? (
          // Native shows empty dates as just the header — only reveal the
          // dashed drop slot while a drag is actively hovering this bucket.
          isActive ? <TimelineDropPlaceholder active /> : null
        ) : (
          <div
            className={cn(
              "space-y-0 border-b border-border/60 pb-1",
              // Only the collapsible bucket fades — see `bodyMounted` above.
              section.collapsible && (showBody ? "tday-rows-enter" : "tday-rows-exit"),
            )}
            style={
              section.collapsible
                ? { animationDuration: `${OVERDUE_ROWS_FADE_MS}ms` }
                : undefined
            }
          >
            {children}
            {isActive && <TimelineDropPlaceholder active />}
          </div>
        ))}
    </section>
  );
}

import React from "react";
import { useDroppable } from "@dnd-kit/core";
import { ChevronDown, ChevronRight } from "lucide-react";
import LineSeparator from "@/components/ui/lineSeparator";
import { cn } from "@/lib/utils";
import { getTodoDateSectionId } from "@/lib/todoToastNavigation";
import type { TimelineSection } from "@/lib/timeline/buildTimelineSections";
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
 * One date bucket of the timeline, registered as a drop target. Empty buckets
 * still render (as a dashed "drop here" slot) so every date is droppable,
 * matching native. The header turns destructive and the bucket highlights while
 * a valid drop hovers it.
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

  const { setNodeRef } = useDroppable({
    id: `section:${section.key}`,
    data: { sectionKey: section.key, targetDayKey: section.targetDayKey },
    disabled: section.targetDayKey == null,
  });

  const headingClass = cn(
    "select-none text-lg font-semibold tracking-tight transition-colors duration-200",
    isActive
      ? headerActiveClass
      : focusedDateKey === section.key && "text-accent",
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
          <LineSeparator className="flex-1 border-border/70" />
        </button>
      ) : (
        <div className={cn(headerToBodyGap, "flex items-center gap-2")}>
          <h3 className={headingClass}>{section.label}</h3>
          <LineSeparator className="flex-1 border-border/70" />
        </div>
      )}

      {showBody &&
        (isEmpty ? (
          <TimelineDropPlaceholder active={isActive} />
        ) : (
          <div className="space-y-2">
            {children}
            {isActive && <TimelineDropPlaceholder active />}
          </div>
        ))}
    </section>
  );
}

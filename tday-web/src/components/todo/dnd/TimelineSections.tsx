import { getTodoDayKey } from "@/lib/todoToastNavigation";
import type { TimelineSection } from "@/lib/timeline/buildTimelineSections";
import TimelineDndContext from "./TimelineDndContext";
import TimelineSectionDroppable from "./TimelineSectionDroppable";
import DraggableTodoItem from "./DraggableTodoItem";

/**
 * Renders a full date-bucketed timeline with cross-date drag-and-drop. Shared by
 * the All / Priority / Scheduled screens and the custom-list screen. Must be
 * rendered inside a `TodoMutationProvider` (the reschedule writes through
 * whichever mutation hooks that provider supplies).
 */
export default function TimelineSections({
  sections,
  timeZone,
  focusedTaskId,
  focusedDateKey,
  earlierExpanded,
  onToggleEarlier,
}: {
  sections: TimelineSection[];
  timeZone?: string;
  focusedTaskId?: string | null;
  focusedDateKey?: string | null;
  earlierExpanded: boolean;
  onToggleEarlier: () => void;
}) {
  return (
    <TimelineDndContext timeZone={timeZone}>
      {sections.map((section) => (
        <TimelineSectionDroppable
          key={section.key}
          section={section}
          focusedDateKey={focusedDateKey}
          collapsed={section.collapsible ? !earlierExpanded : false}
          onToggleCollapse={section.collapsible ? onToggleEarlier : undefined}
        >
          {section.todos.map((todo) => (
            <DraggableTodoItem
              key={todo.id}
              todo={todo}
              currentDayKey={getTodoDayKey(todo.due, timeZone)}
              highlighted={focusedTaskId === todo.id}
              perTaskOverdue={section.dayDiff === 0}
              overdue={section.kind === "earlier"}
            />
          ))}
        </TimelineSectionDroppable>
      ))}
    </TimelineDndContext>
  );
}

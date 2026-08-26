import { useLayoutEffect, useRef } from "react";
import { useDndMonitor } from "@dnd-kit/core";
import { nativeAppScrollAttribute } from "@/components/app/nativeAppLayout";
import { getTodoDayKey } from "@/lib/todoToastNavigation";
import type { TimelineSection } from "@/lib/timeline/buildTimelineSections";
import TimelineDndContext from "./TimelineDndContext";
import TimelineSectionDroppable from "./TimelineSectionDroppable";
import DraggableTodoItem from "./DraggableTodoItem";

/**
 * The drag state lives inside `TimelineDndContext`, but the sections are built
 * one level above it — the empty date buckets only exist while a drag needs
 * somewhere to drop — so it has to be reported back out to whoever calls
 * `buildTimelineSections`. `useDndMonitor` only works from inside the context,
 * hence this zero-render bridge.
 */
function DragActivityBridge({ onChange }: { onChange: (active: boolean) => void }) {
  // What the scroller measured just before the buckets mounted, so the rows can
  // be put back where the finger left them. Chrome and Firefox do this for us
  // through CSS scroll anchoring; WebKit implements no `overflow-anchor` at all,
  // and the installed iOS PWA is exactly where this screen is used — so without
  // compensation, long-pressing a task in a later month inserts every bucket
  // above it and the whole list, the picked-up row included, jumps down by their
  // height at the moment of the lift.
  const anchor = useRef<{ scroller: HTMLElement; top: number; height: number } | null>(null);

  useDndMonitor({
    onDragStart: () => {
      const scroller = document.querySelector<HTMLElement>(`[${nativeAppScrollAttribute}]`);
      if (scroller) {
        anchor.current = {
          scroller,
          top: scroller.scrollTop,
          height: scroller.scrollHeight,
        };
      }
      onChange(true);
    },
    onDragEnd: () => onChange(false),
    onDragCancel: () => onChange(false),
  });

  // Layout effect, not an effect: this has to land in the same frame the buckets
  // are painted in, or the jump is visible before it is corrected.
  useLayoutEffect(() => {
    const pending = anchor.current;
    anchor.current = null;
    if (!pending) return;
    const grew = pending.scroller.scrollHeight - pending.height;
    // Only ever pushes the content back down by what was inserted, and only when
    // the browser has not already done it — on Chrome `grew` is real but
    // scrollTop has already moved, so the difference is nil and this is a no-op.
    if (grew > 0 && pending.scroller.scrollTop === pending.top) {
      pending.scroller.scrollTop = pending.top + grew;
    }
  });

  return null;
}

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
  onDragActiveChange,
}: {
  sections: TimelineSection[];
  timeZone?: string;
  focusedTaskId?: string | null;
  focusedDateKey?: string | null;
  earlierExpanded: boolean;
  onToggleEarlier: () => void;
  /** Drives `includeEmptyDropTargets` on the caller's `buildTimelineSections`. */
  onDragActiveChange: (active: boolean) => void;
}) {
  return (
    <TimelineDndContext timeZone={timeZone}>
      <DragActivityBridge onChange={onDragActiveChange} />
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

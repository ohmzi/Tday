import React, { createContext, useCallback, useContext, useEffect, useState } from "react";
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  pointerWithin,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragOverEvent,
  type DragStartEvent,
} from "@dnd-kit/core";
import { format } from "date-fns";
import { TodoItemType } from "@/types";
import { useTimelineReschedule } from "./useTimelineReschedule";
import { overlayCardClass } from "./timelineDndClasses";
import { hapticDragStart, hapticDragOver, hapticDrop } from "@/lib/haptics";

export type TimelineDraggableData = {
  todo: TodoItemType;
  currentDayKey: string;
};

export type TimelineDroppableData = {
  sectionKey: string;
  targetDayKey: string | null;
};

// Lets a deeply-nested section read "am I the active drop target?" without prop
// drilling through the section list.
const OverSectionContext = createContext<string | null>(null);
export const useTimelineOverSection = () => useContext(OverSectionContext);

// Legacy wrapper removed — now using @/lib/haptics directly.

/**
 * Single drag-and-drop context wrapping every section of a timeline screen.
 * Tasks are draggable; sections are droppable. Dropping a task onto a section
 * reschedules it to that section's `targetDayKey` (preserving time-of-day),
 * mirroring the native "drag a task to another date" interaction.
 */
export default function TimelineDndContext({
  timeZone,
  children,
}: {
  timeZone?: string;
  children: React.ReactNode;
}) {
  const reschedule = useTimelineReschedule(timeZone);
  const [activeTodo, setActiveTodo] = useState<TodoItemType | null>(null);
  const [overSectionKey, setOverSectionKey] = useState<string | null>(null);

  // Lock page scroll while a drag is active so the user can move the card
  // without the page sliding underneath.
  useEffect(() => {
    if (!activeTodo) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, [activeTodo]);

  // Mirror the calendar/TodoGroup sensor config: a small mouse threshold keeps
  // clicks/double-clicks intact, and the touch delay lets a tap or scroll win
  // over a drag until the user presses and holds (native "long-press to lift").
  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 5 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 5 } }),
    useSensor(KeyboardSensor),
  );

  const clear = useCallback(() => {
    setActiveTodo(null);
    setOverSectionKey(null);
  }, []);

  const handleDragStart = useCallback((event: DragStartEvent) => {
    const data = event.active.data.current as TimelineDraggableData | undefined;
    setActiveTodo(data?.todo ?? null);
    hapticDragStart();
  }, []);

  const handleDragOver = useCallback(
    (event: DragOverEvent) => {
      const over = event.over?.data.current as TimelineDroppableData | undefined;
      const active = event.active.data.current as TimelineDraggableData | undefined;
      if (
        !over ||
        over.targetDayKey == null ||
        over.targetDayKey === active?.currentDayKey
      ) {
        setOverSectionKey(null);
        return;
      }
      setOverSectionKey(over.sectionKey);
      hapticDragOver();
    },
    [],
  );

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const active = event.active.data.current as TimelineDraggableData | undefined;
      const over = event.over?.data.current as TimelineDroppableData | undefined;
      clear();
      if (!active?.todo || !over) return;
      const { targetDayKey } = over;
      if (targetDayKey == null || targetDayKey === active.currentDayKey) return;
      hapticDrop();
      reschedule(active.todo, targetDayKey);
    },
    [clear, reschedule],
  );

  return (
    <DndContext
      sensors={sensors}
      collisionDetection={pointerWithin}
      onDragStart={handleDragStart}
      onDragOver={handleDragOver}
      onDragEnd={handleDragEnd}
      onDragCancel={clear}
    >
      <OverSectionContext.Provider value={overSectionKey}>
        {children}
      </OverSectionContext.Provider>
      <DragOverlay dropAnimation={null}>
        {activeTodo ? (
          <div className={overlayCardClass}>
            <p className="line-clamp-1 text-[0.98rem] font-black leading-5 text-foreground">
              {activeTodo.title}
            </p>
            <span className="mt-1 inline-flex rounded-full border border-border/70 bg-muted/70 px-2 py-[0.2rem] text-xs font-black text-foreground/80">
              {format(activeTodo.due, "h:mm a")}
            </span>
          </div>
        ) : null}
      </DragOverlay>
    </DndContext>
  );
}

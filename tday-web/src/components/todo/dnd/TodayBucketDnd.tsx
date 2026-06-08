import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  pointerWithin,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragOverEvent,
  type DragStartEvent,
} from "@dnd-kit/core";
import { format } from "date-fns";
import { cn } from "@/lib/utils";
import { TodoItemType } from "@/types";
import { TodoItemCard } from "@/components/todo/component/TodoItemContainer";
import { useTodoMutation } from "@/providers/TodoMutationProvider";
import { TodoItemTypeWithDateChecksum } from "@/features/todayTodos/query/update-todo";
import { setTodoTimeOfDay } from "@/lib/setTodoTimeOfDay";
import { hapticDragStart, hapticDragOver, hapticDrop } from "@/lib/haptics";
import {
  headerActiveClass,
  headerToBodyGap,
  overlayCardClass,
  placeholderActiveClass,
  placeholderBaseClass,
  placeholderRestClass,
  sectionActiveClass,
  sectionTopGapFilled,
  sectionTopGapFirst,
} from "./timelineDndClasses";

export type TodayBucketLabel = "Morning" | "Afternoon" | "Tonight";

// The three Today time buckets and the canonical hour a task is set to when
// dropped into each. Hours land unambiguously inside the display boundaries
// (Morning < 12, Afternoon 12–18, Tonight ≥ 18), so a dropped task re-renders in
// the bucket it was dropped on. Mirrors the iOS/Android mapping.
export const TODAY_BUCKETS: { label: TodayBucketLabel; targetHour: number }[] = [
  { label: "Morning", targetHour: 9 },
  { label: "Afternoon", targetHour: 15 },
  { label: "Tonight", targetHour: 20 },
];

type TodayDraggableData = { todo: TodoItemType; currentBucket: TodayBucketLabel };
type TodayDroppableData = { bucket: TodayBucketLabel; targetHour: number };

/**
 * "Set a task's time-of-day to a bucket" — the Today-screen sibling of
 * `useTimelineReschedule` (which moves across dates). Keeps the date, changes the
 * time, and writes through whichever mutation hooks the surrounding
 * `TodoMutationProvider` supplies. Recurring instances patch just the instance.
 */
function useTodayBucketReschedule(timeZone?: string) {
  const { useEditTodo, useEditTodoInstance } = useTodoMutation();
  const { editTodoMutateFn } = useEditTodo();
  const { editTodoInstanceMutateFn } = useEditTodoInstance(undefined);

  return useCallback(
    (todo: TodoItemType, targetHour: number) => {
      const nextRange = setTodoTimeOfDay(todo, targetHour, timeZone);

      if (todo.rrule && todo.instanceDate) {
        editTodoInstanceMutateFn({
          ...todo,
          instanceDate: todo.instanceDate ?? todo.due,
          ...nextRange,
        });
        return;
      }

      editTodoMutateFn({
        ...(todo as TodoItemTypeWithDateChecksum),
        ...nextRange,
        dateRangeChecksum: todo.due.toISOString(),
        rruleChecksum: todo.rrule,
      });
    },
    [editTodoInstanceMutateFn, editTodoMutateFn, timeZone],
  );
}

// Lets a bucket read "am I the active drop target?" without prop drilling.
const OverBucketContext = createContext<TodayBucketLabel | null>(null);
const useTodayOverBucket = () => useContext(OverBucketContext);

/**
 * Wraps the Today screen's Morning / Afternoon / Tonight buckets in one drag-and-
 * drop context: tasks are draggable, buckets are droppable. Dropping a task onto a
 * different bucket sets its due time to that part of the day (date unchanged),
 * mirroring the native "drag a task to another time of day" interaction. Must be
 * rendered inside a `TodoMutationProvider`.
 */
export function TodayBucketDndContext({
  timeZone,
  children,
}: {
  timeZone?: string;
  children: React.ReactNode;
}) {
  const reschedule = useTodayBucketReschedule(timeZone);
  const [activeTodo, setActiveTodo] = useState<TodoItemType | null>(null);
  const [overBucket, setOverBucket] = useState<TodayBucketLabel | null>(null);

  // Lock page scroll while a drag is active so the card moves without the page
  // sliding underneath.
  useEffect(() => {
    if (!activeTodo) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, [activeTodo]);

  // Mirror the timeline/calendar sensor config: a small mouse threshold keeps
  // clicks intact, and the touch delay lets a tap or scroll win until press-hold.
  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 5 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 5 } }),
    useSensor(KeyboardSensor),
  );

  const clear = useCallback(() => {
    setActiveTodo(null);
    setOverBucket(null);
  }, []);

  const handleDragStart = useCallback((event: DragStartEvent) => {
    const data = event.active.data.current as TodayDraggableData | undefined;
    setActiveTodo(data?.todo ?? null);
    hapticDragStart();
  }, []);

  const handleDragOver = useCallback((event: DragOverEvent) => {
    const over = event.over?.data.current as TodayDroppableData | undefined;
    const active = event.active.data.current as TodayDraggableData | undefined;
    if (!over || !active || over.bucket === active.currentBucket) {
      setOverBucket(null);
      return;
    }
    setOverBucket(over.bucket);
    hapticDragOver();
  }, []);

  const handleDragEnd = useCallback(
    (event: DragEndEvent) => {
      const active = event.active.data.current as TodayDraggableData | undefined;
      const over = event.over?.data.current as TodayDroppableData | undefined;
      clear();
      if (!active?.todo || !over) return;
      if (over.bucket === active.currentBucket) return;
      hapticDrop();
      reschedule(active.todo, over.targetHour);
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
      <OverBucketContext.Provider value={overBucket}>
        {children}
      </OverBucketContext.Provider>
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

function TodayDropPlaceholder({ active }: { active: boolean }) {
  return (
    <div
      aria-hidden
      className={cn(placeholderBaseClass, active ? placeholderActiveClass : placeholderRestClass)}
    />
  );
}

/**
 * One Today time bucket, registered as a drop target. Empty buckets keep their
 * header (matching the existing Today layout) and only reveal the dashed drop slot
 * while a valid drop hovers them.
 */
export function TodayBucketDroppable({
  bucket,
  targetHour,
  isFirst,
  children,
}: {
  bucket: TodayBucketLabel;
  targetHour: number;
  isFirst: boolean;
  children: React.ReactNode;
}) {
  const overBucket = useTodayOverBucket();
  const isActive = overBucket === bucket;
  const isEmpty = !children || (Array.isArray(children) && children.length === 0);

  const droppableData: TodayDroppableData = { bucket, targetHour };
  const { setNodeRef } = useDroppable({
    id: `today-bucket:${bucket}`,
    data: droppableData,
  });

  return (
    <section
      ref={setNodeRef}
      className={cn(
        "scroll-mt-24 rounded-3xl px-1 transition-all duration-200",
        isFirst ? sectionTopGapFirst : sectionTopGapFilled,
        isActive && sectionActiveClass,
      )}
    >
      <div className={cn(headerToBodyGap, "flex items-center gap-2")}>
        <h3
          className={cn(
            "select-none text-2xl font-black tracking-tight transition-colors duration-200",
            isActive ? headerActiveClass : "text-muted-foreground",
          )}
        >
          {bucket}
        </h3>
      </div>
      {isEmpty ? (
        isActive ? <TodayDropPlaceholder active /> : null
      ) : (
        <div className="space-y-0">
          {children}
          {isActive && <TodayDropPlaceholder active />}
        </div>
      )}
    </section>
  );
}

/**
 * A Today task row that can be dragged onto another time bucket. Mirrors the
 * timeline's `DraggableTodoItem` (drag listeners on the whole card; the lifted
 * card is drawn by the shared `DragOverlay`, so the source row just dims).
 */
export function DraggableTodayTask({
  todo,
  currentBucket,
  highlighted = false,
}: {
  todo: TodoItemType;
  currentBucket: TodayBucketLabel;
  highlighted?: boolean;
}) {
  const data: TodayDraggableData = { todo, currentBucket };
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: todo.id,
    data,
  });

  return (
    <TodoItemCard
      todoItem={todo}
      perTaskOverdue
      highlighted={highlighted}
      showOverdueTag={false}
      containerProps={{ ...attributes, ...listeners }}
      dragging={isDragging}
      setDragNodeRef={setNodeRef}
    />
  );
}

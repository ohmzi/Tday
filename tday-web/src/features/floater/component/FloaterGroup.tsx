import {
  closestCenter,
  DndContext,
  type DragEndEvent,
  KeyboardSensor,
  MouseSensor,
  TouchSensor,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { useCallback, useEffect, useState } from "react";
import { cn } from "@/lib/utils";
import type { FloaterItemType } from "@/types";
import { useReorderFloater } from "@/features/floater/query/reorder-floater";
import FloaterItemContainer from "./FloaterItemContainer";

export default function FloaterGroup({
  floaters,
  className,
  highlightedFloaterId,
  reorderable = true,
}: {
  floaters: FloaterItemType[];
  className?: string;
  highlightedFloaterId?: string | null;
  reorderable?: boolean;
}) {
  const { reorderMutateFn } = useReorderFloater();
  const [items, setItems] = useState(floaters);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  useEffect(() => {
    setItems(floaters);
  }, [floaters]);

  const reorderDiff = useCallback(() => {
    const reorderList = [] as { id: string; order: number }[];
    items.forEach((floater, index) => {
      if (floater.id !== floaters[index]?.id) {
        reorderList.push({ id: floater.id, order: floaters[index]?.order ?? index });
      }
    });
    return reorderList;
  }, [items, floaters]);

  useEffect(() => {
    if (!reorderable) return;
    if (floaters.length !== items.length) return;
    const reorderList = reorderDiff();
    if (reorderList.length > 0) {
      const timer = setTimeout(() => reorderMutateFn(reorderList), 3000);
      return () => clearTimeout(timer);
    }
  }, [items, floaters, reorderable, reorderDiff, reorderMutateFn]);

  const sensors = useSensors(
    useSensor(KeyboardSensor),
    useSensor(MouseSensor),
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 5 } }),
  );

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over) return;
    if (active.id !== over.id) {
      setItems((currentItems) => {
        const oldIndex = currentItems.findIndex((item) => item.id === active.id);
        const newIndex = currentItems.findIndex((item) => item.id === over.id);
        return arrayMove(currentItems, oldIndex, newIndex);
      });
    }
  }

  if (!mounted || !reorderable) {
    return (
      <div className={cn("space-y-0", className)}>
        {items.map((item) => (
          <FloaterItemContainer
            key={item.id}
            floater={item}
            highlighted={highlightedFloaterId === item.id}
          />
        ))}
      </div>
    );
  }

  return (
    <div className={cn("space-y-0", className)}>
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragEnd={handleDragEnd}
      >
        <SortableContext items={items} strategy={verticalListSortingStrategy}>
          {items.map((item) => (
            <FloaterItemContainer
              floater={item}
              key={item.id}
              highlighted={highlightedFloaterId === item.id}
            />
          ))}
        </SortableContext>
      </DndContext>
    </div>
  );
}

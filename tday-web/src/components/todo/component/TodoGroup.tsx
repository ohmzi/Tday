import { SortableContext } from "@dnd-kit/sortable";
import { DndContext, DragEndEvent } from "@dnd-kit/core";
import { useSensor } from "@dnd-kit/core";
import { useSensors } from "@dnd-kit/core";
import { KeyboardSensor, TouchSensor, MouseSensor } from "@dnd-kit/core";
import { closestCenter } from "@dnd-kit/core";
import { arrayMove } from "@dnd-kit/sortable";
import { verticalListSortingStrategy } from "@dnd-kit/sortable";
import { TodoItemType } from "@/types";
import { useCallback, useEffect, useState } from "react";
import { TodoItemContainer } from "./TodoItemContainer";
import { useTodoMutation } from "@/providers/TodoMutationProvider";
import { useUserPreferences } from "@/providers/UserPreferencesProvider";
import { cn } from "@/lib/utils";

const TodoGroup = ({
  todos,
  className,
  overdue,
  perTaskOverdue,
  highlightedTodoId,
  reorderable = true,
  showOverdueTag = true,
}: {
  todos: TodoItemType[];
  className?: string;
  overdue?: boolean;
  perTaskOverdue?: boolean;
  highlightedTodoId?: string | null;
  reorderable?: boolean;
  showOverdueTag?: boolean;
}) => {
  const { preferences } = useUserPreferences();
  const { useReorderTodo } = useTodoMutation();
  const { reorderMutateFn } = useReorderTodo();
  const [items, setItems] = useState(todos);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  // Update local state
  useEffect(() => {
    setItems(todos);
  }, [todos]);

  // Function to detect any changes between items and todos
  const reorderDiff = useCallback(() => {
    const reorderList = [] as { id: string; order: number }[];
    items.forEach((todo, index) => {
      if (todo.id !== todos[index].id) {
        reorderList.push({ id: todo.id, order: todos[index].order });
      }
    });
    return reorderList;
  }, [items, todos]);

  // Changes to local todo arrangement will update database
  useEffect(() => {
    if (!reorderable || preferences?.sortBy) return;
    // Wait for [items] to sync with [todos]
    if (todos.length !== items.length) return;
    const reorderList = reorderDiff();
    if (reorderList.length > 0) {
      const timer = setTimeout(() => reorderMutateFn(reorderList), 3000);
      return () => {
        clearTimeout(timer);
      };
    }
  }, [items, todos, preferences?.sortBy, reorderable, reorderDiff, reorderMutateFn]);

  const sensors = useSensors(
    useSensor(KeyboardSensor),
    useSensor(MouseSensor),
    useSensor(TouchSensor, { activationConstraint: { delay: 250, tolerance: 5 } })
  );

  const [isDragging, setIsDragging] = useState(false);

  // Lock page scroll while a drag is active.
  useEffect(() => {
    if (!isDragging) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, [isDragging]);

  function handleDragEnd(event: DragEndEvent) {
    setIsDragging(false);
    const { active, over } = event;
    if (!over) return;
    if (active.id !== over.id) {
      setItems((items) => {
        const oldIndex = items.findIndex((item) => {
          return item.id === active.id;
        });
        const newIndex = items.findIndex((item) => {
          return item.id === over.id;
        });
        return arrayMove(items, oldIndex, newIndex);
      });
    }
  }

  if (!mounted || !reorderable || preferences?.sortBy) {
    const showDragDisabledToast = reorderable && Boolean(preferences?.sortBy);

    return (
      <div className={cn("space-y-0", className)}>
        {items.map((item) => (
          <div
            key={item.id}
            draggable={showDragDisabledToast}
            onDragStart={
              showDragDisabledToast
                ? (e) => {
                    // A global sort/filter is active — silently block the drag
                    // (no toast, per the unified toast policy).
                    e.preventDefault();
                  }
                : undefined
            }
          >
            <TodoItemContainer
              todoItem={item}
              overdue={overdue}
              perTaskOverdue={perTaskOverdue}
              highlighted={highlightedTodoId === item.id}
              showOverdueTag={showOverdueTag}
            />
          </div>
        ))}
      </div>
    );
  }


  return (
    <div className={cn("space-y-0", className)}>
      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        onDragStart={() => setIsDragging(true)}
        onDragEnd={handleDragEnd}
        onDragCancel={() => setIsDragging(false)}
      >
        <SortableContext
          items={items}
          strategy={verticalListSortingStrategy}>
          {items.map((item) => (
            <TodoItemContainer
              todoItem={item}
              key={item.id}
              overdue={overdue}
              perTaskOverdue={perTaskOverdue}
              highlighted={highlightedTodoId === item.id}
              showOverdueTag={showOverdueTag}
            />
          ))}
        </SortableContext>
      </DndContext>
    </div>
  );
};

export default TodoGroup;

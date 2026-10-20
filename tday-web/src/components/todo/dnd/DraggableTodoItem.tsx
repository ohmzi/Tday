import { useDraggable } from "@dnd-kit/core";
import { TodoItemType } from "@/types";
import { TodoItemCard } from "@/components/todo/component/TodoItemContainer";
import { useTaskSelection } from "@/providers/TaskSelectionProvider";
import type { TimelineDraggableData } from "./TimelineDndContext";

/**
 * A timeline task row that can be dragged onto another date section to
 * reschedule it. Mirrors the sortable `TodoItemContainer` (drag listeners on
 * the whole card via `containerProps`), so a tap still selects/edits and a
 * press-and-hold lifts the card — the native long-press-to-drag feel. The lifted
 * card itself is rendered by the shared `DragOverlay`, so the source row just
 * dims while dragging.
 */
export default function DraggableTodoItem({
  todo,
  currentDayKey,
  highlighted = false,
  perTaskOverdue = false,
  overdue = false,
}: {
  todo: TodoItemType;
  currentDayKey: string;
  highlighted?: boolean;
  perTaskOverdue?: boolean;
  overdue?: boolean;
}) {
  const data: TimelineDraggableData = { todo, currentDayKey };
  // A press-and-hold on a row must not lift it into another date while the user
  // is picking tasks — the press is the selection tap.
  const { selectionMode } = useTaskSelection();
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: todo.id,
    data,
    disabled: selectionMode,
  });

  return (
    <TodoItemCard
      todoItem={todo}
      overdue={overdue}
      perTaskOverdue={perTaskOverdue}
      highlighted={highlighted}
      containerProps={selectionMode ? {} : { ...attributes, ...listeners }}
      dragging={isDragging}
      setDragNodeRef={setNodeRef}
    />
  );
}

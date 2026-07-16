import { TodoItemType } from "@/types";
import { useCallback, useEffect, useState } from "react";
import { TodoItemContainer } from "./TodoItemContainer";
import { useTodoMutation } from "@/providers/TodoMutationProvider";
import { useUserPreferences } from "@/providers/UserPreferencesProvider";
import { cn } from "@/lib/utils";

// Task order is now FIXED by the shared sort engine (src/lib/taskSort.ts, applied
// upstream by buildTimelineSections/compareTodosWithinDay). Drag-to-reorder has
// been retired on web so the UI never implies a manual order the fixed sort would
// immediately override: rows are non-draggable and no drag handles are rendered.
// The reorder mutation (useReorderTodo / reorder-todo.ts / reorder-list-todo.ts)
// is intentionally kept but left dormant — nothing mutates the local order, so
// the diff below stays empty and the mutation never fires.
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

  // Display always mirrors the incoming (already fixed-sorted) todos.
  useEffect(() => {
    setItems(todos);
  }, [todos]);

  // Detect divergence between the rendered list and the source order. With drag
  // retired nothing reorders `items` locally, so this stays empty.
  const reorderDiff = useCallback(() => {
    const reorderList = [] as { id: string; order: number }[];
    items.forEach((todo, index) => {
      if (todo.id !== todos[index]?.id) {
        reorderList.push({ id: todo.id, order: todos[index].order });
      }
    });
    return reorderList;
  }, [items, todos]);

  // Preserved (dormant) reorder write path — retained so the mutation code stays
  // wired, but it can no longer be triggered now that dragging is disabled.
  useEffect(() => {
    if (!reorderable || preferences?.sortBy) return;
    if (todos.length !== items.length) return;
    const reorderList = reorderDiff();
    if (reorderList.length > 0) {
      const timer = setTimeout(() => reorderMutateFn(reorderList), 3000);
      return () => {
        clearTimeout(timer);
      };
    }
  }, [items, todos, preferences?.sortBy, reorderable, reorderDiff, reorderMutateFn]);

  return (
    <div className={cn("space-y-0", className)}>
      {items.map((item) => (
        <div key={item.id} draggable={false}>
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
};

export default TodoGroup;

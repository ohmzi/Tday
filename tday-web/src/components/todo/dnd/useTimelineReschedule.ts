import { useCallback } from "react";
import { TodoItemType } from "@/types";
import { moveTodoToDay } from "@/lib/moveTodoToDay";
import { getTodoDayKey } from "@/lib/todoToastNavigation";
import { useTodoMutation } from "@/providers/TodoMutationProvider";
import { TodoItemTypeWithDateChecksum } from "@/features/todayTodos/query/update-todo";

/**
 * Single source of truth for "reschedule a task to another day" used by the
 * timeline drag-and-drop layer. Lifted verbatim from the original
 * `AllTasksTimelineContainer.handleMove` so cross-date drops behave identically
 * across every timeline screen (and write through whichever mutation hooks the
 * surrounding `TodoMutationProvider` supplies — global or list-scoped).
 *
 * Time-of-day is preserved (`moveTodoToDay`). Recurring instances patch just the
 * instance; everything else patches the task with the date/rrule checksums the
 * backend expects.
 */
export function useTimelineReschedule(timeZone?: string) {
  const { useEditTodo, useEditTodoInstance } = useTodoMutation();
  const { editTodoMutateFn } = useEditTodo();
  const { editTodoInstanceMutateFn } = useEditTodoInstance(undefined);

  return useCallback(
    (todo: TodoItemType, targetDayKey: string) => {
      if (getTodoDayKey(todo.due, timeZone) === targetDayKey) return;

      const nextRange = moveTodoToDay(todo, targetDayKey, timeZone);

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

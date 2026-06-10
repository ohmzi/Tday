import { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useRouter } from "@/lib/navigation";
import { buildScheduledFocusPath } from "@/lib/todoToastNavigation";
import { useUndoableDelete } from "@/hooks/use-undoable-delete";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { TodoItemType } from "@/types";

type TodoDateTarget = Pick<TodoItemType, "due">;

type UndoableDeleteHandlers = {
  /** Fires the real DELETE request once the toast closes without undo. */
  commit: () => void;
  /** Restores the staged (cache-pruned) rows; the server still has them. */
  undo: () => void;
};

export function useTodoActionToast() {
  const { t } = useTranslation("app");
  const showUndoableDelete = useUndoableDelete();
  const router = useRouter();
  const userTZ = useUserTimezone();
  const timeZone = userTZ?.timeZone;

  const openScheduledDate = useCallback((todo: TodoDateTarget) => {
    router.push(buildScheduledFocusPath(todo, timeZone));
  }, [router, timeZone]);

  // Unified toast policy: task create & edit succeed silently; only deletion
  // shows a toast (failures are surfaced as error toasts at the mutation).
  // Deletion is delayed-commit: the caller stages it (cache pruning only) and
  // this toast's Undo decides whether the DELETE request ever fires.
  const showTodoDeletedToast = useCallback(
    (todo: TodoDateTarget, { commit, undo }: UndoableDeleteHandlers) => {
      showUndoableDelete({
        message: t("taskDeleted"),
        commit,
        undo,
        onClick: () => openScheduledDate(todo),
      });
    },
    [openScheduledDate, showUndoableDelete, t],
  );

  return {
    showTodoDeletedToast,
  };
}

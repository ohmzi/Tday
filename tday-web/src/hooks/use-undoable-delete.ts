import { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useToast } from "@/hooks/use-toast";

type UndoableDeleteOptions = {
  /** Toast body, e.g. "Task deleted". */
  message: string;
  /** Fires the real DELETE request. Runs exactly once, when the toast closes without undo. */
  commit: () => void;
  /** Restores the staged (cache-pruned) rows — the server still has them. */
  undo: () => void;
  /** Optional tap-through handler for the toast body. Tapping dismisses the toast, which commits the delete. */
  onClick?: () => void;
};

/**
 * Delayed-commit deletion. The caller "stages" the delete first by pruning the
 * affected query caches (no request sent), then calls this to show a toast
 * with an Undo action:
 * - Undo → the DELETE was never sent, so `undo` just refetches to restore.
 * - Toast closes any other way (timeout, swipe, tap-through) → `commit` fires
 *   the real DELETE.
 * Sonner can fire both onAutoClose and onDismiss for one toast, so a local
 * guard ensures commit runs exactly once and never after undo. Each invocation
 * owns its flags, so rapid successive deletes get independent commit timers.
 */
export function useUndoableDelete() {
  const { toast } = useToast();
  const { t } = useTranslation("app");

  return useCallback(
    ({ message, commit, undo, onClick }: UndoableDeleteOptions) => {
      let undone = false;
      let committed = false;
      const settle = () => {
        if (undone || committed) return;
        committed = true;
        commit();
      };

      toast({
        description: message,
        duration: 5000,
        onClick,
        action: {
          label: t("undo"),
          onClick: () => {
            if (committed || undone) return;
            undone = true;
            undo();
          },
        },
        onAutoClose: settle,
        onDismiss: settle,
      });
    },
    [t, toast],
  );
}

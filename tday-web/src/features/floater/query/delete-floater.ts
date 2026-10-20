import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { useUndoableDelete } from "@/hooks/use-undoable-delete";
import { markTaskDeletedLocally } from "@/lib/task-completion-signal";
import {
  releaseAndRestoreTodoRows,
  stageTodoRows,
} from "@/lib/todo/staged-todo-rows";
import type { FloaterItemType } from "@/types";

// Delayed-commit delete: `deleteMutateFn` only stages the delete (prunes the
// caches and shows an undoable toast). The DELETE request fires when the toast
// closes without undo; undo just refetches since the server never saw it.
//
// The row is claimed at the cache boundary as well as pruned — see
// `@/lib/todo/staged-todo-rows`.
export const useDeleteFloater = () => {
  const { toast } = useToast();
  const { t } = useTranslation("app");
  const showUndoableDelete = useUndoableDelete();
  const queryClient = useQueryClient();
  const { mutate: commitDelete, isPending: deletePending } = useMutation({
    mutationFn: async (floater: FloaterItemType) => {
      await api.DELETE({
        url: "/api/floater",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id: floater.id }),
      });
    },
    mutationKey: ["floater"],
    onError: (error) => {
      // No cache rollback needed: onSettled's invalidations refetch the still
      // existing rows from the server.
      toast({
        description:
          error.message === "Failed to fetch"
            ? "failed to connect to server"
            : error.message,
        variant: "destructive",
      });
    },
    onSettled: (_data, _error, floater) => {
      // Sent (or failed) — released before the invalidations, which are the
      // refetches allowed to answer for the row again. Every root the claim
      // reached, not only the pruned pair.
      releaseAndRestoreTodoRows(queryClient, [floater.id]);
      queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] });
    },
  });

  // Stage: prune the caches now, but DON'T send the DELETE yet — the undo
  // toast decides whether the request ever fires.
  const deleteMutateFn = (floater: FloaterItemType) => {
    // The empty state that follows the last row leaving reads this to tell a
    // list a task was deleted out of from one that was just finished.
    markTaskDeletedLocally();
    // Claim the row before the prune, so a refetch already in flight cannot slip
    // back in behind it.
    stageTodoRows(queryClient, [floater.id]);
    void queryClient.cancelQueries({ queryKey: ["floater"] });
    void queryClient.cancelQueries({ queryKey: ["floaterList"] });
    const remove = (old: FloaterItemType[] = []) =>
      old.filter((item) => item.id !== floater.id);
    queryClient.setQueryData(["floater"], remove);
    // Same shape note as `complete-floater.ts`: this cache holds an object
    // `{ list, floaters }`, not an array, so the array updater above is a no-op
    // against it — the row never actually left the floater-list screen.
    if (floater.listID) {
      queryClient.setQueryData<{ list: unknown; floaters: FloaterItemType[] }>(
        ["floaterList", floater.listID],
        (old) => (old ? { ...old, floaters: remove(old.floaters) } : old),
      );
    }

    showUndoableDelete({
      message: t("taskDeleted"),
      commit: () => commitDelete(floater),
      undo: () => {
        // The server still has the row — a refetch restores the pruned caches,
        // so the claim goes first: this is the refetch that is meant to win.
        releaseAndRestoreTodoRows(queryClient, [floater.id]);
      },
    });
  };

  return { deleteMutateFn, deletePending };
};

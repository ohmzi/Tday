import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { useUndoableDelete } from "@/hooks/use-undoable-delete";
import type { FloaterItemType } from "@/types";

// Delayed-commit delete: `deleteMutateFn` only stages the delete (prunes the
// caches and shows an undoable toast). The DELETE request fires when the toast
// closes without undo; undo just refetches since the server never saw it.
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
      queryClient.invalidateQueries({ queryKey: ["floater"] });
      queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] });
      queryClient.invalidateQueries({ queryKey: ["floaterList", floater.listID] });
    },
  });

  // Stage: prune the caches now, but DON'T send the DELETE yet — the undo
  // toast decides whether the request ever fires.
  const deleteMutateFn = (floater: FloaterItemType) => {
    void queryClient.cancelQueries({ queryKey: ["floater"] });
    void queryClient.cancelQueries({ queryKey: ["floaterList"] });
    const remove = (old: FloaterItemType[] = []) =>
      old.filter((item) => item.id !== floater.id);
    queryClient.setQueryData(["floater"], remove);
    if (floater.listID) queryClient.setQueryData(["floaterList", floater.listID], remove);

    showUndoableDelete({
      message: t("taskDeleted"),
      commit: () => commitDelete(floater),
      undo: () => {
        // The server still has the row — a refetch restores the pruned caches.
        void queryClient.invalidateQueries({ queryKey: ["floater"] });
        void queryClient.invalidateQueries({ queryKey: ["floaterList"] });
      },
    });
  };

  return { deleteMutateFn, deletePending };
};

import { useCallback } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { usePathname, useRouter } from "@/lib/navigation";
import { useMenu } from "@/providers/MenuProvider";
import { useToast } from "@/hooks/use-toast";
import { useUndoableDelete } from "@/hooks/use-undoable-delete";
import type { ListItemMetaMapType } from "@/types";

type DeleteListsResponse = {
  message?: string | null;
  deletedIds?: string[];
};

async function deleteLists(ids: string[]) {
  return (await api.DELETE({
    url: "/api/list",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      id: ids.length === 1 ? ids[0] : undefined,
      ids,
    }),
  })) as DeleteListsResponse | null;
}

function activeListIdFromPath(pathname: string) {
  const marker = "/app/list/";
  const index = pathname.indexOf(marker);
  if (index === -1) return null;
  return pathname.slice(index + marker.length).split("/")[0] || null;
}

/**
 * Delayed-commit deletion for lists (single or bulk), shared by the sidebar
 * and the list form sheet. Staging prunes the sidebar cache and leaves the
 * deleted list's page immediately — no DELETE is sent until the undo toast
 * closes without undo, so Undo simply refetches what the server still has.
 */
export function useUndoableListDelete() {
  const queryClient = useQueryClient();
  const router = useRouter();
  const pathname = usePathname();
  const { setActiveMenu } = useMenu();
  const { toast } = useToast();
  const { t: appDict } = useTranslation("app");
  const showUndoableDelete = useUndoableDelete();

  const invalidateListQueries = useCallback(async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] }),
      queryClient.invalidateQueries({ queryKey: ["list"] }),
      queryClient.invalidateQueries({ queryKey: ["todo"] }),
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] }),
      queryClient.invalidateQueries({ queryKey: ["overdueTodo"] }),
      queryClient.invalidateQueries({ queryKey: ["completedTodo"] }),
    ]);
  }, [queryClient]);

  // Commit half: fires the real DELETE once the undo toast has closed without
  // undo. Runs from a toast callback, possibly after the staging component
  // unmounted, so it only touches the queryClient and the imperative toast
  // (both safe after unmount).
  const commitDeleteLists = useCallback(
    async (ids: string[]) => {
      try {
        await deleteLists(ids);
      } catch (mutationError) {
        const fallback =
          ids.length === 1 ? "Failed to delete list" : "Failed to delete lists";
        toast({
          description:
            mutationError instanceof Error ? mutationError.message : fallback,
          variant: "destructive",
        });
      } finally {
        // Success: refresh caches around the cascade (lists + their tasks).
        // Failure: the same refetch restores the staged pruning.
        await invalidateListQueries();
      }
    },
    [invalidateListQueries, toast],
  );

  // Stage: prune the lists from the sidebar cache and leave a deleted list's
  // page immediately — but DON'T send the DELETE yet; the undo toast decides
  // whether the request ever fires.
  return useCallback(
    (listIds: string[]) => {
      const ids = [...new Set(listIds.map((id) => id.trim()).filter(Boolean))];
      if (ids.length === 0) return;

      void queryClient.cancelQueries({ queryKey: ["listMetaData"] });
      for (const id of ids) {
        void queryClient.cancelQueries({ queryKey: ["list", id] });
        queryClient.removeQueries({ queryKey: ["list", id] });
      }
      queryClient.setQueryData<ListItemMetaMapType>(
        ["listMetaData"],
        (old = {}) => {
          const next = { ...old };
          for (const id of ids) {
            delete next[id];
          }
          return next;
        },
      );

      const activeListId = activeListIdFromPath(pathname);
      if (activeListId && ids.includes(activeListId)) {
        setActiveMenu({ name: "Todo" });
        router.push("/app/tday");
      }

      showUndoableDelete({
        message:
          ids.length === 1
            ? appDict("listDeleted")
            : appDict("listsDeleted", { count: ids.length }),
        commit: () => void commitDeleteLists(ids),
        // The server still has the lists — a refetch restores the pruned caches.
        undo: () => void invalidateListQueries(),
      });
    },
    [
      appDict,
      commitDeleteLists,
      invalidateListQueries,
      pathname,
      queryClient,
      router,
      setActiveMenu,
      showUndoableDelete,
    ],
  );
}

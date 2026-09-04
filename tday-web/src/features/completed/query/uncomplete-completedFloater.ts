import { useQueryClient, useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { CompletedFloaterItemType } from "@/types";

/**
 * `PATCH /api/floater/uncomplete` — see FloaterUncompleteResponse in
 * shared/.../FloaterModels.kt. `listRecreated` means "landed somewhere other
 * than its original list" (true even on a second undo that converges onto an
 * already-recreated list) — not "did this call insert a new list".
 */
type FloaterUncompleteResponse = {
  message?: string;
  listRecreated?: boolean;
  listID?: string | null;
  listName?: string | null;
  listColor?: string | null;
};

export const useUnCompleteFloater = () => {
  const { toast } = useToast();
  const { t } = useTranslation("completed");
  const queryClient = useQueryClient();
  const { mutate: mutateUnComplete, isPending } = useMutation({
    mutationFn: async (
      floaterItem: CompletedFloaterItemType,
    ): Promise<FloaterUncompleteResponse> => {
      return await api.PATCH({
        url: "/api/floater/uncomplete",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id: floaterItem.originalFloaterID }),
      });
    },
    onMutate: async (floaterItem: CompletedFloaterItemType) => {
      await queryClient.cancelQueries({ queryKey: ["completedFloater"] });
      const oldFloaters = queryClient.getQueryData([
        "completedFloater",
      ]) as CompletedFloaterItemType[];

      queryClient.setQueryData(
        ["completedFloater"],
        (oldFloaters: CompletedFloaterItemType[] = []) =>
          oldFloaters.flatMap((oldFloater) => {
            if (oldFloater.id === floaterItem.id) return [];
            return [oldFloater];
          }),
      );
      return { oldFloaters };
    },
    onSuccess: (data) => {
      // Two distinct outcomes: plain restore (silent, matches the todo
      // screen's own uncomplete) vs. landing in a recreated list, which is
      // surprising enough (a different list id than the one this item was
      // completed from) that it is worth a toast on its own.
      if (data?.listRecreated && data.listName) {
        toast({ description: t("floaterRestoredIntoRecreatedList", { listName: data.listName }) });
      }
    },
    onError: (error, _floaterItem, context) => {
      toast({ description: error.message, variant: "destructive" });
      queryClient.setQueryData(["completedFloater"], context?.oldFloaters);
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["floater"] });
      queryClient.invalidateQueries({ queryKey: ["floaterList"] });
      // Restoring a floater may have recreated its list — refresh the sidebar/
      // dashboard list metadata (counts, and the recreated list itself).
      queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] });
    },
  });

  return { mutateUnComplete, isPending };
};

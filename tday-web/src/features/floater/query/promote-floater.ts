import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import type { FloaterItemType } from "@/types";
import type { FloaterListCache } from "@/features/floaterList/query/get-floater-list";

type PromoteFloaterContext = {
  oldFloaters?: FloaterItemType[];
  oldListFloaters?: FloaterListCache;
};

// Schedules a floater into a real Todo (POST /floater/{id}/promote). The
// floater row is consumed server-side, so both silos refetch on settle.
export const usePromoteFloater = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { mutate: promoteMutateFn, isPending: promotePending } = useMutation<
    void,
    Error,
    { floater: FloaterItemType; due: Date },
    PromoteFloaterContext
  >({
    mutationFn: async ({ floater, due }) => {
      await api.POST({
        url: `/api/floater/${floater.id}/promote`,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ due }),
      });
    },
    onMutate: async ({ floater }) => {
      // Optimistically drop the floater from its lists; the todo side refetches.
      await queryClient.cancelQueries({ queryKey: ["floater"] });
      if (floater.listID) {
        await queryClient.cancelQueries({ queryKey: ["floaterList", floater.listID] });
      }
      const oldFloaters = queryClient.getQueryData<FloaterItemType[]>(["floater"]);
      const oldListFloaters = floater.listID
        ? queryClient.getQueryData<FloaterListCache>(["floaterList", floater.listID])
        : undefined;

      const remove = (old: FloaterItemType[] = []) =>
        old.filter((item) => item.id !== floater.id);
      queryClient.setQueryData<FloaterItemType[] | undefined>(["floater"], (old) =>
        old ? remove(old) : old,
      );
      if (floater.listID) {
        // `["floaterList", id]` is `{ list, floaters }`; prune only `floaters`.
        queryClient.setQueryData<FloaterListCache | undefined>(
          ["floaterList", floater.listID],
          (old) => (old ? { ...old, floaters: remove(old.floaters) } : old),
        );
      }
      return { oldFloaters, oldListFloaters };
    },
    onError: (error, { floater }, context) => {
      queryClient.setQueryData(["floater"], context?.oldFloaters);
      if (floater.listID) {
        queryClient.setQueryData(["floaterList", floater.listID], context?.oldListFloaters);
      }
      toast({ description: error.message, variant: "destructive" });
    },
    onSettled: (_data, _error, { floater }) => {
      queryClient.invalidateQueries({ queryKey: ["floater"] });
      queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] });
      if (floater.listID) {
        queryClient.invalidateQueries({ queryKey: ["floaterList", floater.listID] });
      }
      queryClient.invalidateQueries({ queryKey: ["todo"] });
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
    },
  });
  return { promoteMutateFn, promotePending };
};

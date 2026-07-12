import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import type { FloaterItemType } from "@/types";

// Schedules a floater into a real Todo (POST /floater/{id}/promote). The
// floater row is consumed server-side, so both silos refetch on settle.
export const usePromoteFloater = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { mutate: promoteMutateFn, isPending: promotePending } = useMutation({
    mutationFn: async ({
      floater,
      due,
    }: {
      floater: FloaterItemType;
      due: Date;
    }) => {
      await api.POST({
        url: `/api/floater/${floater.id}/promote`,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ due }),
      });
    },
    onMutate: ({ floater }) => {
      // Optimistically drop the floater from its lists; the todo side refetches.
      void queryClient.cancelQueries({ queryKey: ["floater"] });
      const remove = (old: FloaterItemType[] = []) =>
        old.filter((item) => item.id !== floater.id);
      queryClient.setQueryData<FloaterItemType[]>(["floater"], remove);
      if (floater.listID) {
        queryClient.setQueryData<FloaterItemType[]>(
          ["floaterList", floater.listID],
          remove,
        );
      }
    },
    onError: (error) => {
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

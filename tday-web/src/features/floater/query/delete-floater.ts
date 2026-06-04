import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import type { FloaterItemType } from "@/types";

export const useDeleteFloater = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { mutate: deleteMutateFn, isPending: deletePending } = useMutation({
    mutationFn: async (floater: FloaterItemType) => {
      await api.DELETE({
        url: "/api/floater",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id: floater.id }),
      });
    },
    onMutate: async (floater) => {
      await queryClient.cancelQueries({ queryKey: ["floater"] });
      const oldFloaters = queryClient.getQueryData<FloaterItemType[]>(["floater"]);
      const remove = (old: FloaterItemType[] = []) =>
        old.filter((item) => item.id !== floater.id);
      queryClient.setQueryData(["floater"], remove);
      if (floater.listID) queryClient.setQueryData(["floaterList", floater.listID], remove);
      return { oldFloaters };
    },
    onError: (error, _floater, context) => {
      queryClient.setQueryData(["floater"], context?.oldFloaters);
      toast({ description: error.message, variant: "destructive" });
    },
    onSettled: (_data, _error, floater) => {
      queryClient.invalidateQueries({ queryKey: ["floater"] });
      queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] });
      queryClient.invalidateQueries({ queryKey: ["floaterList", floater.listID] });
    },
  });
  return { deleteMutateFn, deletePending };
};

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import type { changeMapType } from "@/features/todayTodos/query/reorder-todo";

export const useReorderFloater = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { mutate: reorderMutateFn, isPending: reorderPending } = useMutation({
    mutationFn: async (changeMap: changeMapType[]) => {
      await Promise.all(
        changeMap.map(({ id, order }) =>
          api.PATCH({
            url: "/api/floater/reorder",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ id, order }),
          }),
        ),
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["floater"] });
      queryClient.invalidateQueries({ queryKey: ["floaterList"] });
    },
    onError: (error) => {
      toast({ description: error.message, variant: "destructive" });
    },
  });

  return { reorderMutateFn, reorderPending };
};

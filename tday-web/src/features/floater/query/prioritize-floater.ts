import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import type { FloaterItemType } from "@/types";

export const usePrioritizeFloater = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { mutate: prioritizeMutateFn, isPending: prioritizePending } =
    useMutation({
      mutationFn: async ({
        id,
        level,
      }: {
        id: string;
        level: FloaterItemType["priority"];
        isRecurring: boolean;
      }) => {
        await api.PATCH({
          url: "/api/floater/prioritize",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ id, priority: level }),
        });
      },
      onMutate: async ({ id, level }) => {
        await queryClient.cancelQueries({ queryKey: ["floater"] });
        const oldTodos = queryClient.getQueryData<FloaterItemType[]>(["floater"]);
        queryClient.setQueryData(["floater"], (old: FloaterItemType[] = []) =>
          old.map((item) => (item.id === id ? { ...item, priority: level } : item)),
        );
        return { oldTodos };
      },
      onError: (error, _params, context) => {
        queryClient.setQueryData(["floater"], context?.oldTodos);
        toast({ description: error.message, variant: "destructive" });
      },
      onSettled: () => {
        queryClient.invalidateQueries({ queryKey: ["floater"] });
        queryClient.invalidateQueries({ queryKey: ["floaterList"] });
      },
    });

  return { prioritizeMutateFn, prioritizePending };
};

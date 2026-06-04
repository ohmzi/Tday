import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { floaterSchema } from "@/schema";
import type { FloaterItemType } from "@/types";
import { floaterListQueryKey, normalizeFloater } from "./floater-utils";

async function postFloater(floater: FloaterItemType) {
  const parsedObj = floaterSchema.safeParse({
    title: floater.title,
    description: floater.description,
    priority: floater.priority,
    listID: floater.listID ?? null,
  });

  if (!parsedObj.success) {
    throw new Error(parsedObj.error.errors[0].message);
  }

  const response = await api.POST({
    url: "/api/floater",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(parsedObj.data),
  });

  if (!response?.floater) {
    throw new Error(response?.message || "bad server response: Did not receive floater");
  }

  return normalizeFloater(response.floater);
}

export const useCreateFloater = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { mutate: createMutateFn, status: createStatus } = useMutation({
    mutationFn: postFloater,
    onMutate: async (newFloater) => {
      await queryClient.cancelQueries({ queryKey: ["floater"] });
      const oldFloaters = queryClient.getQueryData<FloaterItemType[]>(["floater"]);
      queryClient.setQueryData(["floater"], (old: FloaterItemType[] = []) => [
        ...old,
        newFloater,
      ]);
      return { oldFloaters };
    },
    onError: (error, _newFloater, context) => {
      queryClient.setQueryData(["floater"], context?.oldFloaters);
      toast({ description: error.message, variant: "destructive" });
    },
    onSettled: (_data, _error, newFloater) => {
      queryClient.invalidateQueries({ queryKey: ["floater"] });
      queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] });
      queryClient.invalidateQueries({
        queryKey: floaterListQueryKey(newFloater.listID),
      });
    },
    onSuccess: (createdFloater, newFloater) => {
      queryClient.setQueryData(["floater"], (old: FloaterItemType[] = []) =>
        old.map((item) => (item.id === newFloater.id ? createdFloater : item)),
      );
    },
  });

  return { createMutateFn, createStatus };
};

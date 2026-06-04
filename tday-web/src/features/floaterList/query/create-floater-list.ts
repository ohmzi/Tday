import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { listCreateSchema } from "@/schema";
import type { FloaterListItemMetaType, ListColor } from "@/types";
import { normalizeFloaterList } from "./floater-list-utils";

export const useCreateFloaterList = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { mutateAsync: createMutateAsync, isPending: createLoading } =
    useMutation({
      mutationFn: async ({
        name,
        color,
        iconKey,
      }: {
        name: string;
        color?: ListColor;
        iconKey?: string;
      }): Promise<FloaterListItemMetaType> => {
        const parsedObj = listCreateSchema.safeParse({ name, color, iconKey });
        if (!parsedObj.success) {
          throw new Error(parsedObj.error.errors[0].message);
        }
        const response = await api.POST({
          url: "/api/floaterList",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(parsedObj.data),
        });
        if (!response?.list) {
          throw new Error(response?.message || "Failed to create floater list");
        }
        return normalizeFloaterList(response.list);
      },
      onSuccess: () => {
        queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] });
      },
      onError: (error) => {
        toast({ description: error.message, variant: "destructive" });
      },
    });

  return { createMutateAsync, createLoading };
};

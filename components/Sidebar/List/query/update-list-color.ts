import { useToast } from "@/hooks/use-toast";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { ListItemType } from "@/types";

async function recolorList({
  id,
  color,
}: {
  id: string;
  color: ListItemType["color"];
}) {
  if (!id) {
    throw new Error("this list is missing");
  }
  if (!color) throw new Error("color is missing");

  await api.PATCH({
    url: `/api/list/${id}`,
    body: JSON.stringify({ color }),
  });
}

export const useRecolorList = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const {
    mutate: recolorMutateFn,
    isPending: recolorLoading,
    isSuccess,
  } = useMutation({
    mutationFn: (params: { id: string; color: ListItemType["color"] }) =>
      recolorList({ ...params }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] });
    },
    onError: (error) => {
      toast({ description: error.message, variant: "destructive" });
    },
  });
  return { recolorMutateFn, recolorLoading, isSuccess };
};

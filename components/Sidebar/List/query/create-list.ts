import { useToast } from "@/hooks/use-toast";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { ListItemMetaMapType, ListItemMetaType } from "@/types";
import type { ListColor } from "@/types";

async function postList({
  name,
  color,
}: {
  name: string;
  color?: ListColor;
}) {
  const response: { list?: ListItemMetaType } = await api.POST({
    url: "/api/list",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ name, color }),
  });

  return response.list;
}

export const useCreateList = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const {
    mutate: createMutateFn,
    mutateAsync: createMutateAsync,
    isPending: createLoading,
    isSuccess,
  } = useMutation({
    mutationFn: (params: { name: string; color?: ListColor }) =>
      postList({ ...params }),
    onMutate: (newListMeta: Omit<ListItemMetaType, "id">) => {
      queryClient.cancelQueries({ queryKey: ["listMetaData"] });
      const backupListMeta = queryClient.getQueryData(["listMetaData"]);

      queryClient.setQueryData(
        ["listMetaData"],
        (listMetaMap: ListItemMetaMapType) => {
          return {
            ...listMetaMap,
            "-1": { ...newListMeta, createdAt: new Date() },
          };
        },
      );

      return backupListMeta;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] });
    },
    onError: (error) => {
      toast({ description: error.message, variant: "destructive" });
    },
  });

  return { createMutateFn, createMutateAsync, createLoading, isSuccess };
};

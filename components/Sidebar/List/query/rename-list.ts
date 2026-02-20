import { useToast } from "@/hooks/use-toast";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";

async function renameList({ id, name }: { id: string; name: string }) {
  if (!id) {
    throw new Error("this list is missing");
  }
  if (name.trim().length <= 0) {
    throw new Error("list name cannot be empty");
  }
  await api.PATCH({
    url: `/api/list/${id}`,
    body: JSON.stringify({ name }),
  });
}

export const useRenameList = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const {
    mutate: renameMutateFn,
    isPending: renameLoading,
    isSuccess,
  } = useMutation({
    mutationFn: (params: { id: string; name: string }) =>
      renameList({ ...params }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] });
    },
    onError: (error) => {
      toast({ description: error.message, variant: "destructive" });
    },
  });
  return { renameMutateFn, renameLoading, isSuccess };
};

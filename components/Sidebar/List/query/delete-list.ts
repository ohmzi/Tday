import { useToast } from "@/hooks/use-toast";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";

async function deleteList({ id }: { id: string }) {
  await api.DELETE({ url: `/api/list/${id}` });
}

export const useDeleteList = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const {
    mutate: deleteMutateFn,
    isPending: deleteLoading,
    isSuccess,
  } = useMutation({
    mutationFn: (params: { id: string }) => deleteList({ ...params }),
    onSuccess: () => {
      toast({ description: "list deleted" });
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] });
      queryClient.invalidateQueries({ queryKey: ["todo"] });
      queryClient.invalidateQueries({ queryKey: ["overdueTodo"] });
    },
    onError: (error) => {
      toast({ description: error.message, variant: "destructive" });
    },
  });
  return { deleteMutateFn, deleteLoading, isSuccess };
};

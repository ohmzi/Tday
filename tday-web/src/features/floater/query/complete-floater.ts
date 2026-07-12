import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import type { FloaterItemType } from "@/types";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";

// Delayed-commit complete (see complete-todo.ts): stage the removal from the
// floater caches, show an undoable toast, and only PATCH /floater/complete once
// the toast closes without undo.
export const useCompleteFloater = () => {
  const { toast } = useToast();
  const { showTodoCompletedToast } = useTodoActionToast();
  const queryClient = useQueryClient();
  const { mutate: commitComplete, isPending: completePending } = useMutation({
    mutationFn: async (floater: FloaterItemType) => {
      await api.PATCH({
        url: "/api/floater/complete",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id: floater.id }),
      });
    },
    onError: (error) => {
      toast({ description: error.message, variant: "destructive" });
    },
    onSettled: (_data, _error, floater) => {
      queryClient.invalidateQueries({ queryKey: ["floater"] });
      queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] });
      queryClient.invalidateQueries({ queryKey: ["completedFloater"] });
      queryClient.invalidateQueries({ queryKey: ["floaterList", floater.listID] });
    },
  });

  const completeMutateFn = (floater: FloaterItemType) => {
    void queryClient.cancelQueries({ queryKey: ["floater"] });
    const remove = (old: FloaterItemType[] = []) =>
      old.filter((item) => item.id !== floater.id);
    queryClient.setQueryData<FloaterItemType[]>(["floater"], remove);
    if (floater.listID) {
      queryClient.setQueryData<FloaterItemType[]>(["floaterList", floater.listID], remove);
    }

    showTodoCompletedToast({
      commit: () => commitComplete(floater),
      undo: () => {
        // The server still has the floater (incomplete) — a refetch restores it.
        void queryClient.invalidateQueries({ queryKey: ["floater"] });
        if (floater.listID) {
          void queryClient.invalidateQueries({ queryKey: ["floaterList", floater.listID] });
        }
      },
    });
  };

  return { completeMutateFn, completePending };
};

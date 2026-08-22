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
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["floater"] });
      queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] });
      queryClient.invalidateQueries({ queryKey: ["completedFloater"] });
      // Prefix match: rows on a floater-list screen carry no listID (see below).
      queryClient.invalidateQueries({ queryKey: ["floaterList"] });
    },
  });

  const completeMutateFn = (floater: FloaterItemType) => {
    void queryClient.cancelQueries({ queryKey: ["floater"] });
    const remove = (old: FloaterItemType[] = []) =>
      old.filter((item) => item.id !== floater.id);
    queryClient.setQueryData<FloaterItemType[]>(["floater"], remove);
    // Prefix match rather than ["floaterList", floater.listID]: the floater-list endpoint returns
    // FloaterListTodoDto, which carries no listID, so that guard was never true and the ticked
    // floater never left the list. Note the shape difference too — this cache holds an object
    // { list, floaters }, not an array, so it needs its own updater.
    queryClient.setQueriesData<{ list: unknown; floaters: FloaterItemType[] }>(
      { queryKey: ["floaterList"] },
      (old) => (old ? { ...old, floaters: remove(old.floaters) } : old),
    );

    showTodoCompletedToast({
      commit: () => commitComplete(floater),
      undo: () => {
        // The server still has the floater (incomplete) — a refetch restores it.
        void queryClient.invalidateQueries({ queryKey: ["floater"] });
        void queryClient.invalidateQueries({ queryKey: ["floaterList"] });
      },
    });
  };

  return { completeMutateFn, completePending };
};

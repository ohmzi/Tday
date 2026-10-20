import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import type { FloaterItemType } from "@/types";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";
import {
  markCelebrationCancelled,
  markTaskCompleted,
} from "@/lib/task-completion-signal";
import {
  releaseAndRestoreTodoRows,
  stageTodoRows,
} from "@/lib/todo/staged-todo-rows";

// Delayed-commit complete (see complete-todo.ts): stage the removal from the
// floater caches, show an undoable toast, and only PATCH /floater/complete once
// the toast closes without undo.
//
// The prune is one write and the window is seconds of refetching — the Anytime
// feeds re-read `["floater"]`/`["floaterList"]` on every `floater` realtime
// event, including the echo of this completion — so the row is claimed at the
// cache boundary too; see `@/lib/todo/staged-todo-rows`.
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
      // Sent (or failed) — released before the invalidations, which are the
      // refetches allowed to answer for the row again. Prefix match over
      // ["floaterList"]: rows on a floater-list screen carry no listID (see
      // below). Covers every root the claim reached, not only the pruned pair.
      releaseAndRestoreTodoRows(queryClient, [floater.id]);
      queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] });
      queryClient.invalidateQueries({ queryKey: ["completedFloater"] });
    },
  });

  const completeMutateFn = (floater: FloaterItemType) => {
    // The empty state that follows the last row leaving reads this to tell a
    // list the user finished from one that was never filled.
    markTaskCompleted();
    // Claim the row before the prune, so a refetch already in flight cannot slip
    // back in behind it.
    stageTodoRows(queryClient, [floater.id]);
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
        // A row is coming BACK, so this list is not finished any more and the
        // celebration ends now rather than when its own window runs out.
        // Stamped here rather than left to the refetch below: that is a network
        // round trip, and `useArrivalCancel`'s count-rise backstop cannot see
        // the row until it lands.
        markCelebrationCancelled();
        // The server still has the floater (incomplete) — a refetch restores it,
        // so the claim goes first: this is the refetch that is meant to win.
        releaseAndRestoreTodoRows(queryClient, [floater.id]);
      },
    });
  };

  return { completeMutateFn, completePending };
};

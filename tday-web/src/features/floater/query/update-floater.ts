import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { floaterSchema } from "@/schema";
import type { FloaterItemType } from "@/types";
import type { FloaterListCache } from "@/features/floaterList/query/get-floater-list";

type UpdateFloaterContext = {
  oldFloaters?: FloaterItemType[];
  /** The target list's `{ list, floaters }` before the optimistic write. */
  oldListFloaters?: FloaterListCache;
  /** The list the row was in before this edit, when the edit moves it. */
  previousListID: string | null;
  /** That previous list's cache before the optimistic prune. */
  oldPreviousListFloaters?: FloaterListCache;
};

/** The writable fields of a floater row, as `PATCH /api/floater` takes them. */
export type FloaterPatchFields = {
  id: string;
  title: string;
  description: string | null | undefined;
  priority: FloaterItemType["priority"];
  listID?: string | null;
};

/**
 * Writes floater fields. Exported so a caller holding only a floater's id (a
 * demote response, for instance) can write to it without a full FloaterItemType.
 */
export async function patchFloaterFields(fields: FloaterPatchFields) {
  const parsedObj = floaterSchema.safeParse({
    title: fields.title,
    description: fields.description,
    priority: fields.priority,
    listID: fields.listID ?? null,
  });

  if (!parsedObj.success) {
    throw new Error(parsedObj.error.issues[0].message);
  }

  await api.PATCH({
    url: "/api/floater",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id: fields.id, ...parsedObj.data }),
  });
}

async function patchFloater(floater: FloaterItemType) {
  await patchFloaterFields({
    id: floater.id,
    title: floater.title,
    description: floater.description,
    priority: floater.priority,
    listID: floater.listID ?? null,
  });
}

export const useEditFloater = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { mutate: editTodoMutateFn, status: editTodoStatus } = useMutation<
    void,
    Error,
    FloaterItemType,
    UpdateFloaterContext
  >({
    mutationFn: patchFloater,
    onMutate: async (updatedFloater) => {
      const newListID = updatedFloater.listID ?? null;
      await queryClient.cancelQueries({ queryKey: ["floater"] });
      if (newListID) {
        await queryClient.cancelQueries({ queryKey: ["floaterList", newListID] });
      }

      const oldFloaters = queryClient.getQueryData<FloaterItemType[]>(["floater"]);
      // The root cache is the only place that records which list the row was in.
      // If it is not loaded, the old list cannot be pruned here and is left to the
      // invalidation in onSettled.
      const previousListID =
        oldFloaters?.find((floater) => floater.id === updatedFloater.id)?.listID ?? null;
      const movedFromListID =
        previousListID && previousListID !== newListID ? previousListID : null;
      if (movedFromListID) {
        await queryClient.cancelQueries({ queryKey: ["floaterList", movedFromListID] });
      }

      const oldListFloaters = newListID
        ? queryClient.getQueryData<FloaterListCache>(["floaterList", newListID])
        : undefined;
      const oldPreviousListFloaters = movedFromListID
        ? queryClient.getQueryData<FloaterListCache>(["floaterList", movedFromListID])
        : undefined;

      const update = (floater: FloaterItemType) =>
        floater.id === updatedFloater.id ? { ...floater, ...updatedFloater } : floater;

      // Unloaded caches stay unloaded: an `undefined` return leaves them untouched
      // instead of writing `[]` (the floaterList cache is `{ list, floaters }`).
      queryClient.setQueryData<FloaterItemType[] | undefined>(["floater"], (old) =>
        old ? old.map(update) : old,
      );

      if (movedFromListID) {
        queryClient.setQueryData<FloaterListCache | undefined>(
          ["floaterList", movedFromListID],
          (old) =>
            old
              ? {
                  ...old,
                  floaters: old.floaters.filter((floater) => floater.id !== updatedFloater.id),
                }
              : old,
        );
      }

      if (newListID) {
        queryClient.setQueryData<FloaterListCache | undefined>(
          ["floaterList", newListID],
          (old) => {
            if (!old) return old;
            const alreadyInList = old.floaters.some(
              (floater) => floater.id === updatedFloater.id,
            );
            return {
              ...old,
              floaters: alreadyInList
                ? old.floaters.map(update)
                : [...old.floaters, updatedFloater],
            };
          },
        );
      }

      return {
        oldFloaters,
        oldListFloaters,
        previousListID: movedFromListID,
        oldPreviousListFloaters,
      };
    },
    onError: (error, updatedFloater, context) => {
      queryClient.setQueryData(["floater"], context?.oldFloaters);
      if (context?.previousListID) {
        queryClient.setQueryData(
          ["floaterList", context.previousListID],
          context.oldPreviousListFloaters,
        );
      }
      if (updatedFloater.listID) {
        queryClient.setQueryData(
          ["floaterList", updatedFloater.listID],
          context?.oldListFloaters,
        );
      }
      toast({ description: error.message, variant: "destructive" });
    },
    onSettled: (_data, _error, updatedFloater, context) => {
      queryClient.invalidateQueries({ queryKey: ["floater"] });
      queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] });
      // Both ends of a move: the list it left and the list it entered.
      const listIDs = new Set(
        [updatedFloater.listID, context?.previousListID].filter(
          (id): id is string => Boolean(id),
        ),
      );
      for (const listID of listIDs) {
        queryClient.invalidateQueries({ queryKey: ["floaterList", listID] });
      }
    },
  });

  return { editTodoMutateFn, editTodoStatus };
};

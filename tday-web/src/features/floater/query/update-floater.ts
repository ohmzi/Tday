import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { floaterSchema } from "@/schema";
import type { FloaterItemType } from "@/types";

type UpdateFloaterContext = {
  oldFloaters?: FloaterItemType[];
  oldListFloaters?: FloaterItemType[];
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
    throw new Error(parsedObj.error.errors[0].message);
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
      await queryClient.cancelQueries({ queryKey: ["floater"] });
      await queryClient.cancelQueries({ queryKey: ["floaterList", updatedFloater.listID] });

      const oldFloaters = queryClient.getQueryData<FloaterItemType[]>(["floater"]);
      const oldListFloaters = queryClient.getQueryData<FloaterItemType[]>([
        "floaterList",
        updatedFloater.listID,
      ]);

      const update = (floater: FloaterItemType) =>
        floater.id === updatedFloater.id ? { ...floater, ...updatedFloater } : floater;

      queryClient.setQueryData(["floater"], (old: FloaterItemType[] = []) =>
        old.map(update),
      );
      if (updatedFloater.listID) {
        queryClient.setQueryData(
          ["floaterList", updatedFloater.listID],
          (old: FloaterItemType[] = []) => old.map(update),
        );
      }

      return { oldFloaters, oldListFloaters };
    },
    onError: (error, updatedFloater, context) => {
      queryClient.setQueryData(["floater"], context?.oldFloaters);
      if (updatedFloater.listID) {
        queryClient.setQueryData(
          ["floaterList", updatedFloater.listID],
          context?.oldListFloaters,
        );
      }
      toast({ description: error.message, variant: "destructive" });
    },
    onSettled: (_data, _error, updatedFloater) => {
      queryClient.invalidateQueries({ queryKey: ["floater"] });
      queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] });
      queryClient.invalidateQueries({ queryKey: ["floaterList", updatedFloater.listID] });
    },
  });

  return { editTodoMutateFn, editTodoStatus };
};

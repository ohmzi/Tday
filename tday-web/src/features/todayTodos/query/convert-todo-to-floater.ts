import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { patchFloaterFields } from "@/features/floater/query/update-floater";
import type { FloaterItemType, TodoItemType } from "@/types";

export type ConvertTodoToFloaterInput = {
  todo: TodoItemType;
  title: string;
  description: string | null;
  priority: FloaterItemType["priority"];
  listID: string | null;
};

type DemoteTodoResponse = {
  message?: string | null;
  floater?: { id?: string } | null;
};

/**
 * Turning a scheduled task into a Floater is a conversion, not a field write.
 * They are two entities in two tables — `todos` (with a NOT NULL due) and
 * `floaters` (no due, no rrule, and its own list type) — so there is no
 * scheduled/kind field for `PATCH /api/todo` to carry, and the toggle's
 * "off" state has nowhere to land. `POST /api/todo/{id}/demote` is the one
 * endpoint that honours it: it consumes the todo row and mints a floater.
 *
 * The edits made in the same save (title, notes, priority, floater list) are
 * written onto the new floater afterwards, because demote copies the todo
 * row's fields as they were before this save.
 *
 * Recurring tasks are refused by the backend ("recurring tasks cannot be
 * demoted to floaters") — a series would be silently destroyed — so the sheet
 * does not offer the toggle for them; this surfaces the refusal if one arrives
 * anyway rather than half-converting.
 */
export const useConvertTodoToFloater = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const { mutate: convertMutateFn, isPending: convertPending } = useMutation({
    mutationFn: async ({
      todo,
      title,
      description,
      priority,
      listID,
    }: ConvertTodoToFloaterInput) => {
      const response = (await api.POST({
        url: `/api/todo/${todo.id}/demote`,
        headers: { "Content-Type": "application/json" },
        body: undefined,
      })) as DemoteTodoResponse | null;
      const floaterId = response?.floater?.id;
      if (!floaterId) {
        throw new Error("the task could not be turned into a floater");
      }
      await patchFloaterFields({ id: floaterId, title, description, priority, listID });
    },
    onMutate: ({ todo }) => {
      // Optimistically drop the todo from the dated feeds; floaters refetch.
      void queryClient.cancelQueries({ queryKey: ["todo"] });
      void queryClient.cancelQueries({ queryKey: ["todoTimeline"] });
      const remove = (old: TodoItemType[] = []) =>
        old.filter((item) => item.id !== todo.id);
      queryClient.setQueryData<TodoItemType[]>(["todo"], remove);
      queryClient.setQueryData<TodoItemType[]>(["todoTimeline"], remove);
    },
    onError: (error) => {
      toast({ description: error.message, variant: "destructive" });
      // The optimistic removal above must not survive a refusal (a recurring
      // task, say): the todo is still there.
      queryClient.invalidateQueries({ queryKey: ["todo"] });
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["todo"] });
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] });
      queryClient.invalidateQueries({ queryKey: ["floater"] });
      queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] });
    },
  });

  return { convertMutateFn, convertPending };
};

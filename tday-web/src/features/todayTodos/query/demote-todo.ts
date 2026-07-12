import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import type { TodoItemType } from "@/types";

// "Let it float": demotes a stale todo into an Anytime floater
// (POST /todo/{id}/demote). The todo row is consumed server-side. Recurring
// todos are rejected by the backend, so callers hide the action for them.
export const useDemoteTodo = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { mutate: demoteMutateFn, isPending: demotePending } = useMutation({
    mutationFn: async (todo: TodoItemType) => {
      await api.POST({
        url: `/api/todo/${todo.id}/demote`,
        headers: { "Content-Type": "application/json" },
        body: undefined,
      });
    },
    onMutate: (todo) => {
      // Optimistically drop the todo from the timeline; floaters refetch.
      void queryClient.cancelQueries({ queryKey: ["todo"] });
      void queryClient.cancelQueries({ queryKey: ["todoTimeline"] });
      const remove = (old: TodoItemType[] = []) =>
        old.filter((item) => item.id !== todo.id);
      queryClient.setQueryData<TodoItemType[]>(["todo"], remove);
      queryClient.setQueryData<TodoItemType[]>(["todoTimeline"], remove);
    },
    onError: (error) => {
      toast({ description: error.message, variant: "destructive" });
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
  return { demoteMutateFn, demotePending };
};

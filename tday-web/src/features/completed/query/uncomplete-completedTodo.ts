import { useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import {
  releaseAndRestoreTodoRows,
  stageTodoRows,
} from "@/lib/todo/staged-todo-rows";
import { CompletedTodoItemType } from "@/types";
export const useUnCompleteTodo = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { mutate: mutateUnComplete, isPending } = useMutation({
    mutationFn: async (todoItem: CompletedTodoItemType) => {
      await api.PATCH({
        url: "/api/todo/uncomplete",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          id: todoItem.originalTodoID,
          // Date, not epoch millis: the backend parses instanceDate as ISO-8601.
          instanceDate: todoItem.rrule ? (todoItem.instanceDate ?? null) : null,
        }),
      });
    },
    mutationKey: ["completedTodo"],
    onMutate: async (todoItem: CompletedTodoItemType) => {
      // Quieten the key BEFORE writing the optimistic removal, in the order the
      // TanStack guide gives. `cancelQueries` reverts the cache to the state before
      // the in-flight fetch by default (`revert: true`), so cancelling after the
      // removal would undo the removal itself — the list would keep drawing the row
      // the user just dismissed, which is the bug this whole change is about,
      // arrived at from the other side.
      await queryClient.cancelQueries({ queryKey: ["completedTodo"] });
      // Claim the row before the removal, and for the same reason the complete
      // path claims before its prune: a read that starts during the window below
      // answers with the server still reporting the task completed, and would put
      // the row the user just dismissed straight back into this list.
      //
      // Un-complete needs this MORE than the complete path does, not less. That
      // direction removes the row and then holds it out for the five-second undo
      // window; this one removes it immediately and the PATCH does not even fire
      // until the row's exit choreography finishes ~840 ms later. Nothing at all
      // was covering that window: `["completedTodo"]` was deliberately not one of
      // the roots `staged-todo-rows` claimed, because for a completion the row
      // belongs there. For a restore it is exactly the row that must not.
      stageTodoRows(queryClient, [todoItem.originalTodoID ?? todoItem.id]);
      const oldTodos = queryClient.getQueryData([
        "completedTodo",
      ]) as CompletedTodoItemType[];

      queryClient.setQueryData(
        ["completedTodo"],
        (oldTodos: CompletedTodoItemType[] = []) =>
          oldTodos.flatMap((oldTodo) => {
            if (oldTodo.id === todoItem.id) return [];
            return [oldTodo];
          }),
      );
      return { oldTodos };
    },
    onError: (error, _newTodo, context) => {
      toast({ description: error.message, variant: "destructive" });
      queryClient.setQueryData(["completedTodo"], context?.oldTodos);
    },
    onSettled: (_data, _error, todoItem) => {
      // The request has been sent, so the row is the read path's business again —
      // and if it failed the task is still completed server-side, which is exactly
      // what the refetch these invalidations trigger will report. Released first:
      // that refetch is the first one allowed to answer for this row, and it must
      // not be filtered by the claim it is undoing. All of the roots the claim
      // covered, not just this one, because a restore moves the row into the
      // active caches as well — see `releaseAndRestoreTodoRows`.
      releaseAndRestoreTodoRows(queryClient, [todoItem.originalTodoID ?? todoItem.id]);
      // Restoring a task bumps its list's active count back up.
      queryClient.invalidateQueries({ queryKey: ["listMetaData"] });
    },
  });

  return { mutateUnComplete, isPending };
};

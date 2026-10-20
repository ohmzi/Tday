import { useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { canonicalTodoId } from "@/lib/todo/todo-id";
import { TodoItemType } from "@/types";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";
import { markCelebrationCancelled } from "@/lib/task-completion-signal";
import {
  releaseAndRestoreTodoRows,
  stageTodoRows,
} from "@/lib/todo/staged-todo-rows";

// Delayed-commit complete (see complete-todo.ts): stage the removal of this
// recurring instance from the calendar cache, show an undoable toast, and only
// PATCH /complete once the toast closes without undo.
//
// The row is claimed at the cache boundary as well as pruned; see
// `complete-calendar-todo.ts`.
export const useCompleteCalendarTodoInstance = () => {
  const { toast } = useToast();
  const { showTodoCompletedToast } = useTodoActionToast();
  const queryClient = useQueryClient();

  const { mutate: commitComplete, isPending } = useMutation({
    mutationFn: async ({ todoItem }: { todoItem: TodoItemType }) => {
      const todoId = canonicalTodoId(todoItem.id);
      await api.PATCH({
        url: "/api/todo/complete",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          id: todoId,
          // Date, not epoch millis: the backend parses instanceDate as ISO-8601.
          instanceDate: todoItem.instanceDate ?? null,
        }),
      });
    },
    onError: (error) => {
      toast({ description: error.message, variant: "destructive" });
    },
    onSettled: (_data, _error, { todoItem }) => {
      // Sent (or failed) — released before the invalidations, which are the
      // refetches allowed to answer for the row again.
      releaseAndRestoreTodoRows(queryClient, [todoItem.id]);
      queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
    },
  });

  const mutateComplete = ({ todoItem }: { todoItem: TodoItemType }) => {
    const instanceDateMs = todoItem.instanceDate?.getTime();
    // Claim this occurrence's row before the prune. The prune below matches on
    // `instanceDate` because that is what names an occurrence here; the claim
    // matches on the cache id, which is the same occurrence's row id in every
    // list cache — `${todo.id}:${instanceDateTime}`.
    stageTodoRows(queryClient, [todoItem.id]);
    void queryClient.cancelQueries({ queryKey: ["calendarTodo"] });
    if (instanceDateMs != null) {
      queryClient.setQueriesData<TodoItemType[]>(
        { queryKey: ["calendarTodo"] },
        (old) =>
          old?.filter((todo) => todo.instanceDate?.getTime() !== instanceDateMs),
      );
    }

    showTodoCompletedToast({
      commit: () => commitComplete({ todoItem }),
      undo: () => {
        // A row is coming BACK, so whatever screen the celebration is playing
        // on is not finished any more and it ends now rather than when its own
        // window runs out. Stamped here rather than left to the refetch below:
        // that is a network round trip, and `useArrivalCancel`'s count-rise
        // backstop cannot see the row until it lands.
        markCelebrationCancelled();
        // The server still has the instance (incomplete) — a refetch restores it,
        // so the claim goes first: this is the refetch that is meant to win.
        releaseAndRestoreTodoRows(queryClient, [todoItem.id]);
      },
    });
  };

  return { mutateComplete, isPending };
};

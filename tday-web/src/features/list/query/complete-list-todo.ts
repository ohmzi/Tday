import { useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { canonicalTodoId } from "@/lib/todo/todo-id";
import { TodoItemType } from "@/types";
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
// list cache, show an undoable toast, and only PATCH /complete once the toast
// closes without undo. The claim below is the same one the timeline's complete
// makes — see `@/lib/todo/staged-todo-rows`.
export const useCompleteListTodo = () => {
    const { toast } = useToast();
    const { showTodoCompletedToast } = useTodoActionToast();
    const queryClient = useQueryClient();
    const { mutate: commitComplete, isPending: completePending } = useMutation({
        mutationFn: async (todoItem: TodoItemType) => {
            const todoId = canonicalTodoId(todoItem.id);
            await api.PATCH({
                url: "/api/todo/complete",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    id: todoId,
                    // Date, not epoch millis: the backend parses instanceDate as ISO-8601.
                    instanceDate: todoItem.rrule ? (todoItem.instanceDate ?? null) : null,
                }),
            });
        },
        onError: (error) => {
            toast({ description: error.message, variant: "destructive" });
        },
        onSettled: (_data, _error, todoItem) => {
            // Sent (or failed) — the read path may answer for this row again.
            // Every row-list root, not only the ["list"] this site pruned: the
            // guard claimed the row in all of them — see
            // `releaseAndRestoreTodoRows`.
            releaseAndRestoreTodoRows(queryClient, [todoItem.id]);
            queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
        },
    });

    const completeMutateFn = (todoItem: TodoItemType) => {
        // The empty state that follows the last row leaving reads this to tell a
        // list the user finished from one that was never filled.
        markTaskCompleted();
        // Claim the row before the prune: from here the cache boundary refuses
        // it, so a refetch already in flight cannot slip back in behind this.
        stageTodoRows(queryClient, [todoItem.id]);
        // Prefix match over ["list"], the same way delete-list-todo does, rather than keying on
        // ["list", todoItem.listID]. The list-detail endpoint returns ListTodoDto, which has no
        // listID field at all, so every row on a list screen carries listID === null — keying on
        // it wrote to ["list", null], a query no screen observes, and the ticked row simply never
        // left the list.
        void queryClient.cancelQueries({ queryKey: ["list"] });
        queryClient.setQueriesData<TodoItemType[]>(
            { queryKey: ["list"] },
            (oldTodos) => oldTodos?.filter((oldTodo) => oldTodo.id !== todoItem.id),
        );

        showTodoCompletedToast({
            commit: () => commitComplete(todoItem),
            undo: () => {
                // A row is coming BACK, so this list is not finished any more and the
                // celebration ends now rather than when its own window runs out.
                // Stamped here rather than left to the refetch below: that is a
                // network round trip, and `useArrivalCancel`'s count-rise backstop
                // cannot see the row until it lands.
                markCelebrationCancelled();
                // The server still has the row (incomplete) — a refetch restores
                // it, so the claim goes first: this refetch is meant to win, and
                // it covers every cache the claim reached; see
                // `releaseAndRestoreTodoRows`.
                releaseAndRestoreTodoRows(queryClient, [todoItem.id]);
            },
        });
    };

    return { completeMutateFn, completePending };
};

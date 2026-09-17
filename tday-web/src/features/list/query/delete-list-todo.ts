import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "@/hooks/use-toast";
import { api } from "@/lib/api-client";
import { canonicalTodoId } from "@/lib/todo/todo-id";
import { TodoItemType } from "@/types";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";
import { markTaskDeletedLocally } from "@/lib/task-completion-signal";
import {
    releaseAndRestoreTodoRows,
    stageTodoRows,
} from "@/lib/todo/staged-todo-rows";

// Delayed-commit delete: `deleteMutateFn` only stages the delete (prunes the
// caches and shows an undoable toast). The DELETE request fires when the toast
// closes without undo; undo just refetches since the server never saw it.
export const useDeleteListTodo = () => {
    const { toast } = useToast();
    const { showTodoDeletedToast } = useTodoActionToast();
    const queryClient = useQueryClient();
    const { mutate: commitDelete, isPending: deletePending } = useMutation({
        mutationFn: async (todo: TodoItemType) => {
            await api.DELETE({
                url: "/api/todo",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ id: canonicalTodoId(todo.id) }),
            });
        },
        mutationKey: ["list"],
        onError: (error) => {
            // No cache restore here: the row is still claimed by the guard at
            // this point, so an invalidate would only refetch a result the guard
            // then strips again. `onSettled` below releases the claim and
            // refetches every root, which is the refetch that is meant to win.
            toast({
                description:
                    error.message === "Failed to fetch"
                        ? "failed to connect to server"
                        : error.message,
                variant: "destructive",
            });
        },
        onSettled: (_data, _error, todo) => {
            // Sent (or failed) — every cache the claim reached has to be allowed
            // to answer for this row again; see `releaseAndRestoreTodoRows`.
            releaseAndRestoreTodoRows(queryClient, [todo.id]);
            queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
        },
    });

    // Stage: prune the caches now, but DON'T send the DELETE yet — the undo
    // toast decides whether the request ever fires.
    //
    // Same claim as its `todayTodos/query/delete-todo.ts` twin and for the same
    // reason — see `@/lib/todo/staged-todo-rows` — which is what the `["list"]`
    // root exists for.
    const deleteMutateFn = (todo: TodoItemType) => {
        // The empty state that follows the last row leaving reads this to tell
        // a list a task was deleted out of from one that was just finished.
        markTaskDeletedLocally();
        // Claim the row before the prune, so a refetch already in flight cannot
        // slip back in behind it.
        stageTodoRows(queryClient, [todo.id]);
        void queryClient.cancelQueries({ queryKey: ["list"] });
        queryClient.setQueriesData<TodoItemType[]>(
            { queryKey: ["list"] },
            (oldTodos) => {
                return oldTodos?.filter((oldTodo) => oldTodo.id != todo.id)
            }
        )

        showTodoDeletedToast(todo, {
            commit: () => commitDelete(todo),
            undo: () => {
                // The server still has the row — a refetch restores the pruned
                // caches, so the claim goes first: this refetch is meant to win.
                releaseAndRestoreTodoRows(queryClient, [todo.id]);
            },
        });
    };

    return { deleteMutateFn, deletePending };
};

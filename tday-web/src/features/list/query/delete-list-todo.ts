import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "@/hooks/use-toast";
import { api } from "@/lib/api-client";
import { canonicalTodoId } from "@/lib/todo/todo-id";
import { TodoItemType } from "@/types";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";

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
            // Restore the staged pruning — the row still exists on the server.
            queryClient.invalidateQueries({ queryKey: ["list"] });
            toast({
                description:
                    error.message === "Failed to fetch"
                        ? "failed to connect to server"
                        : error.message,
                variant: "destructive",
            });
        },
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
            queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
            queryClient.invalidateQueries({ queryKey: ["overdueTodo"] });
            queryClient.invalidateQueries({ queryKey: ["todo"] });
            queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
        },
    });

    // Stage: prune the caches now, but DON'T send the DELETE yet — the undo
    // toast decides whether the request ever fires.
    const deleteMutateFn = (todo: TodoItemType) => {
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
                // The server still has the row — a refetch restores the cache.
                void queryClient.invalidateQueries({ queryKey: ["list"] });
            },
        });
    };

    return { deleteMutateFn, deletePending };
};

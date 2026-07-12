import { useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { canonicalTodoId } from "@/lib/todo/todo-id";
import { TodoItemType } from "@/types";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";

// Delayed-commit complete (see complete-todo.ts): stage the removal from the
// list cache, show an undoable toast, and only PATCH /complete once the toast
// closes without undo.
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
                    instanceDate: todoItem.rrule ? todoItem.instanceDate?.getTime() : null,
                }),
            });
        },
        onError: (error) => {
            toast({ description: error.message, variant: "destructive" });
        },
        onSettled: (_data, _error, todoItem) => {
            queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
            queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
            queryClient.invalidateQueries({ queryKey: ["todo"] });
            queryClient.invalidateQueries({ queryKey: ["list", todoItem.listID] });
        },
    });

    const completeMutateFn = (todoItem: TodoItemType) => {
        void queryClient.cancelQueries({ queryKey: ["list", todoItem.listID] });
        queryClient.setQueryData<TodoItemType[]>(
            ["list", todoItem.listID],
            (oldTodos = []) => oldTodos.filter((oldTodo) => oldTodo.id !== todoItem.id),
        );

        showTodoCompletedToast({
            commit: () => commitComplete(todoItem),
            undo: () => {
                // The server still has the row (incomplete) — a refetch restores it.
                void queryClient.invalidateQueries({ queryKey: ["list", todoItem.listID] });
            },
        });
    };

    return { completeMutateFn, completePending };
};

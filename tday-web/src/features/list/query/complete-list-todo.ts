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
                    // Date, not epoch millis: the backend parses instanceDate as ISO-8601.
                    instanceDate: todoItem.rrule ? (todoItem.instanceDate ?? null) : null,
                }),
            });
        },
        onError: (error) => {
            toast({ description: error.message, variant: "destructive" });
        },
        onSettled: () => {
            queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
            queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
            queryClient.invalidateQueries({ queryKey: ["todo"] });
            queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
            // Prefix match, not ["list", <id>]: rows on a list screen carry no listID (see below).
            queryClient.invalidateQueries({ queryKey: ["list"] });
        },
    });

    const completeMutateFn = (todoItem: TodoItemType) => {
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
                // The server still has the row (incomplete) — a refetch restores it.
                void queryClient.invalidateQueries({ queryKey: ["list"] });
            },
        });
    };

    return { completeMutateFn, completePending };
};

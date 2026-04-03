import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "@/hooks/use-toast";
import { api } from "@/lib/api-client";
import { TodoItemType } from "@/types";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";
export const useDeleteListTodo = () => {
    const { toast } = useToast();
    const { showTodoDeletedToast } = useTodoActionToast();
    const queryClient = useQueryClient();
    const { mutate: deleteMutateFn, isPending: deletePending } = useMutation({
        mutationFn: async (todo: TodoItemType) => {
            await api.DELETE({
                url: "/api/todo",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ id: todo.id.split(":")[0] }),
            });
        },
        onMutate: async (todo: TodoItemType) => {
            await queryClient.cancelQueries({ queryKey: ["list"] });
            const oldTodos = queryClient.getQueriesData({ queryKey: ["list"] });
            //optimistically update todos
            queryClient.setQueriesData<TodoItemType[]>(
                { queryKey: ["list"] },
                (oldTodos) => {
                    return oldTodos?.filter((oldTodo) => oldTodo.id != todo.id)
                }
            )
            return { oldTodos };
        },
        mutationKey: ["list"],
        onError: (error, _, context) => {
            queryClient.setQueryData(["list"], context?.oldTodos);
            toast({
                description:
                    error.message === "Failed to fetch"
                        ? "failed to connect to server"
                        : error.message,
                variant: "destructive",
            });
        },
        onSettled: () => {
            //optimistically update calendar todos
            queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
            queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
            queryClient.invalidateQueries({ queryKey: ["overdueTodo"] });
            queryClient.invalidateQueries({ queryKey: ["todo"] });
            queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
        },
        onSuccess: (_data, deletedTodo) => {
            showTodoDeletedToast(deletedTodo);
        },
    });
    return { deleteMutateFn, deletePending };
};

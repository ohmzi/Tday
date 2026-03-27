import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "@/hooks/use-toast";
import { api } from "@/lib/api-client";
import { TodoItemType } from "@/types";
export const useDeleteListTodo = () => {
    const { toast } = useToast();
    const queryClient = useQueryClient();
    const { mutate: deleteMutateFn, isPending: deletePending } = useMutation({
        mutationFn: async ({ id }: { id: string }) => {
            await api.DELETE({
                url: "/api/todo",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ id: id.split(":")[0] }),
            });
        },
        onMutate: async ({ id }: { id: string }) => {
            await queryClient.cancelQueries({ queryKey: ["list"] });
            const oldTodos = queryClient.getQueriesData({ queryKey: ["list"] });
            //optimistically update todos
            queryClient.setQueriesData<TodoItemType[]>(
                { queryKey: ["list"] },
                (oldTodos) => {
                    return oldTodos?.filter((todo) => todo.id != id)
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
        },
        onSuccess: () => {
            toast({ description: "todo deleted" });
        },
    });
    return { deleteMutateFn, deletePending };
};

import { useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import { TodoItemType } from "@/types";
export const useCompleteListTodo = () => {
    const { toast } = useToast();
    const queryClient = useQueryClient();
    const { mutate: completeMutateFn, isPending: completePending } = useMutation({
        mutationFn: async (todoItem: TodoItemType) => {
            const todoId = todoItem.id.split(":")[0];
            await api.PATCH({
                url: "/api/todo/complete",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    id: todoId,
                    instanceDate: todoItem.rrule ? todoItem.instanceDate?.getTime() : null,
                }),
            });
        },
        onMutate: async (todoItem: TodoItemType) => {
            await queryClient.cancelQueries({ queryKey: ["list"] });
            const oldTodos = queryClient.getQueryData(["list"]) as TodoItemType[];
            queryClient.setQueryData(["list", todoItem.listID], (oldTodos: TodoItemType[]) => {
                return oldTodos.flatMap((oldTodo) => {

                    if (oldTodo.id === todoItem.id) return [];
                    return [oldTodo];
                })
            }
            );
            return { oldTodos };
        },
        onError: (error, _newTodo, context) => {
            toast({ description: error.message, variant: "destructive" });
            queryClient.setQueryData(["list"], context?.oldTodos);
        },
        onSettled: () => {
            //optimistically update calendar todos
            queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
            queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
            queryClient.invalidateQueries({ queryKey: ["todo"] });

        },
    });

    return { completeMutateFn, completePending };
};

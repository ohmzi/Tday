import { useToast } from "@/hooks/use-toast";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  patchTodo,
  type TodoItemPatchInput,
} from "@/lib/todo/patch-todo";
import { TodoItemType } from "@/types";

type TodoItemTypeWithChecksum = TodoItemPatchInput;

export const useEditCalendarTodo = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const { mutate: editCalendarTodo, status: editTodoStatus } = useMutation({
    mutationFn: (params: TodoItemTypeWithChecksum) =>
      patchTodo(params, {
        instanceDate: params.instanceDate?.getTime(),
        listID: params.listID ?? null,
      }),
    onMutate: async (newTodo) => {
      await queryClient.cancelQueries({
        queryKey: ["calendarTodo"],
      });
      const oldTodos = queryClient.getQueriesData({
        queryKey: ["calendarTodo"],
      });
      queryClient.setQueriesData<TodoItemType[]>(
        {
          queryKey: ["calendarTodo"],
        },
        (oldTodos) =>
          oldTodos?.map((oldTodo) => {
            if (oldTodo.id === newTodo.id) {
              return {
                ...oldTodo,
                completed: newTodo.completed,
                order: newTodo.order,
                pinned: newTodo.pinned,
                userID: newTodo.userID,
                id: newTodo.id,
                title: newTodo.title,
                description: newTodo.description,
                priority: newTodo.priority,
                due: newTodo.due,
                rrule: newTodo.rrule,
                createdAt: new Date(),
              };
            }
            return oldTodo;
          }),
      );
      return { oldTodos };
    },
    onSettled: () => {
      queryClient.invalidateQueries({
        queryKey: ["calendarTodo"],
      });
      queryClient.invalidateQueries({
        queryKey: ["todo"],
      });
      queryClient.invalidateQueries({
        queryKey: ["todoTimeline"],
      });
    },
    onError: (error, _newTodo, context) => {
      queryClient.setQueryData(["calendarTodo"], context?.oldTodos);
      toast({ description: error.message, variant: "destructive" });
    },
  });

  return { editCalendarTodo, editTodoStatus };
};

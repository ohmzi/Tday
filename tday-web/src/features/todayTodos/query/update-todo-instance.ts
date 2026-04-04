import { useToast } from "@/hooks/use-toast";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { todoInstanceSchema } from "@/schema";
import { TodoItemType } from "@/types";
import React from "react";
import { endOfDay } from "date-fns";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";

async function patchTodo({ ghostTodo }: { ghostTodo: TodoItemType }) {
  //validate input for the ghost todo
  const parsedObj = todoInstanceSchema.safeParse({
    title: ghostTodo.title,
    description: ghostTodo.description,
    priority: ghostTodo.priority,
    due: ghostTodo.due,
    rrule: ghostTodo.rrule,
    instanceDate: ghostTodo.instanceDate,
  });
  if (!parsedObj.success) {
    console.error(parsedObj.error.errors[0]);
    return;
  }
  const todoId = ghostTodo.id.split(":")[0];

  await api.PATCH({
    url: "/api/todo/instance",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ ...parsedObj.data, todoId }),
  });
}

export const useEditTodoInstance = (
  setEditInstanceOnly:
    | React.Dispatch<React.SetStateAction<boolean>>
    | undefined,
) => {
  const { toast } = useToast();
  const { showTodoUpdatedToast } = useTodoActionToast();
  const queryClient = useQueryClient();

  const { mutate: editTodoInstanceMutateFn, status: editTodoInstanceStatus } =
    useMutation({
      mutationFn: (ghostTodo: TodoItemType) => patchTodo({ ghostTodo }),
      onMutate: async (newTodo) => {
        await queryClient.cancelQueries({ queryKey: ["todo"] });
        await queryClient.cancelQueries({ queryKey: ["todoTimeline"] });
        const oldTodos = queryClient.getQueryData<TodoItemType[]>(["todo"]);
        const oldTimelineTodos = queryClient.getQueryData<TodoItemType[]>([
          "todoTimeline",
        ]);

        queryClient.setQueryData(["todo"], (oldTodos: TodoItemType[]) =>
          oldTodos.flatMap((oldTodo) => {
            if (oldTodo.id === newTodo.id) {
              if (newTodo.due > endOfDay(new Date())) {
                return [];
              }
              return {
                ...oldTodo,
                title: newTodo.title,
                description: newTodo.description,
                priority: newTodo.priority,
                due: newTodo.due,
              };
            }
            return oldTodo;
          }),
        );
        queryClient.setQueryData(["todoTimeline"], (oldTodos: TodoItemType[] = []) =>
          oldTodos.flatMap((oldTodo) => {
            if (oldTodo.id === newTodo.id) {
              return {
                ...oldTodo,
                title: newTodo.title,
                description: newTodo.description,
                priority: newTodo.priority,
                due: newTodo.due,
              };
            }
            return oldTodo;
          }),
        );
        return { oldTodos, oldTimelineTodos };
      },
      onSettled: () => {
        if (setEditInstanceOnly) setEditInstanceOnly(false);
        queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
        queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      },

      onError: (error, _newTodo, context) => {
        queryClient.setQueryData(["todo"], context?.oldTodos);
        queryClient.setQueryData(["todoTimeline"], context?.oldTimelineTodos);
        toast({ description: error.message, variant: "destructive" });
      },
      onSuccess: (_data, updatedTodo) => {
        showTodoUpdatedToast(updatedTodo);
      },
    });

  return { editTodoInstanceMutateFn, editTodoInstanceStatus };
};

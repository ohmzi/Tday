import { useToast } from "@/hooks/use-toast";
import { useQueryClient, useMutation } from "@tanstack/react-query";
import { todoSchema } from "@/schema";
import { api } from "@/lib/api-client";
import { TodoItemType } from "@/types";

async function postTodo({ todo }: { todo: TodoItemType }) {
  //validate input
  const parsedObj = todoSchema.safeParse({
    title: todo.title,
    description: todo.description,
    priority: todo.priority,
    dtstart: todo.dtstart,
    due: todo.due,
    rrule: todo.rrule,
    listID: todo.listID ?? null,
  });

  if (!parsedObj.success) {
    throw new Error(parsedObj.error.errors[0].message);
  }

  const res = await api.POST({
    url: "/api/todo",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(parsedObj.data),
  });

  //convert todo due from string to time
  res.todo.due = new Date(res.todo.due);
  res.todo.dtstart = new Date(res.todo.dtstart);

  return res.todo;
}

export const useCreateTodo = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { mutate: createMutateFn, status: createStatus } = useMutation({
    mutationFn: (todo: TodoItemType) => postTodo({ todo }),
    onMutate: async (newTodo) => {
      await queryClient.cancelQueries({ queryKey: ["todo"] });
      await queryClient.cancelQueries({ queryKey: ["todoTimeline"] });
      await queryClient.cancelQueries({ queryKey: ["list"] });

      const oldTodos = queryClient.getQueryData(["todo"]);
      const oldListTodos = queryClient.getQueryData<TodoItemType[]>([
        "list",
        newTodo.listID,
      ]);

      queryClient.setQueryData(["todo"], (old: TodoItemType[]) => [
        ...old,
        newTodo,
      ]);
      queryClient.setQueryData(["todoTimeline"], (old: TodoItemType[] = []) => [
        ...old,
        newTodo,
      ]);

      const targetListID = newTodo.listID;
      if (targetListID) {
        queryClient.setQueriesData(
          { queryKey: ["list", targetListID] },
          (old: TodoItemType[]) => {
            return [...old, newTodo];
          },
        );
      }

      return { oldTodos, oldListTodos };
    },
    //if fetch error then revert optimistic updates including form states
    onError: (error, _newTodo, context) => {
      queryClient.setQueryData(["todo"], context?.oldTodos);
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      queryClient.setQueryData(["list"], context?.oldListTodos);
      toast({ description: error.message, variant: "destructive" });
    },
    onSettled: (_, _error, newTodo) => {
      queryClient.invalidateQueries({ queryKey: ["todo"] });
      queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      queryClient.invalidateQueries({
        queryKey: ["list", newTodo.listID],
      });
      queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
    },
    onSuccess: (createdTodo: TodoItemType, newTodo) => {
      queryClient.setQueryData(["todo"], (old: TodoItemType[] = []) =>
        old.map((t) => (t.id === newTodo.id ? createdTodo : t)),
      );
      queryClient.setQueryData(["todoTimeline"], (old: TodoItemType[] = []) =>
        old.map((t) => (t.id === newTodo.id ? createdTodo : t)),
      );
      const targetListID = createdTodo.listID;
      if (targetListID) {
        queryClient.setQueryData(
          ["list", targetListID],
          (old: TodoItemType[] = []) =>
            old.map((t) => (t.id === newTodo.id ? createdTodo : t)),
        );
      }
      toast({ description: "todo created" });
    },
  });
  return { createMutateFn, createStatus };
};

import { useToast } from "@/hooks/use-toast";
import { useTodoActionToast } from "@/hooks/use-todo-action-toast";
import { useQueryClient, useMutation } from "@tanstack/react-query";
import { todoSchema } from "@/schema";
import { api } from "@/lib/api-client";
import { TodoItemType } from "@/types";
import parseApiDateTime from "@/lib/date/parseApiDateTime";

const normalizeTodo = (todo: TodoItemType) => {
  const instanceDate = todo.instanceDate
    ? parseApiDateTime(todo.instanceDate)
    : null;
  const instanceDateTime = instanceDate?.getTime();

  return {
    ...todo,
    id: `${todo.id}:${instanceDateTime}`,
    createdAt: parseApiDateTime(todo.createdAt),
    due: parseApiDateTime(todo.due),
    instanceDate,
    listID: todo.listID ?? null,
  };
};

async function postTodo({ todo }: { todo: TodoItemType }) {
  //validate input
  const parsedObj = todoSchema.safeParse({
    title: todo.title,
    description: todo.description,
    priority: todo.priority,
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
  return normalizeTodo(res.todo);
}

export const useCreateTodo = () => {
  const { toast } = useToast();
  const { showTodoCreatedToast } = useTodoActionToast();
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

      queryClient.setQueryData(["todo"], (old: TodoItemType[] = []) => [
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
          (old: TodoItemType[] = []) => {
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
      if (_newTodo.listID) {
        queryClient.setQueryData(["list", _newTodo.listID], context?.oldListTodos);
      }
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
      showTodoCreatedToast(createdTodo);
    },
  });
  return { createMutateFn, createStatus };
};

import { useCallback } from "react";
import { useToast } from "@/hooks/use-toast";
import { useRouter } from "@/lib/navigation";
import {
  buildScheduledFocusPath,
  buildTodoFocusPath,
} from "@/lib/todoToastNavigation";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { TodoItemType } from "@/types";

type TodoToastTarget = Pick<TodoItemType, "id" | "due">;
type TodoDateTarget = Pick<TodoItemType, "due">;

export function useTodoActionToast() {
  const { toast } = useToast();
  const router = useRouter();
  const userTZ = useUserTimezone();
  const timeZone = userTZ?.timeZone;

  const openTodo = useCallback((todo: TodoToastTarget) => {
    router.push(buildTodoFocusPath(todo, timeZone));
  }, [router, timeZone]);

  const openScheduledDate = useCallback((todo: TodoDateTarget) => {
    router.push(buildScheduledFocusPath(todo, timeZone));
  }, [router, timeZone]);

  const showTodoCreatedToast = useCallback((todo: TodoToastTarget) => {
    toast({
      description: "todo created",
      duration: 5000,
      onClick: () => openTodo(todo),
    });
  }, [openTodo, toast]);

  const showTodoUpdatedToast = useCallback((todo: TodoToastTarget) => {
    toast({
      description: "todo updated",
      duration: 5000,
      onClick: () => openTodo(todo),
    });
  }, [openTodo, toast]);

  const showTodoDeletedToast = useCallback((todo: TodoDateTarget) => {
    toast({
      description: "todo deleted",
      duration: 5000,
      onClick: () => openScheduledDate(todo),
    });
  }, [openScheduledDate, toast]);

  return {
    showTodoCreatedToast,
    showTodoUpdatedToast,
    showTodoDeletedToast,
  };
}

import { useCallback } from "react";
import { useToast } from "@/hooks/use-toast";
import { useRouter } from "@/lib/navigation";
import { buildScheduledFocusPath } from "@/lib/todoToastNavigation";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { TodoItemType } from "@/types";

type TodoDateTarget = Pick<TodoItemType, "due">;

export function useTodoActionToast() {
  const { toast } = useToast();
  const router = useRouter();
  const userTZ = useUserTimezone();
  const timeZone = userTZ?.timeZone;

  const openScheduledDate = useCallback((todo: TodoDateTarget) => {
    router.push(buildScheduledFocusPath(todo, timeZone));
  }, [router, timeZone]);

  // Unified toast policy: task create & edit succeed silently; only deletion
  // shows a success toast (failures are surfaced as error toasts at the mutation).
  const showTodoDeletedToast = useCallback((todo: TodoDateTarget) => {
    toast({
      description: "Task deleted",
      duration: 5000,
      onClick: () => openScheduledDate(todo),
    });
  }, [openScheduledDate, toast]);

  return {
    showTodoDeletedToast,
  };
}

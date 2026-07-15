import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { TaskStepType } from "@/types";

/**
 * Fetches the flat checklist of steps for a single todo. Steps are loaded on
 * demand inside the task editor (they are not part of the list payload), so
 * `enabled` gates the request until the editor actually needs them.
 */
export function useTaskSteps(todoId: string, enabled: boolean) {
  return useQuery<TaskStepType[]>({
    queryKey: ["taskSteps", todoId],
    queryFn: async () => {
      const data = (await api.GET({ url: `/api/todo/${todoId}/steps` })) as {
        steps: TaskStepType[];
      };
      return data.steps ?? [];
    },
    enabled,
  });
}

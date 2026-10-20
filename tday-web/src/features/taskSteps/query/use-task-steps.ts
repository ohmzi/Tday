import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { canonicalTodoId } from "@/lib/todo/todo-id";
import type { TaskStepType } from "@/types";

/**
 * Fetches the flat checklist of steps for a single todo. Steps are loaded on
 * demand inside the task editor (they are not part of the list payload), so
 * `enabled` gates the request until the editor actually needs them.
 *
 * The id is canonicalized first. List rows carry a composite `${id}:${instance}`
 * identity so occurrences stay distinct on screen, but the steps endpoints own a
 * todo, not an occurrence, and the backend matches the path id against `Todos.id`
 * — so a composite id 404s with "todo not found" moments after the editor opens.
 */
export function useTaskSteps(todoId: string, enabled: boolean) {
  const canonicalId = canonicalTodoId(todoId);
  return useQuery<TaskStepType[]>({
    queryKey: ["taskSteps", canonicalId],
    queryFn: async () => {
      const data = (await api.GET({ url: `/api/todo/${canonicalId}/steps` })) as {
        steps: TaskStepType[];
      };
      // `??` would only catch null/undefined; a server (or a stale cache) that
      // answered with a non-array would otherwise reach the checklist's own
      // `.filter`/`.map` and throw "is not a function" in the editor.
      return Array.isArray(data.steps) ? data.steps : [];
    },
    enabled,
  });
}

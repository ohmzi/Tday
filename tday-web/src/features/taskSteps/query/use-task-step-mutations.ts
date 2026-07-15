import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";

const JSON_HEADERS = { "Content-Type": "application/json" };

/**
 * Task-step mutations. None are optimistic — each simply invalidates the
 * ["taskSteps", todoId] cache on settle so the editor re-fetches the fresh
 * list. Every variables object carries `todoId` so we know which cache to
 * invalidate.
 */

export function useCreateTaskStep() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, { todoId: string; title: string }>({
    mutationFn: async ({ todoId, title }) => {
      await api.POST({
        url: "/api/todo/steps",
        headers: JSON_HEADERS,
        body: JSON.stringify({ todoId, title }),
      });
    },
    onSettled: (_data, _error, { todoId }) => {
      queryClient.invalidateQueries({ queryKey: ["taskSteps", todoId] });
    },
  });
}

export function useToggleTaskStep() {
  const queryClient = useQueryClient();

  return useMutation<
    void,
    Error,
    { id: string; completed: boolean; todoId: string }
  >({
    mutationFn: async ({ id, completed }) => {
      await api.POST({
        url: "/api/todo/steps/toggle",
        headers: JSON_HEADERS,
        body: JSON.stringify({ id, completed }),
      });
    },
    onSettled: (_data, _error, { todoId }) => {
      queryClient.invalidateQueries({ queryKey: ["taskSteps", todoId] });
    },
  });
}

export function useDeleteTaskStep() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, { id: string; todoId: string }>({
    mutationFn: async ({ id }) => {
      await api.POST({
        url: "/api/todo/steps/delete",
        headers: JSON_HEADERS,
        body: JSON.stringify({ id }),
      });
    },
    onSettled: (_data, _error, { todoId }) => {
      queryClient.invalidateQueries({ queryKey: ["taskSteps", todoId] });
    },
  });
}

export function useReorderTaskSteps() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, { todoId: string; orderedIds: string[] }>({
    mutationFn: async ({ todoId, orderedIds }) => {
      await api.POST({
        url: "/api/todo/steps/reorder",
        headers: JSON_HEADERS,
        body: JSON.stringify({ todoId, orderedIds }),
      });
    },
    onSettled: (_data, _error, { todoId }) => {
      queryClient.invalidateQueries({ queryKey: ["taskSteps", todoId] });
    },
  });
}

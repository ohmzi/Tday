import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";

/**
 * Resets a reusable floater list — the backend un-completes every floater in it.
 * Invalidates floaters + completed + list caches so the UI reflects the reset.
 */
export function useResetFloaterList() {
  const queryClient = useQueryClient();

  return useMutation<void, Error, { id: string }>({
    mutationFn: async ({ id }) => {
      await api.POST({
        url: `/api/floaterList/${id}/reset`,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ id }),
      });
    },
    onSettled: (_data, _error, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["floater"] });
      queryClient.invalidateQueries({ queryKey: ["floaterListMeta"] });
      queryClient.invalidateQueries({ queryKey: ["completedFloater"] });
      queryClient.invalidateQueries({ queryKey: ["floaterList", id] });
    },
  });
}

import { CompletedFloaterItemType } from "@/types";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import parseApiDateTime from "@/lib/date/parseApiDateTime";

/**
 * Durable floater completion history — the floater twin of `useCompletedTodo`.
 * Survives the source list being deleted (`listDeleted` on each row says so);
 * see `docs/design/completed-floaters-durability.md`.
 */
export const useCompletedFloater = () => {
  const {
    data: completedFloaters = [],
    isLoading: floaterLoading,
  } = useQuery<CompletedFloaterItemType[]>({
    queryKey: ["completedFloater"],
    retry: 2,
    staleTime: 5 * 60 * 1000,
    queryFn: async () => {
      const data = await api.GET({ url: `/api/completedFloater` });
      const { completedFloaters }: { completedFloaters: CompletedFloaterItemType[] } =
        data;
      if (!completedFloaters) {
        throw new Error(
          data.message || `bad server response: Did not recieve completed floaters`,
        );
      }

      const completedFloatersWithFormattedDates = completedFloaters.map(
        (floater) => ({
          ...floater,
          completedAt: parseApiDateTime(floater.completedAt),
        }),
      );

      return completedFloatersWithFormattedDates;
    },
  });

  return { completedFloaters, floaterLoading };
};

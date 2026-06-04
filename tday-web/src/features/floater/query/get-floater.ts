import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { FloaterItemType } from "@/types";
import { normalizeFloaters } from "./floater-utils";

export const useFloater = () => {
  const {
    data: floaters = [],
    isLoading: floaterLoading,
    isFetching,
    isPending,
  } = useQuery<FloaterItemType[]>({
    queryKey: ["floater"],
    retry: 2,
    staleTime: 5 * 60 * 1000,
    queryFn: async ({ signal }) => {
      const response: { floaters?: FloaterItemType[] } = await api.GET({
        url: "/api/floater",
        signal,
      });
      return normalizeFloaters(response.floaters ?? []);
    },
  });

  return { floaters, floaterLoading, isFetching, isPending };
};

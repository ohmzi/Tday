import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type {
  FloaterListItemMetaMapType,
  FloaterListItemMetaType,
} from "@/types";
import { normalizeFloaterLists } from "./floater-list-utils";

export const useFloaterListMetaData = () => {
  const {
    data: floaterListMetaData = {},
    isLoading: floaterListMetaLoading,
    isFetching,
    isPending,
  } = useQuery<FloaterListItemMetaMapType>({
    queryKey: ["floaterListMeta"],
    retry: 2,
    staleTime: 5 * 60 * 1000,
    queryFn: async ({ signal }) => {
      const response: { lists?: FloaterListItemMetaType[] } = await api.GET({
        url: "/api/floaterList",
        signal,
      });

      return Object.fromEntries(
        normalizeFloaterLists(response.lists ?? []).map(({ id, ...rest }) => [
          id,
          rest,
        ]),
      );
    },
  });

  return {
    floaterListMetaData,
    floaterListMetaLoading,
    isFetching,
    isPending,
  };
};

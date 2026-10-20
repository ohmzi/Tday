import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { FloaterItemType, FloaterListItemMetaType } from "@/types";
import { normalizeFloaters } from "@/features/floater/query/floater-utils";

export const useFloaterList = ({ id }: { id: string }) => {
  const {
    data,
    isLoading: floaterListLoading,
    isFetching,
    isPending,
  } = useQuery<{
    list: FloaterListItemMetaType | null;
    floaters: FloaterItemType[];
  }>({
    queryKey: ["floaterList", id],
    retry: 2,
    staleTime: 5 * 60 * 1000,
    queryFn: async ({ queryKey, signal }) => {
      const [, listID] = queryKey;
      const response = await api.GET({
        url: `/api/floaterList/${listID}`,
        signal,
      });
      return {
        list: response.list ?? null,
        floaters: normalizeFloaters(response.floaters ?? []),
      };
    },
  });

  return {
    floaterList: data?.list ?? null,
    floaterListTodos: data?.floaters ?? [],
    floaterListLoading,
    isFetching,
    isPending,
  };
};

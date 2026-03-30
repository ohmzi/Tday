import { ListItemMetaMapType, ListItemMetaType } from "@/types";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";

export const useListMetaData = () => {
  const {
    data: listMetaData = {},
    isLoading: listMetaLoading,
    isFetching,
    isPending,
  } = useQuery<ListItemMetaMapType>({
    queryKey: ["listMetaData"],
    retry: 2,
    staleTime: 5 * 60 * 1000,
    queryFn: async () => {
      const response: { lists?: ListItemMetaType[] } = await api.GET({
        url: "/api/list",
      });

      const lists = response.lists ?? [];

      const listMap = Object.fromEntries(
        lists.map(({ id, ...rest }) => [id, rest]),
      );
      return listMap;
    },
  });

  return {
    listMetaData,
    listMetaLoading,
    isFetching,
    isPending,
  };
};

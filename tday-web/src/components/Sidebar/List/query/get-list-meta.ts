import { ListItemMetaMapType, ListItemMetaType } from "@/types";
import { useQuery } from "@tanstack/react-query";
import { useEffect } from "react";
import { useToast } from "@/hooks/use-toast";
import { api } from "@/lib/api-client";

export const useListMetaData = () => {
  const { toast } = useToast();
  const {
    data: listMetaData = {},
    isLoading: listMetaLoading,
    isError,
    error,
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

  useEffect(() => {
    if (isError) {
      toast({ description: error.message, variant: "destructive" });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isError]);

  return {
    listMetaData,
    listMetaLoading,
    isFetching,
    isPending,
  };
};

import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";

export type UserSearchResultType = {
  id: string;
  username: string;
  name?: string | null;
};

// Username typeahead for the share-member picker. The server requires at
// least 2 characters and caps results at 10.
export const useSearchUsers = (query: string) => {
  const normalized = query.trim();
  const { data: users = [], isFetching: searchPending } = useQuery<UserSearchResultType[]>({
    queryKey: ["userSearch", normalized.toLowerCase()],
    enabled: normalized.length >= 2,
    staleTime: 30 * 1000,
    retry: 1,
    queryFn: async () => {
      const response: { users?: UserSearchResultType[] } = await api.GET({
        url: `/api/user/search?q=${encodeURIComponent(normalized)}`,
      });
      return response.users ?? [];
    },
  });

  return { users, searchPending };
};

import React, { createContext, useContext } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { SortBy, GroupBy, Direction } from "@/types/enums";
import { api } from "@/lib/api-client";

type UserPreferences = {
  id: string;
  userID: string;
  sortBy: SortBy | null;
  groupBy: GroupBy | null;
  direction: Direction | null;
};

type UserPreferencesContextType = {
  preferences: UserPreferences | null;
  updatePreferences: (newPrefs: Partial<Omit<UserPreferences, "id" | "userID">>) => void;
  isLoading: boolean;
  isPending: boolean;
};

const UserPreferencesContext = createContext<UserPreferencesContextType | undefined>(undefined);

async function fetchPreferences(): Promise<UserPreferences> {
  const data = await api.GET({ url: "/api/preferences" });
  return data.userPreferences;
}

async function updatePreferencesAPI(
  preferences: Partial<Omit<UserPreferences, "id" | "userID">>,
): Promise<UserPreferences> {
  const cleanedPrefs = Object.fromEntries(
    Object.entries(preferences).map(([key, value]) => [key, value === undefined ? null : value]),
  );
  const data = await api.PATCH({
    url: "/api/preferences",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(cleanedPrefs),
  });
  return data.userPreferences;
}

function UserPreferencesProviderInner({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient();

  const { data: preferences, isLoading } = useQuery({
    queryKey: ["userPreferences"],
    queryFn: fetchPreferences,
    staleTime: 5 * 60 * 1000,
  });

  const { mutate: updatePreferences, isPending } = useMutation({
    mutationFn: updatePreferencesAPI,
    onMutate: async (newPrefs) => {
      await queryClient.cancelQueries({ queryKey: ["userPreferences"] });
      const previousPreferences = queryClient.getQueryData<UserPreferences>(["userPreferences"]);
      if (previousPreferences) {
        const cleanedPrefs = Object.fromEntries(
          Object.entries(newPrefs).map(([key, value]) => [key, value === undefined ? null : value]),
        );
        queryClient.setQueryData<UserPreferences>(["userPreferences"], {
          ...previousPreferences,
          ...cleanedPrefs,
        });
      }
      return { previousPreferences };
    },
    onError: (_err, _newPrefs, context) => {
      if (context?.previousPreferences) {
        queryClient.setQueryData(["userPreferences"], context.previousPreferences);
      }
    },
    onSuccess: (data) => {
      queryClient.setQueryData(["userPreferences"], data);
    },
  });

  return (
    <UserPreferencesContext.Provider value={{ preferences: preferences || null, updatePreferences, isLoading, isPending }}>
      {children}
    </UserPreferencesContext.Provider>
  );
}

export function UserPreferencesProvider({ children }: { children: React.ReactNode }) {
  return <UserPreferencesProviderInner>{children}</UserPreferencesProviderInner>;
}

export function useUserPreferences() {
  const context = useContext(UserPreferencesContext);
  if (!context) throw new Error("useUserPreferences must be used within UserPreferencesProvider");
  return context;
}

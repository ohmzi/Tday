import React, { createContext, useContext } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { SortBy, GroupBy, Direction } from "@/types/enums";
import { api } from "@/lib/api-client";

type UserPreferences = {
  sortBy: SortBy | null;
  groupBy: GroupBy | null;
  direction: Direction | null;
  aiSummaryEnabled: boolean;
};

type UserPreferencesContextType = {
  preferences: UserPreferences | null;
  updatePreferences: (newPrefs: Partial<UserPreferences>) => void;
  isLoading: boolean;
  isPending: boolean;
};

const UserPreferencesContext = createContext<UserPreferencesContextType | undefined>(undefined);

// The /api/preferences response carries the canonical fields at the TOP LEVEL
// (sortBy/groupBy/direction). The legacy nested `userPreferences` object is
// always null — see PreferenceModels.kt — so read the top-level fields here.
function readPreferences(data: Record<string, unknown> | null): UserPreferences {
  return {
    sortBy: (data?.sortBy as SortBy | null) ?? null,
    groupBy: (data?.groupBy as GroupBy | null) ?? null,
    direction: (data?.direction as Direction | null) ?? null,
    // Default ON when absent — the AI summary feature is opt-out.
    aiSummaryEnabled: (data?.aiSummaryEnabled as boolean | undefined) ?? true,
  };
}

async function fetchPreferences(): Promise<UserPreferences> {
  const data = await api.GET({ url: "/api/preferences" });
  return readPreferences(data);
}

async function updatePreferencesAPI(
  preferences: Partial<UserPreferences>,
): Promise<UserPreferences> {
  const cleanedPrefs = Object.fromEntries(
    Object.entries(preferences).map(([key, value]) => [key, value === undefined ? null : value]),
  );
  const data = await api.PATCH({
    url: "/api/preferences",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(cleanedPrefs),
  });
  return readPreferences(data);
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

import React, { createContext, useContext } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { SortBy, GroupBy, Direction, DefaultHomeScreen } from "@/types/enums";
import { api } from "@/lib/api-client";

type UserPreferences = {
  sortBy: SortBy | null;
  groupBy: GroupBy | null;
  direction: Direction | null;
  aiSummaryEnabled: boolean;
  defaultHomeScreen: DefaultHomeScreen;
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
    // Default Scheduled when absent — matches the hardcoded launch behavior every
    // platform had before this preference existed.
    defaultHomeScreen:
      (data?.defaultHomeScreen as DefaultHomeScreen | undefined) ?? DefaultHomeScreen.scheduled,
  };
}

/**
 * The same payload, but carrying ONLY the fields the response actually holds.
 *
 * A PATCH answers with the stored preferences, so this normally returns the whole record.
 * A body with none of them is not a preferences payload at all — an acknowledgement such as
 * `{ message: "preferences updated" }`, which is what the route used to send and what Local
 * Mode still sends (see `lib/local/localApi.ts`) — and yields `{}`, which the caller merges
 * as a no-op. Substituting [readPreferences]'s own defaults here instead is what snapped the
 * "Default home screen" thumb back to Scheduled on the tick the write succeeded.
 */
function readPreferencesPatch(data: Record<string, unknown> | null): Partial<UserPreferences> {
  if (!data) return {};
  const patch: Partial<UserPreferences> = {};
  if ("sortBy" in data) patch.sortBy = (data.sortBy as SortBy | null) ?? null;
  if ("groupBy" in data) patch.groupBy = (data.groupBy as GroupBy | null) ?? null;
  if ("direction" in data) patch.direction = (data.direction as Direction | null) ?? null;
  if ("aiSummaryEnabled" in data) {
    patch.aiSummaryEnabled = (data.aiSummaryEnabled as boolean | undefined) ?? true;
  }
  if ("defaultHomeScreen" in data) {
    patch.defaultHomeScreen =
      (data.defaultHomeScreen as DefaultHomeScreen | undefined) ?? DefaultHomeScreen.scheduled;
  }
  return patch;
}

async function fetchPreferences(): Promise<UserPreferences> {
  const data = await api.GET({ url: "/api/preferences" });
  return readPreferences(data);
}

async function updatePreferencesAPI(
  preferences: Partial<UserPreferences>,
): Promise<Partial<UserPreferences>> {
  const cleanedPrefs = Object.fromEntries(
    Object.entries(preferences).map(([key, value]) => [key, value === undefined ? null : value]),
  );
  const data = await api.PATCH({
    url: "/api/preferences",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(cleanedPrefs),
  });
  return readPreferencesPatch(data);
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
      // MERGE the response over what is in the cache NOW — which is the optimistic value
      // `onMutate` just wrote, not the pre-mutation snapshot `onError` would put back —
      // rather than replacing the cache with the response. The response is the stored record
      // and normally says the same thing the optimistic write already said; a response that
      // carries no preference fields at all (older server, Local Mode) contributes nothing
      // and the optimistic value stands. Replacing the cache outright is what made the
      // control spring back: the body it wrote was the acknowledgement, and every field it
      // could not find was filled with a default.
      const current = queryClient.getQueryData<UserPreferences>(["userPreferences"]);
      if (!current) return;
      queryClient.setQueryData<UserPreferences>(["userPreferences"], {
        ...current,
        ...data,
      });
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

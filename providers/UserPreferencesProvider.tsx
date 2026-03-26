"use client"

import React, { createContext, useContext } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { SortBy, GroupBy, Direction } from '@prisma/client';

type UserPreferences = {
    sortBy: SortBy | null;
    groupBy: GroupBy | null;
    direction: Direction | null;
};

type UserPreferencesContextType = {
    preferences: UserPreferences | null;
    updatePreferences: (newPrefs: Partial<UserPreferences>) => void;
    isLoading: boolean;
    isPending: boolean;
};

const UserPreferencesContext = createContext<UserPreferencesContextType | undefined>(undefined);

type PreferencesResponse = {
    sortBy?: SortBy | null;
    groupBy?: GroupBy | null;
    direction?: Direction | null;
    userPreferences?: Partial<UserPreferences> | null;
};

function normalizePreferences(data: PreferencesResponse): UserPreferences {
    const preferences = data.userPreferences ?? data;

    return {
        sortBy: preferences.sortBy ?? null,
        groupBy: preferences.groupBy ?? null,
        direction: preferences.direction ?? null,
    };
}

async function fetchPreferences(): Promise<UserPreferences> {
    const res = await fetch('/api/preferences');
    if (!res.ok) throw new Error('Failed to fetch preferences');
    const data = await res.json() as PreferencesResponse;
    return normalizePreferences(data);
}

async function updatePreferencesAPI(preferences: Partial<UserPreferences>): Promise<UserPreferences> {
    const cleanedPrefs = Object.fromEntries(
        Object.entries(preferences).map(([key, value]) => [key, value === undefined ? null : value])
    );

    const res = await fetch('/api/preferences', {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(cleanedPrefs),
    });

    if (!res.ok) throw new Error('Failed to update preferences');
    const data = await res.json() as PreferencesResponse & { message?: string };

    if (data.userPreferences || data.sortBy !== undefined || data.groupBy !== undefined || data.direction !== undefined) {
        return normalizePreferences(data);
    }

    return fetchPreferences();
}

function UserPreferencesProviderInner({
    children,
}: {
    children: React.ReactNode;
}) {
    const queryClient = useQueryClient();

    const { data: preferences, isLoading } = useQuery({
        queryKey: ['userPreferences'],
        queryFn: fetchPreferences,
        staleTime: 5 * 60 * 1000,
    });

    const { mutate: updatePreferences, isPending } = useMutation({
        mutationFn: updatePreferencesAPI,
        onMutate: async (newPrefs) => {
            // Cancel outgoing refetches
            await queryClient.cancelQueries({ queryKey: ['userPreferences'] });

            // Snapshot the previous value
            const previousPreferences = queryClient.getQueryData<UserPreferences>(['userPreferences']);

            // Optimistically update to the new value
            if (previousPreferences) {
                const cleanedPrefs = Object.fromEntries(
                    Object.entries(newPrefs).map(([key, value]) => [key, value === undefined ? null : value])
                );
                queryClient.setQueryData<UserPreferences>(['userPreferences'], {
                    ...previousPreferences,
                    ...cleanedPrefs,
                });
            }
            return { previousPreferences };
        },
        onError: (err, _newPrefs, context) => {
            // Rollback on error
            if (context?.previousPreferences) {
                queryClient.setQueryData(['userPreferences'], context.previousPreferences);
            }
            console.error('Failed to update preferences:', err);
        },
        onSuccess: (data) => {
            // Update with server response
            queryClient.setQueryData(['userPreferences'], data);
        },
    });

    return (
        <UserPreferencesContext.Provider value={{
            preferences: preferences || null,
            updatePreferences,
            isLoading,
            isPending
        }}>
            {children}
        </UserPreferencesContext.Provider>
    );
}

export function UserPreferencesProvider({
    children,
}: {
    children: React.ReactNode;
}) {
    return (
        <UserPreferencesProviderInner>
            {children}
        </UserPreferencesProviderInner>
    );
}

export function useUserPreferences() {
    const context = useContext(UserPreferencesContext);
    if (!context) {
        throw new Error('useUserPreferences must be used within UserPreferencesProvider');
    }
    return context;
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useToast } from "@/hooks/use-toast";
import type { ListMemberType, ShareRoleType } from "@/types";

// Both list domains expose the same member-management API surface; the hooks
// switch the base path so one sheet component serves scheduled lists and
// floater lists.
export type ShareListKind = "list" | "floaterList";

export type ListMembersType = {
  owner: ListMemberType;
  members: ListMemberType[];
};

const metaQueryKey = (kind: ShareListKind) =>
  kind === "list" ? "listMetaData" : "floaterListMetaData";

export const useListMembers = (kind: ShareListKind, listId: string, enabled: boolean) => {
  const { data: members, isLoading: membersLoading } = useQuery<ListMembersType>({
    queryKey: ["listMembers", kind, listId],
    enabled,
    retry: 1,
    queryFn: async () =>
      (await api.GET({ url: `/api/${kind}/${listId}/members` })) as ListMembersType,
  });

  return { members, membersLoading };
};

const useInvalidateMembers = (kind: ShareListKind, listId: string) => {
  const queryClient = useQueryClient();
  return async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["listMembers", kind, listId] }),
      queryClient.invalidateQueries({ queryKey: [metaQueryKey(kind)] }),
    ]);
  };
};

export const useAddListMember = (kind: ShareListKind, listId: string) => {
  const { toast } = useToast();
  const invalidate = useInvalidateMembers(kind, listId);
  const { mutateAsync: addMemberMutateFn, isPending: addMemberPending } = useMutation({
    mutationFn: async ({ username, role }: { username: string; role: ShareRoleType }) => {
      await api.POST({
        url: `/api/${kind}/${listId}/members`,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, role }),
      });
    },
    onSuccess: invalidate,
    onError: (error) => {
      toast({ description: error.message, variant: "destructive" });
    },
  });
  return { addMemberMutateFn, addMemberPending };
};

export const useUpdateListMemberRole = (kind: ShareListKind, listId: string) => {
  const { toast } = useToast();
  const invalidate = useInvalidateMembers(kind, listId);
  const { mutate: updateRoleMutateFn, isPending: updateRolePending } = useMutation({
    mutationFn: async ({ userId, role }: { userId: string; role: ShareRoleType }) => {
      await api.PATCH({
        url: `/api/${kind}/${listId}/members`,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ userId, role }),
      });
    },
    onSuccess: invalidate,
    onError: (error) => {
      toast({ description: error.message, variant: "destructive" });
    },
  });
  return { updateRoleMutateFn, updateRolePending };
};

export const useRemoveListMember = (kind: ShareListKind, listId: string) => {
  const { toast } = useToast();
  const invalidate = useInvalidateMembers(kind, listId);
  const { mutate: removeMemberMutateFn, isPending: removeMemberPending } = useMutation({
    mutationFn: async ({ userId }: { userId: string }) => {
      await api.DELETE({
        url: `/api/${kind}/${listId}/members`,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ userId }),
      });
    },
    onSuccess: invalidate,
    onError: (error) => {
      toast({ description: error.message, variant: "destructive" });
    },
  });
  return { removeMemberMutateFn, removeMemberPending };
};

export const useLeaveList = (kind: ShareListKind, listId: string) => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { mutateAsync: leaveListMutateFn, isPending: leaveListPending } = useMutation({
    mutationFn: async () => {
      await api.POST({
        url: `/api/${kind}/${listId}/leave`,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({}),
      });
    },
    onSuccess: async () => {
      // Leaving drops the list and all its tasks from every feed.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: [metaQueryKey(kind)] }),
        queryClient.invalidateQueries({ queryKey: ["todo"] }),
        queryClient.invalidateQueries({ queryKey: ["todoTimeline"] }),
        queryClient.invalidateQueries({ queryKey: ["overdueTodo"] }),
        queryClient.invalidateQueries({ queryKey: ["floater"] }),
        queryClient.invalidateQueries({ queryKey: ["floaterList"] }),
        queryClient.invalidateQueries({ queryKey: ["list"] }),
        queryClient.invalidateQueries({ queryKey: ["calendarTodo"] }),
      ]);
    },
    onError: (error) => {
      toast({ description: error.message, variant: "destructive" });
    },
  });
  return { leaveListMutateFn, leaveListPending };
};

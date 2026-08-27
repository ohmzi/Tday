import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Crown, Share2, UserPlus, X } from "lucide-react";
import AppBottomSheet from "@/components/ui/AppBottomSheet";
import { Input } from "@/components/ui/input";
import { SheetCard, SheetSectionTitle } from "@/components/ui/sheet-chrome";
import { cn } from "@/lib/utils";
import { hapticTick } from "@/lib/haptics";
import {
  type ShareListKind,
  useAddListMember,
  useLeaveList,
  useListMembers,
  useRemoveListMember,
  useUpdateListMemberRole,
} from "@/features/list/query/share-members";
import { type UserSearchResultType, useSearchUsers } from "@/features/user/query/search-users";
import type { ListMemberType, ShareRoleType } from "@/types";

type ManageMembersSheetProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  listId: string;
  listType: ShareListKind;
  listName: string;
  myRole: ShareRoleType;
  // Non-owners reach the external text share from here (owners have it in the
  // edit sheet's Sharing section).
  onShareExternal?: () => void;
};

const MEMBER_ROLES: ShareRoleType[] = ["EDITOR", "VIEWER"];

/**
 * Avatar letter for a member or search-result row: the first character of the display name, or
 * of the username when there is no name. Both rows show the same person, so they share this.
 */
function avatarInitial(person: { username: string; name?: string | null }) {
  const source = person.name?.trim() || person.username;
  return source.slice(0, 1).toUpperCase();
}

/**
 * The sheet behind a shared list's "Members" action: the current roster, plus — for the owner —
 * a username typeahead for adding people, and for everyone else the leave-list flow.
 */
export default function ManageMembersSheet({
  open,
  onOpenChange,
  listId,
  listType,
  listName,
  myRole,
  onShareExternal,
}: ManageMembersSheetProps) {
  const { t: appDict } = useTranslation("app");
  const isOwner = myRole === "OWNER";

  const { members, membersLoading } = useListMembers(listType, listId, open);
  const { addMemberMutateFn, addMemberPending } = useAddListMember(listType, listId);
  const { updateRoleMutateFn } = useUpdateListMemberRole(listType, listId);
  const { removeMemberMutateFn } = useRemoveListMember(listType, listId);
  const { leaveListMutateFn, leaveListPending } = useLeaveList(listType, listId);

  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [confirmingLeave, setConfirmingLeave] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(search), 250);
    return () => window.clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    if (!open) return;
    setSearch("");
    setDebouncedSearch("");
    setConfirmingLeave(false);
  }, [open]);

  const { users: searchResults, searchPending } = useSearchUsers(
    isOwner && open ? debouncedSearch : "",
  );
  // People already on the list stay in the results as a disabled row. Filtering them out
  // instead made the sheet answer "No users found" for an account that exists, is approved and
  // is right there in the list above — which reads as the search being broken.
  const memberIds = new Set(
    [members?.owner.userId, ...(members?.members ?? []).map((member) => member.userId)].filter(
      Boolean,
    ),
  );

  const handleAdd = async (username: string) => {
    hapticTick();
    await addMemberMutateFn({ username, role: "EDITOR" });
    setSearch("");
    setDebouncedSearch("");
  };

  const handleLeave = async () => {
    await leaveListMutateFn();
    onOpenChange(false);
  };

  const renderRoleControl = (member: ListMemberType) => {
    if (!isOwner) {
      return (
        <span className="rounded-full bg-muted/70 px-2.5 py-1 text-xs font-black text-muted-foreground">
          {appDict(member.role === "EDITOR" ? "roleEditor" : "roleViewer")}
        </span>
      );
    }
    return (
      <div className="flex items-center rounded-full bg-muted/60 p-0.5">
        {MEMBER_ROLES.map((role) => (
          <button
            key={role}
            type="button"
            onClick={() => {
              if (member.role === role) return;
              hapticTick();
              updateRoleMutateFn({ userId: member.userId, role });
            }}
            className={cn(
              "rounded-full px-2.5 py-1 text-xs font-black transition-colors",
              member.role === role
                ? "bg-card text-foreground shadow-sm"
                : "text-muted-foreground",
            )}
            aria-pressed={member.role === role}
          >
            {appDict(role === "EDITOR" ? "roleEditor" : "roleViewer")}
          </button>
        ))}
      </div>
    );
  };

  const renderMemberRow = (member: ListMemberType, isOwnerRow: boolean) => (
    <div key={member.userId} className="flex items-center gap-3 px-1 py-2">
      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-accent/15 text-sm font-black text-accent">
        {avatarInitial(member)}
      </span>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-black text-foreground">
          {member.name?.trim() || member.username}
        </p>
        <p className="truncate text-xs font-bold text-muted-foreground">@{member.username}</p>
      </div>
      {isOwnerRow ? (
        <span className="flex items-center gap-1 rounded-full bg-amber-500/15 px-2.5 py-1 text-xs font-black text-amber-600 dark:text-amber-400">
          <Crown className="h-3.5 w-3.5" />
          {appDict("roleOwner")}
        </span>
      ) : (
        <>
          {renderRoleControl(member)}
          {isOwner ? (
            <button
              type="button"
              onClick={() => {
                hapticTick();
                removeMemberMutateFn({ userId: member.userId });
              }}
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-muted/60 text-muted-foreground transition-colors hover:bg-destructive/10 hover:text-destructive"
              aria-label={appDict("removeMember")}
            >
              <X className="h-4 w-4 stroke-[2.6]" />
            </button>
          ) : null}
        </>
      )}
    </div>
  );

  /**
   * One row of the username typeahead. Someone already on the list renders dimmed with a static
   * "already a member" badge in place of the add button, rather than being dropped from the
   * results — see the note on `memberIds` above.
   */
  const renderSearchResultRow = (user: UserSearchResultType) => {
    const alreadyMember = memberIds.has(user.id);
    return (
      <div
        key={user.id}
        className={cn("flex items-center gap-3 px-1 py-2", alreadyMember && "opacity-60")}
      >
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-muted/70 text-sm font-black text-muted-foreground">
          {avatarInitial(user)}
        </span>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-black text-foreground">
            {user.name?.trim() || user.username}
          </p>
          <p className="truncate text-xs font-bold text-muted-foreground">@{user.username}</p>
        </div>
        {alreadyMember ? (
          <span className="flex h-8 items-center rounded-full bg-muted/70 px-3 text-xs font-black text-muted-foreground">
            {appDict("alreadyAMember")}
          </span>
        ) : (
          <button
            type="button"
            disabled={addMemberPending}
            onClick={() => void handleAdd(user.username)}
            className="flex h-8 items-center gap-1.5 rounded-full bg-accent/15 px-3 text-xs font-black text-accent transition-colors hover:bg-accent/25 disabled:opacity-50"
          >
            <UserPlus className="h-3.5 w-3.5 stroke-[2.6]" />
            {appDict("addMember")}
          </button>
        )}
      </div>
    );
  };

  return (
    <AppBottomSheet
      variant="native"
      open={open}
      onOpenChange={onOpenChange}
      title={appDict("members")}
      onClose={() => onOpenChange(false)}
      onConfirm={() => onOpenChange(false)}
      confirmLabel={appDict("done")}
      closeLabel={appDict("cancel")}
    >
      <div className="flex flex-col gap-3 pb-2">
        <SheetSectionTitle>{listName}</SheetSectionTitle>
        <SheetCard className="px-3 py-1.5">
          {membersLoading ? (
            <div className="space-y-2 p-2">
              <div className="h-10 animate-pulse rounded-2xl bg-muted/70" />
              <div className="h-10 animate-pulse rounded-2xl bg-muted/70" />
            </div>
          ) : members ? (
            <div className="divide-y divide-border/50">
              {renderMemberRow(members.owner, true)}
              {members.members.map((member) => renderMemberRow(member, false))}
            </div>
          ) : null}
        </SheetCard>

        {isOwner ? (
          <>
            <SheetSectionTitle>{appDict("addMember")}</SheetSectionTitle>
            <SheetCard className="p-3.5">
              <Input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder={appDict("searchUsersPlaceholder")}
                autoCapitalize="none"
                autoCorrect="off"
                className="h-12 rounded-2xl border-transparent bg-muted/60 font-bold focus-visible:ring-0"
              />
              {debouncedSearch.trim().length >= 2 ? (
                <div className="mt-2 divide-y divide-border/50">
                  {searchResults.map(renderSearchResultRow)}
                  {!searchPending && searchResults.length === 0 ? (
                    <p className="px-1 py-2 text-sm font-bold text-muted-foreground">
                      {appDict("noUsersFound")}
                    </p>
                  ) : null}
                </div>
              ) : null}
            </SheetCard>
          </>
        ) : confirmingLeave ? (
          <div className="rounded-2xl border border-destructive/30 bg-destructive/5 p-4">
            <p className="text-sm font-extrabold text-destructive">
              {appDict("leaveListConfirm", { name: listName })}
            </p>
            <div className="mt-3 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
              <button
                type="button"
                onClick={() => setConfirmingLeave(false)}
                className="rounded-2xl border border-border/70 bg-card px-5 py-2.5 text-sm font-black"
              >
                {appDict("cancel")}
              </button>
              <button
                type="button"
                disabled={leaveListPending}
                onClick={() => void handleLeave()}
                className="rounded-2xl bg-destructive px-5 py-2.5 text-sm font-black text-destructive-foreground disabled:opacity-50"
              >
                {appDict("leaveList")}
              </button>
            </div>
          </div>
        ) : (
          <>
            {onShareExternal ? (
              <button
                type="button"
                onClick={() => {
                  onOpenChange(false);
                  onShareExternal();
                }}
                className="flex w-full items-center justify-center gap-2 rounded-2xl border border-border/70 bg-muted/60 px-5 py-2.5 text-sm font-black text-foreground transition-colors hover:bg-muted active:scale-[0.99]"
              >
                <Share2 className="h-4 w-4 stroke-[2.4]" />
                {appDict("share")}
              </button>
            ) : null}
            <button
              type="button"
              onClick={() => setConfirmingLeave(true)}
              className="flex w-full items-center justify-center gap-2 rounded-2xl border border-destructive/30 bg-destructive/5 px-5 py-2.5 text-sm font-black text-destructive transition-colors hover:bg-destructive/10 active:scale-[0.99]"
            >
              {appDict("leaveList")}
            </button>
          </>
        )}
      </div>
    </AppBottomSheet>
  );
}

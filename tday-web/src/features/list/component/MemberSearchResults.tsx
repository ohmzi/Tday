import { useTranslation } from "react-i18next";
import { UserPlus } from "lucide-react";
import { cn } from "@/lib/utils";
import type { UserSearchResultType } from "@/features/user/query/search-users";

type MemberSearchResultsProps = {
  users: UserSearchResultType[];
  /** User ids already on the list — the owner plus every current member. */
  memberIds: ReadonlySet<string>;
  searchPending: boolean;
  addPending: boolean;
  onAdd: (username: string) => void;
};

/** Avatar letter for a result row: first character of the display name, or of the username. */
function resultInitial(user: UserSearchResultType) {
  const source = user.name?.trim() || user.username;
  return source.slice(0, 1).toUpperCase();
}

/**
 * The result list of the share-member typeahead.
 *
 * People already on the list stay in the results as a dimmed, non-actionable row. Filtering them
 * out instead made the sheet answer "No users found" for an account that exists, is approved and
 * is right there in the member list above — which reads as the search being broken.
 */
export default function MemberSearchResults({
  users,
  memberIds,
  searchPending,
  addPending,
  onAdd,
}: MemberSearchResultsProps) {
  const { t: appDict } = useTranslation("app");

  const renderRow = (user: UserSearchResultType) => {
    const alreadyMember = memberIds.has(user.id);
    return (
      <div
        key={user.id}
        className={cn("flex items-center gap-3 px-1 py-2", alreadyMember && "opacity-60")}
      >
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-muted/70 text-sm font-black text-muted-foreground">
          {resultInitial(user)}
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
            disabled={addPending}
            onClick={() => onAdd(user.username)}
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
    <div className="mt-2 divide-y divide-border/50">
      {users.map(renderRow)}
      {!searchPending && users.length === 0 ? (
        <p className="px-1 py-2 text-sm font-bold text-muted-foreground">
          {appDict("noUsersFound")}
        </p>
      ) : null}
    </div>
  );
}

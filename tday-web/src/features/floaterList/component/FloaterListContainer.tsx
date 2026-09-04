import { useMemo, useState } from "react";
import { Pencil, RotateCcw, Search, Users } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { useResetFloaterList } from "@/features/floaterList/query/reset-floater-list";
import { useTranslation } from "react-i18next";
import ManageMembersSheet from "@/features/list/component/ManageMembersSheet";
import SummaryButton from "@/features/summary/SummaryButton";
import NativePageHeader, { useNativePageBarSlots } from "@/components/app/NativePageHeader";
import ScreenWatermark from "@/components/app/ScreenWatermark";
import EmptyState from "@/components/app/EmptyState";
import { taskJustCompleted } from "@/lib/task-completion-signal";
import { useCelebrateEmptyTransition } from "@/hooks/use-celebrate-empty-transition";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import { useShareListAsText } from "@/hooks/use-share-list";
import { useIsLocalMode } from "@/hooks/useAppMode";
import { Button } from "@/components/ui/button";
import { getListIcon } from "@/lib/listIcons";
import { listColorAccentColors, nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import FloaterGroup from "@/features/floater/component/FloaterGroup";
import { buildFloaterSections } from "@/lib/floater/buildFloaterSections";
import { useFloaterList } from "@/features/floaterList/query/get-floater-list";
import { useFloaterListMetaData } from "@/features/floaterList/query/get-floater-list-meta";
import FloaterListFormSheet from "./FloaterListFormSheet";
import { flattenNotesToPlainText } from "@/lib/richNotes";

export default function FloaterListContainer({ id }: { id: string }) {
  const { t: appDict } = useTranslation("app");
  const { toast } = useToast();
  const isLocalMode = useIsLocalMode();
  const resetList = useResetFloaterList();
  const { floaterListMetaData } = useFloaterListMetaData();
  const { floaterList, floaterListTodos, floaterListLoading } = useFloaterList({ id });
  const [searchQuery, setSearchQuery] = useState("");
  const [editListOpen, setEditListOpen] = useState(false);
  const [membersOpen, setMembersOpen] = useState(false);
  // Remote sibling of `taskJustCompleted()` below — fires for a completion on
  // another device or by a collaborator, not just this tab's own tap.
  const remoteEmptied = useCelebrateEmptyTransition(floaterListTodos.length === 0);

  const listMeta = floaterListMetaData[id] ?? floaterList;
  const listName = listMeta?.name?.trim() || "";
  const listColor = listMeta?.color;
  const listAccent = listColor
    ? listColorAccentColors[listColor]
    : nativeScreenAccentColors.floater;
  const ListIcon = getListIcon(listMeta?.iconKey);
  const editableList = listMeta
    ? {
        id,
        name: listMeta.name,
        color: listMeta.color,
        iconKey: listMeta.iconKey,
      }
    : null;
  const myRole = listMeta && "myRole" in listMeta ? (listMeta.myRole ?? "OWNER") : "OWNER";
  const isViewer = myRole === "VIEWER";
  const sharedByLabel =
    listMeta && "ownerUsername" in listMeta ? listMeta.ownerUsername : null;
  const shareListAsText = useShareListAsText({ listName, todos: floaterListTodos });

  const filteredFloaters = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return floaterListTodos;
    return floaterListTodos.filter((floater) => {
      return (
        floater.title.toLowerCase().includes(query) ||
        flattenNotesToPlainText(floater.description).toLowerCase().includes(query)
      );
    });
  }, [floaterListTodos, searchQuery]);

  const sections = useMemo(
    () => buildFloaterSections(filteredFloaters),
    [filteredFloaters],
  );
  const isSearching = Boolean(searchQuery.trim());
  // This page keeps its search bar as the pinned bar, so the header renders only
  // the block that scrolls away and docks its title into that bar instead.
  const barSlots = useNativePageBarSlots();

  return (
    <div className="mb-20">
      <ScreenWatermark icon={ListIcon} color={listAccent} />
      <MobileSearchHeader
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        placeholder={
          listName ? `${appDict("searchIn")} ${listName}...` : appDict("searchFloatersPlaceholder")
        }
        pageCollapse={{ ...barSlots, title: listName, accentColor: listAccent }}
        // An empty list has nothing for a query to narrow, and the button would
        // only raise a keyboard over the empty-state scene. The unsearched set,
        // so a word that matches nothing keeps the field.
        searchUnavailable={!floaterListLoading && floaterListTodos.length === 0}
        trailingAction={
          <div className="flex shrink-0 items-center gap-1.5">
          {/* 48px here, against the 56px of the back button and the search
              toggle beside them. Deliberate, and the one bar in the app that
              mixes: this is the only screen carrying five controls, and at 56
              they come to 280px of circle in a 288px bar on a 320px phone. The
              sibling custom list has four and keeps them all at 56. The gaps
              count into that sum too: 256px of circle plus the bar's own two
              10px gaps leaves 12px for this cluster's two, hence 6px and not
              the 8px they read as elsewhere — at 8px the last circle sat 4px
              into the page's 16px gutter, out of line with every row beneath
              it. */}
          {/* Same gate the native list screens use: a summary is only offered
              where there is something to summarize. */}
          {floaterListTodos.length > 0 ? (
            <SummaryButton mode="floater" listId={id} className="h-12 w-12" />
          ) : null}
          {listMeta?.reusable && !isViewer ? (
            <Button
              type="button"
              variant="ghost"
              size="icon"
              disabled={resetList.isPending}
              className="h-12 w-12 shrink-0 rounded-full border border-white/70 bg-card/90 text-foreground shadow-[0_14px_30px_-16px_hsl(var(--shadow)/0.6)] hover:bg-card dark:border-white/10"
              onClick={() => {
                resetList.mutate(
                  { id },
                  { onSuccess: () => toast({ description: appDict("floaterListReset") }) },
                );
              }}
              aria-label={appDict("resetFloaterList")}
            >
              <RotateCcw className="h-6 w-6 stroke-[2.6]" />
            </Button>
          ) : null}
          {editableList ? (
            // One entry point per role: owners get the edit sheet (which hosts
            // the Sharing section); members go straight to the members sheet.
            <Button
              type="button"
              variant="ghost"
              size="icon"
              className="h-12 w-12 shrink-0 rounded-full border border-white/70 bg-card/90 text-foreground shadow-[0_14px_30px_-16px_hsl(var(--shadow)/0.6)] hover:bg-card dark:border-white/10"
              onClick={() =>
                myRole === "OWNER" ? setEditListOpen(true) : setMembersOpen(true)
              }
              aria-label={
                myRole === "OWNER"
                  ? `${appDict("editFloaterList")} ${listName}`
                  : appDict("members")
              }
            >
              {myRole === "OWNER" ? (
                <Pencil className="h-6 w-6 stroke-[2.6]" />
              ) : (
                <Users className="h-6 w-6 stroke-[2.6]" />
              )}
            </Button>
          ) : null}
          </div>
        }
      />

      <NativePageHeader
        title={listName}
        accentColor={listAccent}
        icon={ListIcon}
        barSlots={barSlots}
        beneathTitle={
          sharedByLabel ? (
            <p className="mt-1 flex items-center gap-1.5 px-1 text-xs font-black text-muted-foreground">
              <Users className="h-3.5 w-3.5" />
              {appDict("sharedBy", { name: sharedByLabel })}
            </p>
          ) : null
        }
      />

      {floaterListLoading ? (
        <div className="space-y-3 px-1 py-6">
          <div className="h-6 w-36 animate-pulse rounded-full bg-muted" />
          <div className="h-16 animate-pulse rounded-2xl bg-muted/70" />
          <div className="h-16 animate-pulse rounded-2xl bg-muted/70" />
        </div>
      ) : null}

      {!floaterListLoading && !isSearching && floaterListTodos.length === 0 ? (
        <EmptyState
          icon={ListIcon}
          accentColor={listAccent}
          title={appDict("floaterListEmpty")}
          description={appDict("floaterListEmptyBody")}
          // Finishing a list is a payoff, not an absence: the confetti is for
          // the tick that emptied it, not for a list that was already empty.
          // Whether that tick happened here, on another device, or from a
          // collaborator on this shared list.
          celebrate={taskJustCompleted() || remoteEmptied}
        />
      ) : null}

      {!floaterListLoading && isSearching && filteredFloaters.length === 0 ? (
        <EmptyState
          icon={Search}
          accentColor={listAccent}
          title={appDict("noMatchingFloaters")}
          description={appDict("searchEmptyBody")}
          action={
            <button
              type="button"
              onClick={() => setSearchQuery("")}
              className="rounded-full border border-border/60 bg-card px-5 py-2.5 text-sm font-black text-foreground shadow-[0_14px_30px_-16px_hsl(var(--shadow)/0.6)] transition-transform hover:-translate-y-0.5"
            >
              {appDict("clearSearch")}
            </button>
          }
        />
      ) : null}

      {!floaterListLoading && sections.length > 0 ? (
        <div className="space-y-5">
          {sections.map((section) => (
            <section key={section.id} className="space-y-1">
              <h2 className="px-1 text-[1.75rem] font-black leading-8 text-foreground">
                {appDict(section.labelKey)}
              </h2>
              <FloaterGroup floaters={section.items} readOnly={isViewer} />
            </section>
          ))}
        </div>
      ) : null}

      <FloaterListFormSheet
        open={editListOpen}
        onOpenChange={setEditListOpen}
        list={editableList}
        // Collaborators need accounts; a local workspace has none, so the
        // Members entry disappears while plain-text sharing stays.
        onManageMembers={isLocalMode ? undefined : () => setMembersOpen(true)}
        onShareList={() => void shareListAsText()}
      />
      <ManageMembersSheet
        open={membersOpen}
        onOpenChange={setMembersOpen}
        listId={id}
        listType="floaterList"
        listName={listName}
        myRole={myRole}
        onShareExternal={() => void shareListAsText()}
      />
    </div>
  );
}

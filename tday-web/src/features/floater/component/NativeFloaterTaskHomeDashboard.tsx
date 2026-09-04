import { useMemo, useState } from "react";
import { CheckCircle, Leaf, Search } from "lucide-react";
import { useTranslation } from "react-i18next";
import ScreenWatermark from "@/components/app/ScreenWatermark";
import EmptyState from "@/components/app/EmptyState";
import { taskJustCompleted } from "@/lib/task-completion-signal";
import { useCelebrateEmptyTransition } from "@/hooks/use-celebrate-empty-transition";
import { Link, useRouter } from "@/lib/navigation";
import { cn } from "@/lib/utils";
import { sortFloatersByPriority } from "@/lib/floater/buildFloaterSections";
import { getListIcon } from "@/lib/listIcons";
import {
  listColorAccentColors,
  nativeScreenAccentColors,
} from "@/components/app/nativeScreenTheme";
import { useFloater } from "@/features/floater/query/get-floater";
import { useFloaterListMetaData } from "@/features/floaterList/query/get-floater-list-meta";
import { useCompletedFloater } from "@/features/completed/query/get-completedFloater";
import FloaterGroup from "./FloaterGroup";
import FloaterListFormSheet from "@/features/floaterList/component/FloaterListFormSheet";
import { flattenNotesToPlainText } from "@/lib/richNotes";

import RootFeedHeroHeader from "@/components/app/RootFeedHeroHeader";
function renderTileOverlay() {
  return (
    <>
      <div className="pointer-events-none absolute -left-14 -top-20 h-44 w-52 rounded-full bg-white/20 blur-2xl" />
      <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(135deg,rgba(255,255,255,0.12),rgba(231,243,255,0.10)_45%,rgba(255,242,250,0.08)_68%,transparent)]" />
    </>
  );
}

export default function NativeFloaterTaskHomeDashboard() {
  const router = useRouter();
  const { t: appDict } = useTranslation("app");
  const { floaters, floaterLoading } = useFloater();
  const { floaterListMetaData } = useFloaterListMetaData();
  const { completedFloaters } = useCompletedFloater();
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [createListOpen, setCreateListOpen] = useState(false);
  const floaterAccent = nativeScreenAccentColors.floater;

  const listCounts = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const floater of floaters) {
      if (floater.completed || !floater.listID) continue;
      counts[floater.listID] = (counts[floater.listID] ?? 0) + 1;
    }
    return counts;
  }, [floaters]);

  const lists = useMemo(() => {
    // Show every named list — including ones with no tasks yet — to match the
    // native apps. Keep the natural (metadata) order so lists don't reshuffle
    // as their task counts change.
    return Object.entries(floaterListMetaData)
      .filter(([, list]) => Boolean(list.name?.trim()))
      .map(([id, list]) => ({ id, ...list, count: listCounts[id] ?? 0 }));
  }, [floaterListMetaData, listCounts]);

  const filteredFloaters = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return floaters;
    return floaters.filter((floater) => {
      const listName = floater.listID
        ? floaterListMetaData[floater.listID]?.name ?? ""
        : "";
      return (
        floater.title.toLowerCase().includes(query) ||
        flattenNotesToPlainText(floater.description).toLowerCase().includes(query) ||
        listName.toLowerCase().includes(query)
      );
    });
  }, [floaterListMetaData, floaters, searchQuery]);

  const sortedFloaters = useMemo(
    () => sortFloatersByPriority(filteredFloaters),
    [filteredFloaters],
  );
  const isSearching = Boolean(searchQuery.trim());
  const hasFloaters = floaters.some((floater) => !floater.completed);
  // Remote sibling of `taskJustCompleted()` below — fires for a completion on
  // another device or by a collaborator, not just this tab's own tap.
  const remoteEmptied = useCelebrateEmptyTransition(!hasFloaters);

  return (
    <>
      <ScreenWatermark icon={Leaf} color={floaterAccent} />
      <div className="flex w-full flex-col gap-4 sm:gap-5">
        <RootFeedHeroHeader
          title={appDict("floater")}
          mark="floaterLeaf"
          searchOpen={searchOpen}
          searchQuery={searchQuery}
          searchPlaceholder={appDict("searchFloaterTasksPlaceholder")}
          searchPlaceholderShort={appDict("searchShort")}
          searchAriaLabel={appDict("searchFloaters")}
          createListAriaLabel={appDict("newFloaterList")}
          settingsAriaLabel="Settings"
          onSearchQueryChange={setSearchQuery}
          onSearchOpenChange={(open) => {
            setSearchOpen(open);
            setSearchQuery("");
          }}
          onCreateList={() => setCreateListOpen(true)}
          onOpenSettings={() => router.push("/app/settings")}
          results={
            isSearching ? (
              <div className="max-h-[60vh] overflow-y-auto rounded-[28px] border border-white/70 bg-card/95 p-2 shadow-[0_16px_36px_-30px_hsl(var(--shadow)/0.55)] dark:border-white/10">
                {sortedFloaters.length === 0 ? (
                  <p className="px-3 py-4 text-sm font-extrabold text-muted-foreground">
                    {appDict("floaterEmpty")}
                  </p>
                ) : (
                  sortedFloaters.map((floater) => (
                    <button
                      type="button"
                      key={floater.id}
                      className="flex w-full items-center gap-3 rounded-2xl px-3 py-2 text-left transition-colors hover:bg-muted/65"
                      onClick={() => router.push(`/app/todo?todo=${encodeURIComponent(floater.id)}`)}
                    >
                      <span
                        className="h-2.5 w-2.5 shrink-0 rounded-full"
                        style={{ backgroundColor: floaterAccent }}
                      />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-black text-foreground">
                          {floater.title}
                        </span>
                        {floater.listID && floaterListMetaData[floater.listID]?.name ? (
                          <span className="block truncate text-xs font-extrabold text-muted-foreground">
                            {floaterListMetaData[floater.listID]?.name}
                          </span>
                        ) : null}
                      </span>
                    </button>
                  ))
                )}
              </div>
            ) : null
          }
        />

        <section
          className="relative flex h-[70px] items-center justify-between overflow-hidden rounded-[26px] px-5 text-white shadow-[0_14px_30px_-18px_rgba(50,90,130,0.62)]"
          style={{ backgroundColor: floaterAccent }}
        >
          {renderTileOverlay()}
          <span className="relative truncate text-[1.38rem] font-black leading-none tracking-tight">
            {appDict("floater")}
          </span>
          <span className="relative text-[2.1rem] font-black leading-none">
            {floaters.filter((floater) => !floater.completed).length}
          </span>
        </section>

        {/* The only nav path from the Floater tab into its own durable
            completion history — the Todo side reaches the same screen via the
            sidebar/More sheet, neither of which surfaces here. Opens straight
            into the Floater tab of that screen. */}
        <Link
          href="/app/completed?scope=floater"
          className={cn(
            "relative flex h-[70px] items-center gap-3 overflow-hidden rounded-[26px] px-5 text-white",
            "shadow-[0_14px_30px_-20px_rgba(60,70,90,0.55)] transition-transform duration-200",
            "hover:-translate-y-0.5 active:translate-y-0.5",
          )}
          style={{ backgroundColor: nativeScreenAccentColors.completed }}
        >
          {renderTileOverlay()}
          <CheckCircle className="relative h-6 w-6 shrink-0 stroke-[2.5]" />
          <span className="relative min-w-0 flex-1 truncate text-[1.1rem] font-black">
            {appDict("floaterCompletedTile")}
          </span>
          <span className="relative text-2xl font-black leading-none">
            {completedFloaters.length}
          </span>
        </Link>

        {floaterLoading ? (
          <div className="space-y-3 px-1 py-6">
            <div className="h-6 w-36 animate-pulse rounded-full bg-muted" />
            <div className="h-16 animate-pulse rounded-2xl bg-muted/70" />
            <div className="h-16 animate-pulse rounded-2xl bg-muted/70" />
          </div>
        ) : null}

        {!floaterLoading && !hasFloaters && !isSearching ? (
          <EmptyState
            icon={Leaf}
            accentColor={floaterAccent}
            title={appDict("floaterEmpty")}
            description={appDict("floaterEmptyBody")}
            // Finishing the feed is a payoff, not an absence: the confetti is
            // for the tick that emptied it, not for an empty Anytime feed.
            // Whether that tick happened here, on another device, or from a
            // collaborator on a shared list.
            celebrate={taskJustCompleted() || remoteEmptied}
          />
        ) : null}

        {!floaterLoading && isSearching && sortedFloaters.length === 0 ? (
          <EmptyState
            icon={Search}
            accentColor={floaterAccent}
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

        {!floaterLoading && sortedFloaters.length > 0 ? (
          <FloaterGroup floaters={sortedFloaters} reorderable={false} />
        ) : null}

        {lists.length > 0 ? (
          <section className="space-y-2 pb-16 pt-6">
            <h2 className="px-1 text-[1.75rem] font-black leading-8 text-foreground">
              {appDict("myFloaterLists")}
            </h2>
            <div className="space-y-2">
              {lists.map((list) => {
                const color = list.color
                  ? listColorAccentColors[list.color]
                  : floaterAccent;
                const ListIcon = getListIcon(list.iconKey);
                return (
                  <Link
                    key={list.id}
                    href={`/app/floater-list/${list.id}`}
                    className={cn(
                      "relative flex min-h-[66px] items-center gap-3 overflow-hidden rounded-[24px] px-4 text-white",
                      "shadow-[0_14px_30px_-20px_rgba(60,70,90,0.55)] transition-transform duration-200",
                      "hover:-translate-y-0.5 active:translate-y-0.5",
                    )}
                    style={{ backgroundColor: color }}
                  >
                    {renderTileOverlay()}
                    <ListIcon className="relative h-6 w-6 shrink-0 stroke-[2.5]" />
                    <span className="relative min-w-0 flex-1 truncate text-[1.1rem] font-black">
                      {list.name}
                    </span>
                    <span className="relative text-2xl font-black leading-none">
                      {list.count}
                    </span>
                  </Link>
                );
              })}
            </div>
          </section>
        ) : null}
      </div>

      <FloaterListFormSheet
        open={createListOpen}
        onOpenChange={setCreateListOpen}
        onSaved={(list) => {
          if (list?.id) router.push(`/app/floater-list/${list.id}`);
        }}
      />
    </>
  );
}

import { useMemo, useState } from "react";
import { CheckCircle, Leaf, Search } from "lucide-react";
import { useTranslation } from "react-i18next";
import ScreenWatermark from "@/components/app/ScreenWatermark";
import EmptyState from "@/components/app/EmptyState";
import EmptyStateSlot from "@/components/app/EmptyStateSlot";
import { useFloaterEmptyState } from "@/features/floater/lib/useFloaterEmptyState";
import { useRowPlacement } from "@/hooks/useRowPlacement";
import { DELAY_MS } from "@/lib/motion";
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
import { TaskRowSkeletonGroup } from "@/components/ui/TaskRowSkeleton";
import { useSkeletonCrossfade } from "@/hooks/useSkeletonCrossfade";
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
  // The feed hands over to its rows instead of swapping to them in one frame;
  // the exit class lives on the wrapper, never on the pulsing bars inside it.
  const { showSkeleton, skeletonClassName } = useSkeletonCrossfade(floaterLoading);
  const { floaterListMetaData } = useFloaterListMetaData();
  const { completedFloaters } = useCompletedFloater();
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [createListOpen, setCreateListOpen] = useState(false);
  const placementRef = useRowPlacement<HTMLDivElement>();
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
  // Counted rather than tested for existence, because the cancel needs the
  // number and not the boolean: a row ARRIVING is what ends a celebration, and
  // on a feed that is not empty on either side of an undo the boolean does not
  // move. The tile below reads the same count instead of recomputing it.
  const pendingFloaterCount = useMemo(
    () => floaters.filter((floater) => !floater.completed).length,
    [floaters],
  );
  // Empty/celebration/cancel, shared with the Anytime list screen rather than
  // derived twice — see `useFloaterEmptyState`, which is where the undo that
  // brings a row back ends the burst flying over it.
  const { showEmpty, celebrate, sceneLeavingOnCancel } = useFloaterEmptyState({
    isLoading: floaterLoading,
    isSearching,
    // Search-immune: a query that hides every task is not a finished feed, and
    // a task ARRIVING behind one still ends a celebration.
    pendingRowCount: pendingFloaterCount,
  });

  return (
    <>
      <ScreenWatermark icon={Leaf} color={floaterAccent} />
      {/* The same travel the scheduled task home's column got, for the worse version of
          its problem. Ticking the last Anytime task swaps a one-row feed for the
          `min-h-[42vh]` empty state, so the lists below drop by most of a screen — and
          they did it in the frame the confetti fired, which put the biggest uncued jump
          in the app underneath the one moment nobody is looking at the layout. Now the
          tiles glide down first and the celebration waits for them; see the empty
          state's own `celebrationStartDelayMs` below for the ordering. */}
      <div ref={placementRef} className="flex w-full flex-col gap-4 sm:gap-5">
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
                      className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left transition-colors hover:bg-muted/65"
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
            {pendingFloaterCount}
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
            "shadow-[0_14px_30px_-20px_rgba(60,70,90,0.55)] transition-transform duration-enter",
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

        {/* Held one `Quick` past the frame the feed refilled, because the burst
            inside is still fading and this element is what it is painted into —
            an undo that unmounts the scene cuts the fade one layer down, which
            is the same complaint the fade exists to answer. The slot closes its
            42vh track under that fade too, so the tiles above and the lists
            below take the space back over the beat rather than in the frame the
            node goes. */}
        {showEmpty || sceneLeavingOnCancel ? (
          <EmptyStateSlot leavingOnCancel={sceneLeavingOnCancel}>
            <EmptyState
              icon={Leaf}
              accentColor={floaterAccent}
              title={appDict("floaterEmpty")}
              description={appDict("floaterEmptyBody")}
              // Finishing the feed is a payoff, not an absence: the confetti is
              // for the tick that emptied it, not for an empty Anytime feed.
              // Whether that tick happened here, on another device, or from a
              // collaborator on a shared list — and it ENDS the moment a task
              // comes back, however it got here.
              celebrate={celebrate}
              // This scene is drawn INLINE: mounting it is what pushes the tiles
              // above down, so the travel and the burst would otherwise be the
              // same beat. `PlacementLead` is the token for exactly that wait —
              // it is `Emphasis` by construction, so it cannot drift away from
              // the placement it is here to outlast — and holding the whole
              // celebration back by it leaves the confetti's own lead intact:
              // travel, then burst, then scene. The same order Android gets from
              // `TdayFeedItemMotion.CelebrationStartDelayMillis`.
              celebrationStartDelayMs={DELAY_MS.placementLead}
            />
          </EmptyStateSlot>
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

        {/* Placeholder and rows in one column child, because the column is a flex box with a
            gap: a second child holding the fading placeholder would keep 16 px of gap open
            for the length of the fade and then drop it, which is a step this row exists to
            remove. The exiting skeleton releases its own height immediately and paints over
            the rows that have taken the slot.

            What arrives here is a bare `FloaterGroup` — no section label above it — so the
            pill this used to draw stood in for a heading that never comes, and its two
            64 px cards stood in for a flat row of 62. `TaskRowSkeletonGroup` is the feed's
            own row geometry, and it is the same primitive the sibling list screen loads
            behind: two root feeds that load differently are two root feeds. */}
        {showSkeleton || (!floaterLoading && sortedFloaters.length > 0) ? (
          <div>
            {showSkeleton ? (
              <div className={skeletonClassName}>
                <TaskRowSkeletonGroup />
              </div>
            ) : null}
            {!floaterLoading && sortedFloaters.length > 0 ? (
              <FloaterGroup
                floaters={sortedFloaters}
                reorderable={false}
                className="tday-content-enter"
              />
            ) : null}
          </div>
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
                      "shadow-[0_14px_30px_-20px_rgba(60,70,90,0.55)] transition-transform duration-enter",
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

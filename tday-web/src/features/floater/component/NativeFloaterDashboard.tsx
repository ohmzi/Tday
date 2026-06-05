import { useMemo, useState } from "react";
import {
  Ellipsis,
  Leaf,
  ListPlus,
  Search,
  X,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import ScreenWatermark from "@/components/app/ScreenWatermark";
import NativeAppBrandButton from "@/components/app/NativeAppBrandButton";
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
import FloaterGroup from "./FloaterGroup";
import FloaterListFormSheet from "@/features/floaterList/component/FloaterListFormSheet";

const topButtonClass =
  "flex h-14 w-14 items-center justify-center rounded-full border border-white/70 bg-card/90 text-foreground shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)] transition-all duration-200 hover:-translate-y-0.5 hover:bg-card active:translate-y-0 dark:border-white/10";

function renderTileOverlay() {
  return (
    <>
      <div className="pointer-events-none absolute -left-14 -top-20 h-44 w-52 rounded-full bg-white/20 blur-2xl" />
      <div className="pointer-events-none absolute inset-0 bg-[linear-gradient(135deg,rgba(255,255,255,0.12),rgba(231,243,255,0.10)_45%,rgba(255,242,250,0.08)_68%,transparent)]" />
    </>
  );
}

export default function NativeFloaterDashboard() {
  const router = useRouter();
  const { t: appDict } = useTranslation("app");
  const { floaters, floaterLoading } = useFloater();
  const { floaterListMetaData } = useFloaterListMetaData();
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
        (floater.description ?? "").toLowerCase().includes(query) ||
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

  return (
    <>
      <ScreenWatermark icon={Leaf} color={floaterAccent} />
      <div className="flex w-full flex-col gap-4 sm:gap-5">
        <header className="relative flex min-h-14 items-center justify-between gap-3">
          <NativeAppBrandButton className="min-w-0" />

          <div className="flex shrink-0 items-center gap-2">
            <button
              type="button"
              className={topButtonClass}
              onClick={() => {
                setSearchOpen((value) => !value);
                setSearchQuery("");
              }}
              aria-label={appDict("searchFloaters")}
            >
              {searchOpen ? <X className="h-5 w-5" /> : <Search className="h-5 w-5" />}
            </button>
            <button
              type="button"
              className={topButtonClass}
              onClick={() => setCreateListOpen(true)}
              aria-label={appDict("newFloaterList")}
            >
              <ListPlus className="h-5 w-5" />
            </button>
            <button
              type="button"
              className={topButtonClass}
              onClick={() => router.push("/app/settings")}
              aria-label="Settings"
            >
              <Ellipsis className="h-5 w-5" />
            </button>
          </div>
        </header>

        {searchOpen ? (
          <section className="rounded-[24px] border border-white/70 bg-card/92 p-3 shadow-[0_16px_36px_-30px_hsl(var(--shadow)/0.55)] dark:border-white/10">
            <div className="relative">
              <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <input
                autoFocus
                type="search"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder={appDict("searchFloatersPlaceholder")}
                className="h-12 w-full rounded-2xl border border-border/70 bg-muted/55 pl-11 pr-4 text-base font-extrabold outline-none transition-colors focus:border-accent/50 focus:bg-card md:text-sm"
              />
            </div>
          </section>
        ) : null}

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

        {floaterLoading ? (
          <div className="space-y-3 px-1 py-6">
            <div className="h-6 w-36 animate-pulse rounded-full bg-muted" />
            <div className="h-16 animate-pulse rounded-2xl bg-muted/70" />
            <div className="h-16 animate-pulse rounded-2xl bg-muted/70" />
          </div>
        ) : null}

        {!floaterLoading && !hasFloaters && !isSearching ? (
          <div className="flex min-h-[42vh] flex-col items-center justify-center text-center">
            <p className="text-2xl font-black text-muted-foreground/70">
              {appDict("floaterEmpty")}
            </p>
          </div>
        ) : null}

        {!floaterLoading && isSearching && sortedFloaters.length === 0 ? (
          <div className="mx-auto flex min-h-[45vh] max-w-md flex-col items-center justify-center text-center">
            <div className="relative mb-6">
              <div className="flex h-24 w-24 items-center justify-center rounded-full bg-muted/50">
                <Search className="h-12 w-12 text-muted-foreground/50" />
              </div>
              <div className="absolute -right-1 -top-1 flex h-6 w-6 items-center justify-center rounded-full border-2 border-background bg-accent/20">
                <X className="h-3 w-3 text-accent" />
              </div>
            </div>
            <h3 className="mb-2 text-2xl font-semibold text-foreground">
              {appDict("noMatchingFloaters")}
            </h3>
            <button
              onClick={() => setSearchQuery("")}
              className="text-sm font-black text-accent hover:underline"
            >
              {appDict("clearSearch")}
            </button>
          </div>
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

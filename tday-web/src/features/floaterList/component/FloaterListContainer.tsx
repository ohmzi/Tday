import { useMemo, useState } from "react";
import { Pencil, Search, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import NativePageTitle from "@/components/app/NativePageTitle";
import ScreenWatermark from "@/components/app/ScreenWatermark";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import { Button } from "@/components/ui/button";
import { getListIcon } from "@/lib/listIcons";
import { listColorAccentColors, nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import FloaterGroup from "@/features/floater/component/FloaterGroup";
import { buildFloaterSections } from "@/lib/floater/buildFloaterSections";
import { useFloaterList } from "@/features/floaterList/query/get-floater-list";
import { useFloaterListMetaData } from "@/features/floaterList/query/get-floater-list-meta";
import FloaterListFormSheet from "./FloaterListFormSheet";
import FloaterListDot from "./FloaterListDot";

export default function FloaterListContainer({ id }: { id: string }) {
  const { t: appDict } = useTranslation("app");
  const { floaterListMetaData } = useFloaterListMetaData();
  const { floaterList, floaterListTodos, floaterListLoading } = useFloaterList({ id });
  const [searchQuery, setSearchQuery] = useState("");
  const [editListOpen, setEditListOpen] = useState(false);

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

  const filteredFloaters = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return floaterListTodos;
    return floaterListTodos.filter((floater) => {
      return (
        floater.title.toLowerCase().includes(query) ||
        (floater.description ?? "").toLowerCase().includes(query)
      );
    });
  }, [floaterListTodos, searchQuery]);

  const sections = useMemo(
    () => buildFloaterSections(filteredFloaters),
    [filteredFloaters],
  );
  const isSearching = Boolean(searchQuery.trim());

  return (
    <div className="mb-20">
      <ScreenWatermark icon={ListIcon} color={listAccent} />
      <MobileSearchHeader
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        placeholder={
          listName ? `${appDict("searchIn")} ${listName}...` : appDict("searchFloatersPlaceholder")
        }
      />

      <div className="flex items-start justify-between gap-3">
        <NativePageTitle
          title={listName}
          accentColor={listAccent}
          iconNode={<FloaterListDot id={id} className="h-7 w-7 shrink-0" />}
          className="min-w-0 flex-1"
        />
        {editableList ? (
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="mt-4 h-12 w-12 shrink-0 rounded-full border border-white/70 bg-card/90 text-foreground shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)] hover:bg-card dark:border-white/10"
            onClick={() => setEditListOpen(true)}
            aria-label={`${appDict("editFloaterList")} ${listName}`}
          >
            <Pencil className="h-5 w-5" />
          </Button>
        ) : null}
      </div>

      {floaterListLoading ? (
        <div className="space-y-3 px-1 py-6">
          <div className="h-6 w-36 animate-pulse rounded-full bg-muted" />
          <div className="h-16 animate-pulse rounded-2xl bg-muted/70" />
          <div className="h-16 animate-pulse rounded-2xl bg-muted/70" />
        </div>
      ) : null}

      {!floaterListLoading && !isSearching && floaterListTodos.length === 0 ? (
        <div className="flex min-h-[42vh] flex-col items-center justify-center text-center">
          <p className="text-2xl font-black text-muted-foreground/70">
            {appDict("floaterListEmpty")}
          </p>
        </div>
      ) : null}

      {!floaterListLoading && isSearching && filteredFloaters.length === 0 ? (
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

      {!floaterListLoading && sections.length > 0 ? (
        <div className="space-y-5">
          {sections.map((section) => (
            <section key={section.id} className="space-y-1">
              <h2 className="px-1 text-[1.75rem] font-black leading-8 text-foreground">
                {appDict(section.labelKey)}
              </h2>
              <FloaterGroup floaters={section.items} />
            </section>
          ))}
        </div>
      ) : null}

      <FloaterListFormSheet
        open={editListOpen}
        onOpenChange={setEditListOpen}
        list={editableList}
      />
    </div>
  );
}

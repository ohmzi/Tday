import React, { useMemo, useState } from "react";
import TodoListLoading from "@/components/ui/TodoListLoading";
import { useCompletedFloater } from "../query/get-completedFloater";
import { useGroupedHistory } from "../hooks/useGroupedHistory";
import GroupedCompletedFloaterContainer from "./GroupedFloaterContainer";
import { useTranslation } from "react-i18next";
import NativePageHeader, { useNativePageBarSlots } from "@/components/app/NativePageHeader";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import ScreenWatermark from "@/components/app/ScreenWatermark";
import EmptyState from "@/components/app/EmptyState";
import { nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import { CheckCircle, Search } from "lucide-react";
import { flattenNotesToPlainText } from "@/lib/richNotes";
import type { CompletedFloaterItemType } from "@/types";

const floaterAccent = nativeScreenAccentColors.floater;

// The floater twin of CompletedTodoContainer.tsx — same shell (header, search,
// date-grouped rows), reading the durable completed-floater history instead of
// completed todos. Kept as its own self-contained container (rather than a
// prop-driven variant of the todo one) so the two can be switched between as
// whole screens from CompletedContainer without either depending on the
// other's internals.
const CompletedFloaterContainer = ({
  tabSwitcher,
}: {
  /**
   * The Tasks/Floater segmented control CompletedContainer owns — handed down
   * so it renders under the title (NativePageHeader's beneathTitle slot) on
   * whichever of the two sibling screens is currently mounted.
   */
  tabSwitcher?: React.ReactNode;
}) => {
  const { t: completedDict } = useTranslation("completed");
  const { t: appDict } = useTranslation("app");
  const { completedFloaters, floaterLoading } = useCompletedFloater();
  const [searchQuery, setSearchQuery] = useState("");

  const filteredFloaters = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return completedFloaters;
    return completedFloaters.filter((floater) => {
      const title = floater.title.toLowerCase();
      const description = flattenNotesToPlainText(floater.description).toLowerCase();
      const listName = (floater.listName ?? "").toLowerCase();
      return (
        title.includes(query) ||
        description.includes(query) ||
        listName.includes(query)
      );
    });
  }, [completedFloaters, searchQuery]);

  const groupedHistory = useGroupedHistory<CompletedFloaterItemType>(filteredFloaters);

  const isSearching = Boolean(searchQuery.trim());
  const barSlots = useNativePageBarSlots();

  return (
    <div className="mb-20">
      <ScreenWatermark icon={CheckCircle} color={floaterAccent} />

      <MobileSearchHeader
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        placeholder={`${appDict("searchIn")} ${completedDict("title")}...`}
        searchUnavailable={!floaterLoading && completedFloaters.length === 0}
        pageCollapse={{
          ...barSlots,
          title: completedDict("title"),
          accentColor: floaterAccent,
        }}
      />

      <NativePageHeader
        title={completedDict("title")}
        accentColor={floaterAccent}
        icon={CheckCircle}
        barSlots={barSlots}
        beneathTitle={tabSwitcher}
      />

      {floaterLoading && <TodoListLoading className="mt-8" />}

      {!floaterLoading && !isSearching && completedFloaters.length === 0 && (
        <EmptyState
          icon={CheckCircle}
          accentColor={floaterAccent}
          title={completedDict("floaterEmpty")}
          description={completedDict("floaterEmptyBody")}
        />
      )}

      {!floaterLoading && isSearching && filteredFloaters.length === 0 && (
        <EmptyState
          icon={Search}
          accentColor={floaterAccent}
          title={appDict("noMatchingTasks")}
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
      )}

      {!floaterLoading &&
        Array.from(groupedHistory.entries())
          .sort((a, b) => {
            const aDate = new Date(a[1][0].completedAt).getTime();
            const bDate = new Date(b[1][0].completedAt).getTime();
            return bDate - aDate;
          })
          .map(([dateTimeString, completedFloaters]) => (
            <GroupedCompletedFloaterContainer
              key={dateTimeString}
              dateTimeString={dateTimeString}
              completedFloaters={completedFloaters}
            />
          ))}
    </div>
  );
};

export default CompletedFloaterContainer;

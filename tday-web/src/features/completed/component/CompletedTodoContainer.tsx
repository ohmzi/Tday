import React, { useMemo, useState } from "react";
import TodoListLoading from "@/components/ui/TodoListLoading";
import { useCompletedTodo } from "../query/get-completedTodo";
import { useGroupedHistory } from "../hooks/useGroupedHistory";
import GroupedCompletedTodoContainer from "./GroupedContainer";
import { useTranslation } from "react-i18next";
import NativePageHeader, { useNativePageBarSlots } from "@/components/app/NativePageHeader";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import ScreenWatermark from "@/components/app/ScreenWatermark";
import EmptyState from "@/components/app/EmptyState";
import { nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import { CheckCircle, Search } from "lucide-react";
import { flattenNotesToPlainText } from "@/lib/richNotes";

const CompletedTodoContainer = () => {
  const { t: completedDict } = useTranslation("completed")
  const { t: appDict } = useTranslation("app");
  const { completedTodos, todoLoading } = useCompletedTodo();
  const [searchQuery, setSearchQuery] = useState("");

  const filteredTodos = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return completedTodos;
    return completedTodos.filter((todo) => {
      const title = todo.title.toLowerCase();
      const description = flattenNotesToPlainText(todo.description).toLowerCase();
      return title.includes(query) || description.includes(query);
    });
  }, [completedTodos, searchQuery]);

  const groupedHistory = useGroupedHistory(filteredTodos);

  const isSearching = Boolean(searchQuery.trim());
  // The search field is this page's pinned bar, so the header below renders
  // only the block that scrolls away and docks its title into it — the same
  // split the custom list uses.
  const barSlots = useNativePageBarSlots();

  return (
    <div className="mb-20">
      <ScreenWatermark icon={CheckCircle} />

      <MobileSearchHeader
        searchQuery={searchQuery}
        onSearchChange={setSearchQuery}
        placeholder={`${appDict("searchIn")} ${completedDict("title")}...`}
        // No magnifier over an empty history — nothing for a query to narrow.
        // The unsearched set, so a word that matches nothing keeps the field.
        searchUnavailable={!todoLoading && completedTodos.length === 0}
        pageCollapse={{
          ...barSlots,
          title: completedDict("title"),
          accentColor: nativeScreenAccentColors.completed,
        }}
      />

      <NativePageHeader
        title={completedDict("title")}
        accentColor={nativeScreenAccentColors.completed}
        icon={CheckCircle}
        barSlots={barSlots}
      />

      {todoLoading && <TodoListLoading className="mt-8" />}

      {/* Empty state — nothing has been ticked off yet */}
      {!todoLoading && !isSearching && completedTodos.length === 0 && (
        <EmptyState
          icon={CheckCircle}
          accentColor={nativeScreenAccentColors.completed}
          title={completedDict("empty")}
          description={completedDict("emptyBody")}
        />
      )}

      {/* Empty state — no search results */}
      {!todoLoading && isSearching && filteredTodos.length === 0 && (
        <EmptyState
          icon={Search}
          accentColor={nativeScreenAccentColors.completed}
          title={appDict("noMatchingTasks")}
          description={appDict("searchEmptyBody")}
          action={
            <button
              type="button"
              onClick={() => setSearchQuery("")}
              className="rounded-full border border-border/60 bg-card px-5 py-2.5 text-sm font-black text-foreground shadow-[0_12px_28px_-22px_hsl(var(--shadow)/0.55)] transition-transform hover:-translate-y-0.5"
            >
              {appDict("clearSearch")}
            </button>
          }
        />
      )}

      {!todoLoading &&
        Array.from(groupedHistory.entries())
          .sort((a, b) => {
            const aDate = new Date(a[1][0].completedAt).getTime();
            const bDate = new Date(b[1][0].completedAt).getTime();
            return bDate - aDate;
          })
          .map(([dateTimeString, completeTodos]) => (
            <GroupedCompletedTodoContainer
              key={dateTimeString}
              dateTimeString={dateTimeString}
              completedTodos={completeTodos}
            />
          ))}
    </div>
  );
};

export default CompletedTodoContainer;

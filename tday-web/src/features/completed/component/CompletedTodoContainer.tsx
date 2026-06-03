import React, { useMemo, useState } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import TodoListLoading from "@/components/ui/TodoListLoading";
import { useCompletedTodo } from "../query/get-completedTodo";
import { useGroupedHistory } from "../hooks/useGroupedHistory";
import GroupedCompletedTodoContainer from "./GroupedContainer";
import { useTranslation } from "react-i18next";
import NativePageTitle from "@/components/app/NativePageTitle";
import ScreenWatermark from "@/components/app/ScreenWatermark";
import { nativeScreenAccentColors } from "@/components/app/nativeScreenTheme";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import { CheckCircle, Search, X } from "lucide-react";

const CompletedTodoContainer = () => {
  const { t: completedDict } = useTranslation("completed")
  const { completedTodos, todoLoading } = useCompletedTodo();
  const [searchQuery, setSearchQuery] = useState("");

  const filteredTodos = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return completedTodos;
    return completedTodos.filter((todo) => {
      const title = todo.title.toLowerCase();
      const description = (todo.description || "").toLowerCase();
      return title.includes(query) || description.includes(query);
    });
  }, [completedTodos, searchQuery]);

  const groupedHistory = useGroupedHistory(filteredTodos);

  if (todoLoading)
    return (
      <div>
        <MobileSearchHeader searchQuery={searchQuery} onSearchChange={setSearchQuery} />
        <Skeleton className="mt-8 h-10 w-56" />
        <TodoListLoading className="mt-8" />
      </div>
    );

  return (
    <div className="mb-20">
      <ScreenWatermark icon={CheckCircle} />
      <MobileSearchHeader searchQuery={searchQuery} onSearchChange={setSearchQuery} />

      <NativePageTitle
        title={completedDict("title")}
        accentColor={nativeScreenAccentColors.completed}
        icon={CheckCircle}
      />

      {searchQuery.trim() && filteredTodos.length === 0 && (
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
            No matching tasks
          </h3>
          <p className="mb-6 text-sm text-muted-foreground">
            Try different keywords or{" "}
            <button
              onClick={() => setSearchQuery("")}
              className="text-accent hover:underline"
            >
              clear your search
            </button>
          </p>
        </div>
      )}

      {!searchQuery.trim() && filteredTodos.length === 0 && (
        <div className="flex min-h-[42vh] flex-col items-center justify-center text-center">
          <p className="text-2xl font-black text-muted-foreground/70">
            No completed tasks yet
          </p>
        </div>
      )}

      {Array.from(groupedHistory.entries())
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

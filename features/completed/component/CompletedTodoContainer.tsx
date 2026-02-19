"use client";
import React, { useMemo, useState } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import TodoListLoading from "@/components/ui/TodoListLoading";
import { useCompletedTodo } from "../query/get-completedTodo";
import { useGroupedHistory } from "../hooks/useGroupedHistory";
import GroupedCompletedTodoContainer from "./GroupedContainer";
import { useTranslations } from "next-intl";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import LineSeparator from "@/components/ui/lineSeparator";
import { CheckCircleIcon, Search, X } from "lucide-react";

const CompletedTodoContainer = () => {
  const completedDict = useTranslations("completed")
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
      <MobileSearchHeader searchQuery={searchQuery} onSearchChange={setSearchQuery} />

      <div className="mt-8 mb-4 sm:mt-10 sm:mb-5 lg:mt-16 lg:mb-6 ml-[2px] flex items-center gap-2">
        <CheckCircleIcon className="h-6 w-6 text-accent" />
        <h3 className="select-none text-2xl font-semibold tracking-tight">
          {completedDict("title")}
        </h3>
      </div>
      <LineSeparator className="flex-1 border-border/70" />

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
            No matching tasks found
          </h3>
          <p className="mb-6 text-sm text-muted-foreground">
            Try adjusting your search terms or{" "}
            <button
              onClick={() => setSearchQuery("")}
              className="text-accent hover:underline"
            >
              clear the search
            </button>
          </p>
        </div>
      )}

      {!searchQuery.trim() && filteredTodos.length === 0 && (
        <div className="mt-4 rounded-2xl border border-border/65 bg-card/95 px-4 py-6 text-sm text-muted-foreground">
          No completed tasks yet.
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

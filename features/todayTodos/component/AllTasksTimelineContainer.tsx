"use client";

import React, { useEffect, useMemo, useRef, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { Command, Menu, Search, Sun, X } from "lucide-react";
import LineSeparator from "@/components/ui/lineSeparator";
import TodoListLoading from "@/components/todo/component/TodoListLoading";
import TodoGroup from "@/components/todo/component/TodoGroup";
import TodoMutationProvider from "@/providers/TodoMutationProvider";
import { TodoItemType } from "@/types";
import { useTodoTimeline } from "../query/get-todo-timeline";
import { usePinTodo } from "../query/pin-todo";
import { useCompleteTodo } from "../query/complete-todo";
import { useDeleteTodo } from "../query/delete-todo";
import { usePrioritizeTodo } from "../query/prioritize-todo";
import { useEditTodo } from "../query/update-todo";
import { useEditTodoInstance } from "../query/update-todo-instance";
import { useReorderTodo } from "../query/reorder-todo";
import CreateTodoBtn from "./CreateTodoBtn";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { useProjectMetaData } from "@/components/Sidebar/Project/query/get-project-meta";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { useMenu } from "@/providers/MenuProvider";

const PAGE_SIZE = 10;
const MS_IN_DAY = 1000 * 60 * 60 * 24;

type TimelineItem = {
  todo: TodoItemType;
  dayDiff: number;
  dayKey: string;
  label: string;
};

type TimelineSection = {
  key: string;
  label: string;
  dayDiff: number;
  todos: TodoItemType[];
};

const getTimeZoneDate = (date: Date, timeZone?: string) =>
  new Date(date.toLocaleString("en-US", { timeZone: timeZone || "UTC" }));

const getDayDiff = (date: Date, timeZone?: string) => {
  const nowInTimezone = getTimeZoneDate(new Date(), timeZone);
  const dateInTimezone = getTimeZoneDate(date, timeZone);

  const todayMidnight = new Date(
    nowInTimezone.getFullYear(),
    nowInTimezone.getMonth(),
    nowInTimezone.getDate(),
  );
  const dateMidnight = new Date(
    dateInTimezone.getFullYear(),
    dateInTimezone.getMonth(),
    dateInTimezone.getDate(),
  );

  return Math.round((dateMidnight.getTime() - todayMidnight.getTime()) / MS_IN_DAY);
};

const getDayKey = (date: Date, timeZone?: string) => {
  const dateInTimezone = getTimeZoneDate(date, timeZone);
  const y = dateInTimezone.getFullYear();
  const m = String(dateInTimezone.getMonth() + 1).padStart(2, "0");
  const d = String(dateInTimezone.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
};

const getDayLabel = ({
  date,
  dayDiff,
  locale,
  timeZone,
  appDict,
}: {
  date: Date;
  dayDiff: number;
  locale: string;
  timeZone?: string;
  appDict: ReturnType<typeof useTranslations>;
}) => {
  if (dayDiff === 0) return appDict("today");
  if (dayDiff === 1) return appDict("tomorrow");
  return new Intl.DateTimeFormat(locale, {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: timeZone || "UTC",
  }).format(date);
};

const getTimelinePriority = (dayDiff: number) => {
  if (dayDiff === 0) return 0; // Today
  if (dayDiff === 1) return 1; // Tomorrow
  if (dayDiff > 1) return 2; // Future dates
  return 3; // Past dates
};

const compareTimelineItems = (a: TimelineItem, b: TimelineItem) => {
  const priorityDelta = getTimelinePriority(a.dayDiff) - getTimelinePriority(b.dayDiff);
  if (priorityDelta !== 0) {
    return priorityDelta;
  }

  if (a.dayDiff > 1 || b.dayDiff > 1) {
    const futureDelta = a.dayDiff - b.dayDiff;
    if (futureDelta !== 0) {
      return futureDelta;
    }
  } else if (a.dayDiff < 0 || b.dayDiff < 0) {
    const pastDelta = b.dayDiff - a.dayDiff;
    if (pastDelta !== 0) {
      return pastDelta;
    }
  }

  if (a.todo.pinned !== b.todo.pinned) {
    return a.todo.pinned ? -1 : 1;
  }

  const orderDelta = a.todo.order - b.todo.order;
  if (orderDelta !== 0) {
    return orderDelta;
  }

  return a.todo.due.getTime() - b.todo.due.getTime();
};

const toSections = (items: TimelineItem[]) => {
  const sections: TimelineSection[] = [];

  for (const item of items) {
    const currentSection = sections[sections.length - 1];
    if (!currentSection || currentSection.key !== item.dayKey) {
      sections.push({
        key: item.dayKey,
        label: item.label,
        dayDiff: item.dayDiff,
        todos: [item.todo],
      });
      continue;
    }

    currentSection.todos.push(item.todo);
  }

  return sections;
};

const normalizeTagName = (name: string | null | undefined) =>
  (name || "").replace(/^#+\s*/, "").trim();

const AllTasksTimelineContainer = () => {
  const locale = useLocale();
  const appDict = useTranslations("app");
  const userTZ = useUserTimezone();
  const { setShowMenu } = useMenu();
  const { projectMetaData } = useProjectMetaData();
  const { todos, todoLoading } = useTodoTimeline();
  const [searchQuery, setSearchQuery] = useState("");
  const [isSearchFocused, setIsSearchFocused] = useState(false);
  const searchInputRef = useRef<HTMLInputElement | null>(null);

  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const isMac =
    typeof window !== "undefined" &&
    navigator.userAgent.toLowerCase().includes("mac");

  const timelineItems = useMemo(() => {
    return todos
      .map((todo) => {
        const dayDiff = getDayDiff(todo.due, userTZ?.timeZone);
        return {
          todo,
          dayDiff,
          dayKey: getDayKey(todo.due, userTZ?.timeZone),
          label: getDayLabel({
            date: todo.due,
            dayDiff,
            locale,
            timeZone: userTZ?.timeZone,
            appDict,
          }),
        };
      })
      .sort(compareTimelineItems);
  }, [appDict, locale, todos, userTZ?.timeZone]);

  const filteredTimelineItems = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) {
      return timelineItems;
    }

    return timelineItems.filter(({ todo }) => {
      const title = todo.title.toLowerCase();
      const description = (todo.description || "").toLowerCase();
      const tagName = normalizeTagName(projectMetaData[todo.projectID || ""]?.name)
        .toLowerCase();

      return (
        title.includes(query) ||
        description.includes(query) ||
        tagName.includes(query)
      );
    });
  }, [projectMetaData, searchQuery, timelineItems]);

  const visibleTimelineItems = useMemo(
    () => filteredTimelineItems.slice(0, visibleCount),
    [filteredTimelineItems, visibleCount],
  );
  const sections = useMemo(
    () => toSections(visibleTimelineItems),
    [visibleTimelineItems],
  );
  const hasTodayTasks = useMemo(
    () => filteredTimelineItems.some((item) => item.dayDiff === 0),
    [filteredTimelineItems],
  );

  const hasMore = visibleCount < filteredTimelineItems.length;

  useEffect(() => {
    setVisibleCount(PAGE_SIZE);
  }, [filteredTimelineItems.length]);

  useEffect(() => {
    if (!hasMore || !sentinelRef.current) {
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        const [entry] = entries;
        if (!entry?.isIntersecting) {
          return;
        }
        setVisibleCount((prev) =>
          Math.min(prev + PAGE_SIZE, filteredTimelineItems.length),
        );
      },
      {
        root: null,
        rootMargin: "200px 0px",
      },
    );

    observer.observe(sentinelRef.current);
    return () => {
      observer.disconnect();
    };
  }, [filteredTimelineItems.length, hasMore]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        searchInputRef.current?.focus();
      }

      if (e.key === "Escape" && (isSearchFocused || searchQuery.trim().length > 0)) {
        setSearchQuery("");
        setIsSearchFocused(false);
        searchInputRef.current?.blur();
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [isSearchFocused, searchQuery]);

  return (
    <TodoMutationProvider
      useCompleteTodo={useCompleteTodo}
      useDeleteTodo={useDeleteTodo}
      useEditTodo={useEditTodo}
      useEditTodoInstance={useEditTodoInstance}
      usePinTodo={usePinTodo}
      usePrioritizeTodo={usePrioritizeTodo}
      useReorderTodo={useReorderTodo}
    >
      <div className="mb-20">
        <header
          className={cn(
            "sticky top-0 z-40",
            "flex h-16 items-center gap-3",
            "bg-background/60 backdrop-blur-2xl",
            "border-b border-border/30",
            "-mx-4 px-4",
            "sm:-mx-6 sm:px-6",
            "lg:static lg:mx-0 lg:h-auto lg:border-0 lg:bg-transparent lg:backdrop-blur-none lg:px-0 lg:pb-2 lg:pt-4",
            "transition-all duration-300"
          )}
        >
          <Button
            variant="ghost"
            size="icon"
            className={cn(
              "lg:hidden h-10 w-10 rounded-xl",
              "hover:bg-accent/80",
              "transition-all duration-200",
            )}
            aria-label="Open menu"
            onClick={() => setShowMenu(true)}
          >
            <Menu className="h-5 w-5" />
          </Button>

          <div className="flex-1 flex justify-center">
            <div className="relative w-full max-w-xl">
              <div
                className={cn(
                  "relative flex items-center",
                  "rounded-md",
                  "bg-muted/50",
                  "border border-border/40",
                  "transition-colors duration-200",
                  isSearchFocused && [
                    "bg-card",
                    "border-border",
                  ],
                )}
              >
                <Search
                  className={cn(
                    "absolute left-4 h-4 w-4 pointer-events-none",
                    "transition-colors duration-200",
                    isSearchFocused ? "text-accent" : "text-muted-foreground",
                  )}
                />

                <input
                  ref={searchInputRef}
                  type="text"
                  placeholder="Search notes..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  onFocus={() => setIsSearchFocused(true)}
                  onBlur={() => setIsSearchFocused(false)}
                  className={cn(
                    "w-full h-11 pl-11 pr-24",
                    "bg-transparent",
                    "rounded-md",
                    "text-sm text-foreground",
                    "placeholder:text-muted-foreground/50",
                    "outline-none",
                  )}
                />

                <div className="absolute right-3 flex items-center gap-2">
                  {searchQuery ? (
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-7 w-7 rounded-md hover:bg-accent/50"
                      onClick={() => {
                        setSearchQuery("");
                        setIsSearchFocused(false);
                        searchInputRef.current?.blur();
                      }}
                    >
                      <X className="h-4 w-4" />
                    </Button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => searchInputRef.current?.focus()}
                      className={cn(
                        "hidden sm:flex items-center gap-1",
                        "px-2 py-1 rounded-md",
                        "bg-muted/60 text-muted-foreground/60",
                        "text-xs font-medium",
                        "hover:bg-muted hover:text-muted-foreground",
                        "transition-all duration-200",
                        isSearchFocused && "opacity-0 pointer-events-none",
                      )}
                    >
                      {isMac ? (
                        <>
                          <Command className="h-3 w-3" />
                          <span>K</span>
                        </>
                      ) : (
                        <span>Ctrl+K</span>
                      )}
                    </button>
                  )}
                </div>
              </div>
            </div>
          </div>

          <div className="w-10 lg:hidden" />
        </header>

        <div className="mt-8 mb-4 sm:mt-10 sm:mb-5 lg:mt-16 lg:mb-6 ml-[2px] flex items-center gap-2">
          <Sun className="h-6 w-6 text-accent" />
          <h3 className="select-none text-2xl font-semibold tracking-tight">
            {appDict("today")}
          </h3>
        </div>
        <LineSeparator className="flex-1 border-border/70" />

        {todoLoading && <TodoListLoading heading={appDict("today")} />}

        {!todoLoading && !searchQuery.trim() && !hasTodayTasks && (
          <div className="mt-4 rounded-2xl border border-border/65 bg-card/95 px-4 py-6 text-sm text-muted-foreground">
            No today tasks yet.
          </div>
        )}

        {!todoLoading && searchQuery.trim() && filteredTimelineItems.length === 0 && (
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

        {sections.map((section) => (
          <section
            key={section.key}
            className={cn(
              "mb-8 lg:mb-10",
              section.dayDiff === 0 && "mt-5 sm:mt-6 lg:mt-8",
            )}
          >
            {section.dayDiff !== 0 && (
              <div className="mb-3 mt-6 flex items-center gap-2 sm:mt-7 lg:mb-4 lg:mt-10">
                <h3 className="select-none text-lg font-semibold tracking-tight">
                  {section.label}
                </h3>
                <LineSeparator className="flex-1 border-border/70" />
              </div>
            )}
            <TodoGroup todos={section.todos} overdue={section.dayDiff < 0} />
          </section>
        ))}

        {hasMore && (
          <div ref={sentinelRef} className="flex h-12 items-center justify-center">
            <span className="text-xs text-muted-foreground">Loading more tasks...</span>
          </div>
        )}

        <CreateTodoBtn />
      </div>
    </TodoMutationProvider>
  );
};

export default AllTasksTimelineContainer;

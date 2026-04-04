import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { CalendarClock, ChevronDown, ChevronRight, Clock3, Command, Flag, Layers, Menu, Search, Sun, X } from "lucide-react";
import LineSeparator from "@/components/ui/lineSeparator";
import TodoListLoading from "@/components/todo/component/TodoListLoading";
import TodoGroup from "@/components/todo/component/TodoGroup";
import { TodoItemCard } from "@/components/todo/component/TodoItemContainer";
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
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { useMenu } from "@/providers/MenuProvider";
import { useLocale } from "@/lib/navigation";
import { useSearchParams } from "react-router-dom";
import { useToast } from "@/hooks/use-toast";
import { useUserPreferences } from "@/providers/UserPreferencesProvider";
import { moveTodoToDay } from "@/lib/moveTodoToDay";
import { useTodoMutation } from "@/providers/TodoMutationProvider";
import { TodoItemTypeWithDateChecksum } from "../query/update-todo";
import {
  TODO_FOCUS_DATE_QUERY_PARAM,
  TODO_FOCUS_MODE_DELETED,
  TODO_FOCUS_MODE_QUERY_PARAM,
  TODO_FOCUS_TASK_QUERY_PARAM,
  formatTodoFocusDateLabel,
  getTodoDateSectionId,
  getTodoDayKey,
  isTodoFocusDateKey,
} from "@/lib/todoToastNavigation";

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

type TimelineScope = "today" | "scheduled" | "all" | "priority" | "overdue";

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
  appDict: (key: string) => string;
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
  if (dayDiff < 0) return -1; // Earlier – above everything
  if (dayDiff === 0) return 0; // Today
  if (dayDiff === 1) return 1; // Tomorrow
  return 2; // Future dates
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

const compareOverdueTimelineItems = (a: TimelineItem, b: TimelineItem) => {
  const aIsToday = a.dayDiff === 0;
  const bIsToday = b.dayDiff === 0;

  if (aIsToday !== bIsToday) {
    return aIsToday ? -1 : 1;
  }

  return compareTimelineItems(a, b);
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

const normalizeListName = (name: string | null | undefined) =>
  (name || "").trim();

const isPriorityTask = (priority: string | null | undefined) => {
  const normalized = (priority || "").trim().toLowerCase();
  return normalized === "medium" ||
    normalized === "high" ||
    normalized === "important" ||
    normalized === "urgent";
};

const isOverdueTask = (due: Date) => due < new Date();

const SCOPE_CONFIG: Record<TimelineScope, { icon: React.ElementType; heading: string; emptyMessage: string }> = {
  today: { icon: Sun, heading: "today", emptyMessage: "No tasks for today" },
  overdue: { icon: Clock3, heading: "Overdue", emptyMessage: "No overdue tasks" },
  scheduled: { icon: CalendarClock, heading: "Scheduled", emptyMessage: "No upcoming tasks" },
  all: { icon: Layers, heading: "All Tasks", emptyMessage: "No tasks" },
  priority: { icon: Flag, heading: "priority", emptyMessage: "No priority tasks" },
};

const DRAG_DISABLED_MESSAGE = "Drag disabled; a global filter is active";

const ScheduledTimelineSections = ({
  sections,
  focusedTaskId,
  focusedDateKey,
  timeZone,
}: {
  sections: TimelineSection[];
  focusedTaskId: string | null;
  focusedDateKey: string | null;
  timeZone?: string;
}) => {
  const { toast } = useToast();
  const { preferences } = useUserPreferences();
  const { useEditTodo, useEditTodoInstance } = useTodoMutation();
  const { editTodoMutateFn } = useEditTodo();
  const { editTodoInstanceMutateFn } = useEditTodoInstance(undefined);
  const [draggedTodoId, setDraggedTodoId] = useState<string | null>(null);
  const [dropSectionKey, setDropSectionKey] = useState<string | null>(null);

  const todosById = useMemo(
    () =>
      new Map(
        sections.flatMap((section) =>
          section.todos.map((todo) => [todo.id, todo] as const),
        ),
      ),
    [sections],
  );

  const clearDragState = useCallback(() => {
    setDraggedTodoId(null);
    setDropSectionKey(null);
  }, []);

  const handleMove = useCallback((todo: TodoItemType, targetSectionKey: string) => {
    if (getTodoDayKey(todo.due, timeZone) === targetSectionKey) {
      clearDragState();
      return;
    }

    const nextRange = moveTodoToDay(todo, targetSectionKey, timeZone);

    if (todo.rrule && todo.instanceDate) {
      editTodoInstanceMutateFn({
        ...todo,
        instanceDate: todo.instanceDate ?? todo.due,
        ...nextRange,
      });
      clearDragState();
      return;
    }

    editTodoMutateFn({
      ...(todo as TodoItemTypeWithDateChecksum),
      ...nextRange,
      dateRangeChecksum: todo.due.toISOString(),
      rruleChecksum: todo.rrule,
    });
    clearDragState();
  }, [clearDragState, editTodoInstanceMutateFn, editTodoMutateFn, timeZone]);

  const handleDragStart = useCallback((
    event: React.DragEvent<HTMLDivElement>,
    todo: TodoItemType,
    sectionKey: string,
  ) => {
    if (preferences?.sortBy) {
      event.preventDefault();
      toast({ title: DRAG_DISABLED_MESSAGE });
      return;
    }

    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("text/plain", todo.id);
    setDraggedTodoId(todo.id);
    setDropSectionKey(sectionKey);
  }, [preferences?.sortBy, toast]);

  return (
    <>
      {sections.map((section) => {
        const isDropTarget = draggedTodoId !== null && dropSectionKey === section.key;

        return (
          <section
            id={getTodoDateSectionId(section.key)}
            key={section.key}
            className={cn(
              "mb-8 scroll-mt-24 rounded-3xl transition-colors lg:mb-10",
              section.dayDiff === 0 && "mt-5 sm:mt-6 lg:mt-8",
              isDropTarget && "bg-accent/5 ring-1 ring-accent/25",
            )}
            onDragEnter={() => {
              if (draggedTodoId) {
                setDropSectionKey(section.key);
              }
            }}
            onDragOver={(event) => {
              if (!draggedTodoId || preferences?.sortBy) {
                return;
              }

              event.preventDefault();
              event.dataTransfer.dropEffect = "move";
              if (dropSectionKey !== section.key) {
                setDropSectionKey(section.key);
              }
            }}
            onDrop={(event) => {
              event.preventDefault();
              const todoId = event.dataTransfer.getData("text/plain") || draggedTodoId;
              const todo = todoId ? todosById.get(todoId) : null;
              if (todo) {
                handleMove(todo, section.key);
              } else {
                clearDragState();
              }
            }}
          >
            <div className="px-3 py-2 sm:px-4">
              <div className="mb-3 mt-4 flex items-center gap-2 sm:mt-5 lg:mb-4 lg:mt-6">
                <h3
                  className={cn(
                    "select-none text-lg font-semibold tracking-tight",
                    focusedDateKey === section.key && "text-accent",
                  )}
                >
                  {section.label}
                </h3>
                <LineSeparator className="flex-1 border-border/70" />
              </div>
              <div className="space-y-2">
                {section.todos.map((todo) => (
                  <div
                    key={todo.id}
                    draggable={true}
                    onDragStart={(event) => handleDragStart(event, todo, section.key)}
                    onDragEnd={clearDragState}
                  >
                    <TodoItemCard
                      todoItem={todo}
                      overdue={false}
                      perTaskOverdue={section.dayDiff === 0}
                      highlighted={focusedTaskId === todo.id}
                      dragging={draggedTodoId === todo.id}
                    />
                  </div>
                ))}
              </div>
            </div>
          </section>
        );
      })}
    </>
  );
};

const AllTasksTimelineContainer = ({
  scope = "today",
}: {
  scope?: TimelineScope;
}) => {
  const locale = useLocale();
  const [searchParams] = useSearchParams();
  const { t: appDict } = useTranslation("app");
  const userTZ = useUserTimezone();
  const { setShowMenu } = useMenu();
  const { listMetaData } = useListMetaData();
  const { todos, todoLoading } = useTodoTimeline();
  const [searchQuery, setSearchQuery] = useState("");
  const [isSearchFocused, setIsSearchFocused] = useState(false);
  const searchInputRef = useRef<HTMLInputElement | null>(null);

  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);
  const [earlierExpanded, setEarlierExpanded] = useState(false);
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const { icon: ScopeIcon, emptyMessage: emptyStateMessage, heading: scopeHeading } = SCOPE_CONFIG[scope];
  const pageHeading = scope === "today" || scope === "priority" ? appDict(scopeHeading) : scopeHeading;
  const isMac =
    typeof window !== "undefined" &&
    navigator.userAgent.toLowerCase().includes("mac");
  const focusedTaskId = searchParams.get(TODO_FOCUS_TASK_QUERY_PARAM);
  const focusedDateKey = useMemo(() => {
    const value = searchParams.get(TODO_FOCUS_DATE_QUERY_PARAM);
    return isTodoFocusDateKey(value) ? value : null;
  }, [searchParams]);
  const isDeletedFocus =
    searchParams.get(TODO_FOCUS_MODE_QUERY_PARAM) === TODO_FOCUS_MODE_DELETED;

  const scopedTodos = useMemo(() => {
    if (scope === "priority") return todos.filter((todo) => isPriorityTask(todo.priority));
    return todos;
  }, [scope, todos]);

  const timelineItems = useMemo(() => {
    return scopedTodos
      .map((todo) => {
        const dayDiff = getDayDiff(todo.due, userTZ?.timeZone);
        return {
          todo,
          dayDiff,
          dayKey: getTodoDayKey(todo.due, userTZ?.timeZone),
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
  }, [appDict, locale, scopedTodos, userTZ?.timeZone]);

  const filteredTimelineItems = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) {
      return timelineItems;
    }

    return timelineItems.filter(({ todo }) => {
      const title = todo.title.toLowerCase();
      const description = (todo.description || "").toLowerCase();
      const listId = todo.listID ?? "";
      const listName = normalizeListName(listMetaData[listId]?.name)
        .toLowerCase();

      return (
        title.includes(query) ||
        description.includes(query) ||
        listName.includes(query)
      );
    });
  }, [listMetaData, searchQuery, timelineItems]);

  const scopeFilteredItems = useMemo(() => {
    if (scope === "today") {
      return filteredTimelineItems.filter((item) => item.dayDiff === 0);
    }
    if (scope === "overdue") {
      return filteredTimelineItems
        .filter((item) => isOverdueTask(item.todo.due))
        .sort(compareOverdueTimelineItems);
    }
    if (scope === "scheduled") {
      const now = new Date();
      return filteredTimelineItems.filter((item) => item.todo.due >= now);
    }
    return filteredTimelineItems;
  }, [filteredTimelineItems, scope]);
  const focusedTaskIndex = useMemo(
    () =>
      focusedTaskId
        ? scopeFilteredItems.findIndex((item) => item.todo.id === focusedTaskId)
        : -1,
    [focusedTaskId, scopeFilteredItems],
  );
  const focusedTaskItem = focusedTaskIndex >= 0
    ? scopeFilteredItems[focusedTaskIndex]
    : null;
  const focusedDateIndex = useMemo(
    () =>
      focusedDateKey
        ? scopeFilteredItems.findIndex((item) => item.dayKey === focusedDateKey)
        : -1,
    [focusedDateKey, scopeFilteredItems],
  );
  const showScheduledFocusEmptyState = scope === "scheduled" &&
    Boolean(focusedDateKey) &&
    focusedDateIndex === -1 &&
    !searchQuery.trim();
  const focusedDateLabel = useMemo(
    () => (focusedDateKey ? formatTodoFocusDateLabel(focusedDateKey, locale) : null),
    [focusedDateKey, locale],
  );

  const visibleTimelineItems = useMemo(
    () => scopeFilteredItems.slice(0, visibleCount),
    [scopeFilteredItems, visibleCount],
  );
  const sections = useMemo(
    () => toSections(visibleTimelineItems),
    [visibleTimelineItems],
  );
  const earlierSections = useMemo(
    () => sections.filter((s) => s.dayDiff < 0),
    [sections],
  );
  const regularSections = useMemo(
    () => sections.filter((s) => s.dayDiff >= 0),
    [sections],
  );
  const earlierTaskCount = useMemo(
    () => earlierSections.reduce((sum, s) => sum + s.todos.length, 0),
    [earlierSections],
  );
  const hasScopedTasks = useMemo(() => {
    if (scope === "today") {
      return scopeFilteredItems.some((item) => item.dayDiff === 0);
    }
    return scopeFilteredItems.length > 0;
  }, [scopeFilteredItems, scope]);

  const hasMore = visibleCount < scopeFilteredItems.length;

  useEffect(() => {
    setVisibleCount(PAGE_SIZE);
  }, [scopeFilteredItems.length]);

  useEffect(() => {
    const targetIndex = focusedTaskIndex >= 0 ? focusedTaskIndex : focusedDateIndex;
    if (targetIndex < 0 || targetIndex < visibleCount) {
      return;
    }

    setVisibleCount((prev) => {
      const nextCount = Math.ceil((targetIndex + 1) / PAGE_SIZE) * PAGE_SIZE;
      return Math.min(Math.max(prev, nextCount), scopeFilteredItems.length);
    });
  }, [focusedDateIndex, focusedTaskIndex, scopeFilteredItems.length, visibleCount]);

  useEffect(() => {
    if (scope === "all" && focusedTaskItem?.dayDiff != null && focusedTaskItem.dayDiff < 0) {
      setEarlierExpanded(true);
    }
  }, [focusedTaskItem?.dayDiff, scope]);

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
          Math.min(prev + PAGE_SIZE, scopeFilteredItems.length),
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
  }, [scopeFilteredItems.length, hasMore]);

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

  useEffect(() => {
    if (!focusedTaskId || focusedTaskIndex < 0) {
      return;
    }
    if (focusedTaskIndex >= visibleCount) {
      return;
    }
    if (scope === "all" && focusedTaskItem?.dayDiff != null && focusedTaskItem.dayDiff < 0 && !earlierExpanded) {
      return;
    }

    const frame = window.requestAnimationFrame(() => {
      document
        .getElementById(`todo-focus-${encodeURIComponent(focusedTaskId)}`)
        ?.scrollIntoView({ behavior: "smooth", block: "center" });
    });

    return () => window.cancelAnimationFrame(frame);
  }, [
    earlierExpanded,
    focusedTaskId,
    focusedTaskIndex,
    focusedTaskItem?.dayDiff,
    scope,
    visibleCount,
  ]);

  useEffect(() => {
    if (scope !== "scheduled" || !focusedDateKey || focusedTaskId) {
      return;
    }
    if (focusedDateIndex >= visibleCount && focusedDateIndex >= 0) {
      return;
    }

    const frame = window.requestAnimationFrame(() => {
      document
        .getElementById(getTodoDateSectionId(focusedDateKey))
        ?.scrollIntoView({ behavior: "smooth", block: "start" });
    });

    return () => window.cancelAnimationFrame(frame);
  }, [focusedDateIndex, focusedDateKey, focusedTaskId, scope, visibleCount]);

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
                  placeholder="Search tasks..."
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
          <ScopeIcon className="h-6 w-6 text-accent" />
          <h3 className="select-none text-2xl font-semibold tracking-tight">
            {pageHeading}
          </h3>
        </div>
        <LineSeparator className="flex-1 border-border/70" />

        {todoLoading && <TodoListLoading heading={pageHeading} />}

        {!todoLoading && !searchQuery.trim() && !hasScopedTasks && (
          <div className="mt-4 rounded-2xl border border-border/65 bg-card/95 px-4 py-6 text-sm text-muted-foreground">
            {emptyStateMessage}
          </div>
        )}

        {showScheduledFocusEmptyState && focusedDateKey && focusedDateLabel && (
          <section
            id={getTodoDateSectionId(focusedDateKey)}
            className="mb-8 scroll-mt-24 lg:mb-10"
          >
            <div className="mb-3 mt-6 flex items-center gap-2 sm:mt-7 lg:mb-4 lg:mt-10">
              <h3 className="select-none text-lg font-semibold tracking-tight text-accent">
                {focusedDateLabel}
              </h3>
              <LineSeparator className="flex-1 border-border/70" />
            </div>
            <div className="rounded-2xl border border-accent/20 bg-accent/10 px-4 py-5 text-sm text-muted-foreground">
              {isDeletedFocus
                ? "The task is gone. No tasks remain scheduled for this date."
                : "No tasks are scheduled for this date."}
            </div>
          </section>
        )}

        {!todoLoading && searchQuery.trim() && scopeFilteredItems.length === 0 && (
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

        {scope === "overdue" &&
          regularSections.map((section) => (
            <section
              id={getTodoDateSectionId(section.key)}
              key={section.key}
              className={cn(
                "mb-8 scroll-mt-24 lg:mb-10",
                section.dayDiff === 0 && "mt-5 sm:mt-6 lg:mt-8",
              )}
            >
              <div className="mb-3 mt-6 flex items-center gap-2 sm:mt-7 lg:mb-4 lg:mt-10">
                <h3
                  className={cn(
                    "select-none text-lg font-semibold tracking-tight",
                    focusedDateKey === section.key && "text-accent",
                  )}
                >
                  {section.label}
                </h3>
                <LineSeparator className="flex-1 border-border/70" />
              </div>
              <TodoGroup
                todos={section.todos}
                overdue={section.dayDiff < 0}
                perTaskOverdue={section.dayDiff === 0}
                highlightedTodoId={focusedTaskId}
              />
            </section>
          ))}

        {scope === "overdue" &&
          earlierSections.map((section) => (
            <section
              id={getTodoDateSectionId(section.key)}
              key={section.key}
              className="mb-8 scroll-mt-24 lg:mb-10"
            >
              <div className="mb-3 mt-6 flex items-center gap-2 sm:mt-7 lg:mb-4 lg:mt-10">
                <h3
                  className={cn(
                    "select-none text-lg font-semibold tracking-tight",
                    focusedDateKey === section.key && "text-accent",
                  )}
                >
                  {section.label}
                </h3>
                <LineSeparator className="flex-1 border-border/70" />
              </div>
              <TodoGroup
                todos={section.todos}
                overdue={true}
                highlightedTodoId={focusedTaskId}
              />
            </section>
          ))}

        {scope === "all" && earlierSections.length > 0 && (
          <section className="mb-8 lg:mb-10">
            <button
              type="button"
              onClick={() => setEarlierExpanded((v) => !v)}
              className="mb-3 mt-6 flex w-full items-center gap-2 sm:mt-7 lg:mb-4 lg:mt-10"
            >
              {earlierExpanded ? (
                <ChevronDown className="h-4 w-4 text-muted-foreground" />
              ) : (
                <ChevronRight className="h-4 w-4 text-muted-foreground" />
              )}
              <h3 className="select-none text-lg font-semibold tracking-tight">
                Earlier
              </h3>
              <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
                {earlierTaskCount}
              </span>
              <LineSeparator className="flex-1 border-border/70" />
            </button>

            {earlierExpanded &&
              earlierSections.map((section) => (
                  <div
                    id={getTodoDateSectionId(section.key)}
                    key={section.key}
                    className="scroll-mt-24"
                  >
                  <div className="mb-3 flex items-center gap-2 lg:mb-4">
                    <h4
                      className={cn(
                        "select-none text-base font-medium tracking-tight text-muted-foreground",
                        focusedDateKey === section.key && "text-accent",
                      )}
                    >
                      {section.label}
                    </h4>
                    <LineSeparator className="flex-1 border-border/70" />
                  </div>
                  <div className="mb-4">
                    <TodoGroup
                      todos={section.todos}
                      overdue={true}
                      highlightedTodoId={focusedTaskId}
                    />
                  </div>
                </div>
              ))}
          </section>
        )}

        {scope === "scheduled" && (
          <ScheduledTimelineSections
            sections={regularSections}
            focusedTaskId={focusedTaskId}
            focusedDateKey={focusedDateKey}
            timeZone={userTZ?.timeZone}
          />
        )}

        {scope !== "overdue" && scope !== "scheduled" && regularSections.map((section) => (
          <section
            id={getTodoDateSectionId(section.key)}
            key={section.key}
            className={cn(
              "mb-8 scroll-mt-24 lg:mb-10",
              section.dayDiff === 0 && "mt-5 sm:mt-6 lg:mt-8",
            )}
          >
            {(section.dayDiff !== 0 || scope === "all") && (
              <div className="mb-3 mt-6 flex items-center gap-2 sm:mt-7 lg:mb-4 lg:mt-10">
                <h3
                  className={cn(
                    "select-none text-lg font-semibold tracking-tight",
                    focusedDateKey === section.key && "text-accent",
                  )}
                >
                  {section.label}
                </h3>
                <LineSeparator className="flex-1 border-border/70" />
              </div>
            )}
            <TodoGroup
              todos={section.todos}
              overdue={false}
              perTaskOverdue={section.dayDiff === 0}
              highlightedTodoId={focusedTaskId}
            />
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

import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { CalendarClock, Clock3, Flag, Layers, Search, Sun, X } from "lucide-react";
import NativePageTitle from "@/components/app/NativePageTitle";
import { timelineScopeAccentColors } from "@/components/app/nativeScreenTheme";
import MobileSearchHeader, { type SearchResultItem } from "@/components/ui/MobileSearchHeader";
import LineSeparator from "@/components/ui/lineSeparator";
import TodoListLoading from "@/components/todo/component/TodoListLoading";
import TodoGroup from "@/components/todo/component/TodoGroup";
import TimelineSections from "@/components/todo/dnd/TimelineSections";
import {
  headerToBodyGap,
  sectionTopGapFilled,
  sectionTopGapFirst,
} from "@/components/todo/dnd/timelineDndClasses";
import TodoMutationProvider from "@/providers/TodoMutationProvider";
import { TodoItemType } from "@/types";
import { useTodoTimeline } from "../query/get-todo-timeline";
import { useCompleteTodo } from "../query/complete-todo";
import { useDeleteTodo } from "../query/delete-todo";
import { usePrioritizeTodo } from "../query/prioritize-todo";
import { useEditTodo } from "../query/update-todo";
import { useEditTodoInstance } from "../query/update-todo-instance";
import { useReorderTodo } from "../query/reorder-todo";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import { cn } from "@/lib/utils";
import { useLocale, useRouter } from "@/lib/navigation";
import { getDisplayDate } from "@/lib/date/displayDate";
import { useSearchParams } from "react-router-dom";
import {
  buildTimelineSections,
  findSectionKeyForDayKey,
} from "@/lib/timeline/buildTimelineSections";
import {
  TODO_FOCUS_DATE_QUERY_PARAM,
  TODO_FOCUS_TASK_QUERY_PARAM,
  buildTodoFocusPath,
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

// Scopes that render the native date-bucketed timeline with drag-and-drop.
const isTimelineScope = (scope: TimelineScope) =>
  scope === "all" || scope === "priority" || scope === "scheduled";

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

const AllTasksTimelineContainer = ({
  scope = "today",
}: {
  scope?: TimelineScope;
}) => {
  const locale = useLocale();
  const router = useRouter();
  const [searchParams] = useSearchParams();
  const { t: appDict } = useTranslation("app");
  const userTZ = useUserTimezone();
  const { listMetaData } = useListMetaData();
  const { todos, todoLoading } = useTodoTimeline();
  const [searchQuery, setSearchQuery] = useState("");

  const timeline = isTimelineScope(scope);

  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);
  const [earlierExpanded, setEarlierExpanded] = useState(false);
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const { icon: ScopeIcon, emptyMessage: emptyStateMessage, heading: scopeHeading } = SCOPE_CONFIG[scope];
  const pageHeading = scope === "today" || scope === "priority" ? appDict(scopeHeading) : scopeHeading;
  const focusedTaskId = searchParams.get(TODO_FOCUS_TASK_QUERY_PARAM);
  const focusedDateKey = useMemo(() => {
    const value = searchParams.get(TODO_FOCUS_DATE_QUERY_PARAM);
    return isTodoFocusDateKey(value) ? value : null;
  }, [searchParams]);
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

  // Global task search: matches across ALL dated tasks (not just this scope). Selecting a
  // result navigates to All Tasks focused on it, reusing the existing focus/scroll mechanism.
  const searchResults = useMemo<SearchResultItem[]>(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return [];
    return timelineItems
      .filter(({ todo }) => {
        const title = todo.title.toLowerCase();
        const description = (todo.description || "").toLowerCase();
        const listId = todo.listID ?? "";
        const listName = normalizeListName(listMetaData[listId]?.name).toLowerCase();
        return (
          title.includes(query) ||
          description.includes(query) ||
          listName.includes(query)
        );
      })
      .slice(0, 8)
      .map(({ todo }) => ({
        id: todo.id,
        title: todo.title,
        subtitle: getDisplayDate(todo.due, true, locale, userTZ?.timeZone),
      }));
  }, [searchQuery, timelineItems, listMetaData, locale, userTZ?.timeZone]);

  const handleSelectSearchResult = useCallback(
    (id: string) => {
      const item = timelineItems.find(({ todo }) => todo.id === id);
      if (!item) return;
      setSearchQuery("");
      router.push(buildTodoFocusPath(item.todo, userTZ?.timeZone));
    },
    [router, timelineItems, userTZ?.timeZone],
  );

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

  // The native date-bucketed timeline (All / Priority / Scheduled).
  const timelineSections = useMemo(() => {
    if (!timeline) return [];
    const built = buildTimelineSections({
      todos: filteredTimelineItems.map((item) => item.todo),
      locale,
      timeZone: userTZ?.timeZone,
      futureOnly: scope === "scheduled",
      placesEarlierBeforeToday: scope !== "scheduled",
      todayLabel: appDict("today"),
      tomorrowLabel: appDict("tomorrow"),
    });
    // Scheduled mirrors native: show only dates that actually have tasks (no
    // empty Today/Tomorrow/day drop-target buckets). All/Priority keep theirs.
    if (scope === "scheduled") {
      return built.filter((section) => section.todos.length > 0);
    }
    return built;
  }, [appDict, filteredTimelineItems, locale, scope, timeline, userTZ?.timeZone]);

  const focusedDateIndex = useMemo(
    () =>
      focusedDateKey
        ? scopeFilteredItems.findIndex((item) => item.dayKey === focusedDateKey)
        : -1,
    [focusedDateKey, scopeFilteredItems],
  );
  const focusedTaskIndex = useMemo(
    () =>
      focusedTaskId
        ? scopeFilteredItems.findIndex((item) => item.todo.id === focusedTaskId)
        : -1,
    [focusedTaskId, scopeFilteredItems],
  );
  // ----- today / overdue paging + grouping (unchanged) -----
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
  const hasScopedTasks = useMemo(() => {
    if (scope === "today") {
      return scopeFilteredItems.some((item) => item.dayDiff === 0);
    }
    return scopeFilteredItems.length > 0;
  }, [scopeFilteredItems, scope]);

  const hasMore = !timeline && visibleCount < scopeFilteredItems.length;
  const isSearching = Boolean(searchQuery.trim());
  // Timeline scopes always render their buckets (drop targets) unless an active
  // search yielded nothing.
  const showTimeline = timeline && !(isSearching && scopeFilteredItems.length === 0);
  const showEmptyCard = !timeline && !todoLoading && !isSearching && !hasScopedTasks;

  useEffect(() => {
    setVisibleCount(PAGE_SIZE);
  }, [scopeFilteredItems.length]);

  useEffect(() => {
    if (timeline) return;
    const targetIndex = focusedTaskIndex >= 0 ? focusedTaskIndex : focusedDateIndex;
    if (targetIndex < 0 || targetIndex < visibleCount) {
      return;
    }

    setVisibleCount((prev) => {
      const nextCount = Math.ceil((targetIndex + 1) / PAGE_SIZE) * PAGE_SIZE;
      return Math.min(Math.max(prev, nextCount), scopeFilteredItems.length);
    });
  }, [focusedDateIndex, focusedTaskIndex, scopeFilteredItems.length, timeline, visibleCount]);

  // Expand Earlier when the focused task lives in the past (timeline scopes).
  useEffect(() => {
    if (!timeline || !focusedTaskId) return;
    const earlier = timelineSections.find((section) => section.kind === "earlier");
    if (earlier?.todos.some((todo) => todo.id === focusedTaskId)) {
      setEarlierExpanded(true);
    }
  }, [focusedTaskId, timeline, timelineSections]);

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

  // Scroll a focused date into view within the timeline (the bucket may be an
  // aggregate Earlier / Rest / month section).
  useEffect(() => {
    if (!timeline || !focusedDateKey || focusedTaskId) {
      return;
    }
    const sectionKey = findSectionKeyForDayKey(
      timelineSections,
      focusedDateKey,
      userTZ?.timeZone,
    );
    if (!sectionKey) return;

    const frame = window.requestAnimationFrame(() => {
      document
        .getElementById(getTodoDateSectionId(sectionKey))
        ?.scrollIntoView({ behavior: "smooth", block: "start" });
    });

    return () => window.cancelAnimationFrame(frame);
  }, [focusedDateKey, focusedTaskId, timeline, timelineSections, userTZ?.timeZone]);

  return (
    <TodoMutationProvider
      useCompleteTodo={useCompleteTodo}
      useDeleteTodo={useDeleteTodo}
      useEditTodo={useEditTodo}
      useEditTodoInstance={useEditTodoInstance}
      usePrioritizeTodo={usePrioritizeTodo}
      useReorderTodo={useReorderTodo}
    >
      <div className="mb-20">
        <MobileSearchHeader
          searchQuery={searchQuery}
          onSearchChange={setSearchQuery}
          results={searchResults}
          onSelectResult={handleSelectSearchResult}
        />

        <NativePageTitle
          title={pageHeading}
          accentColor={timelineScopeAccentColors[scope]}
          icon={ScopeIcon}
        />

        {todoLoading && <TodoListLoading heading={pageHeading} />}

        {showEmptyCard && (
          <div className="mt-4 rounded-2xl border border-border/65 bg-card/95 px-4 py-6 text-sm text-muted-foreground">
            {emptyStateMessage}
          </div>
        )}

        {!todoLoading && isSearching && scopeFilteredItems.length === 0 && (
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

        {showTimeline && (
          <TimelineSections
            sections={timelineSections}
            timeZone={userTZ?.timeZone}
            focusedTaskId={focusedTaskId}
            focusedDateKey={focusedDateKey}
            earlierExpanded={earlierExpanded}
            onToggleEarlier={() => setEarlierExpanded((value) => !value)}
          />
        )}

        {scope === "overdue" &&
          regularSections.map((section) => (
            <section
              id={getTodoDateSectionId(section.key)}
              key={section.key}
              className={cn(
                "scroll-mt-24",
                section.dayDiff === 0 ? sectionTopGapFirst : sectionTopGapFilled,
              )}
            >
              <div className={cn(headerToBodyGap, "flex items-center gap-2")}>
                <h3
                  className={cn(
                    "select-none text-2xl font-black tracking-tight",
                    focusedDateKey === section.key ? "text-accent" : "text-muted-foreground",
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
                showOverdueTag={false}
              />
            </section>
          ))}

        {scope === "overdue" &&
          earlierSections.map((section) => (
            <section
              id={getTodoDateSectionId(section.key)}
              key={section.key}
              className={cn("scroll-mt-24", sectionTopGapFilled)}
            >
              <div className={cn(headerToBodyGap, "flex items-center gap-2")}>
                <h3
                  className={cn(
                    "select-none text-2xl font-black tracking-tight",
                    focusedDateKey === section.key ? "text-accent" : "text-muted-foreground",
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
                showOverdueTag={false}
              />
            </section>
          ))}

        {scope === "today" && regularSections.map((section) => (
          <section
            id={getTodoDateSectionId(section.key)}
            key={section.key}
            className={cn(
              "scroll-mt-24",
              section.dayDiff === 0 ? sectionTopGapFirst : sectionTopGapFilled,
            )}
          >
            {section.dayDiff !== 0 && (
              <div className={cn(headerToBodyGap, "flex items-center gap-2")}>
                <h3
                  className={cn(
                    "select-none text-2xl font-black tracking-tight",
                    focusedDateKey === section.key ? "text-accent" : "text-muted-foreground",
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
      </div>
    </TodoMutationProvider>
  );
};

export default AllTasksTimelineContainer;

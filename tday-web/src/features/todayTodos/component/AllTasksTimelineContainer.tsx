import React, { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { CalendarClock, CheckCheck, Clock3, Flag, Layers, Search, Sun } from "lucide-react";
import { isSameDay } from "date-fns";
import { useCompletedTodo } from "@/features/completed/query/get-completedTodo";
import NativePageHeader, { useNativePageBarSlots } from "@/components/app/NativePageHeader";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import ScreenWatermark from "@/components/app/ScreenWatermark";
import EmptyState from "@/components/app/EmptyState";
import { taskJustCompleted } from "@/lib/task-completion-signal";
import { timelineScopeAccentColors } from "@/components/app/nativeScreenTheme";
import SummaryButton from "@/features/summary/SummaryButton";
import WeekInReviewCard from "@/features/summary/WeekInReviewCard";
import TodoListLoading from "@/components/todo/component/TodoListLoading";
import TodoGroup from "@/components/todo/component/TodoGroup";
import TimelineSections from "@/components/todo/dnd/TimelineSections";
import {
  TODAY_BUCKETS,
  TodayBucketDndContext,
  TodayBucketDroppable,
  DraggableTodayTask,
} from "@/components/todo/dnd/TodayBucketDnd";
import {
  headerToBodyGap,
  sectionTopGapFilled,
  sectionTopGapFirst,
} from "@/components/todo/dnd/timelineDndClasses";
import TodoMutationProvider from "@/providers/TodoMutationProvider";
import TaskSelectionProvider from "@/providers/TaskSelectionProvider";
import BulkSelectButton from "@/components/todo/bulk/BulkSelectButton";
import { TodoItemType } from "@/types";
import { useTodoTimeline } from "../query/get-todo-timeline";
import { useCompleteTodo } from "../query/complete-todo";
import { useDeleteTodo } from "../query/delete-todo";
import { usePrioritizeTodo } from "../query/prioritize-todo";
import { useEditTodo } from "../query/update-todo";
import { useEditTodoInstance } from "../query/update-todo-instance";
import { useReorderTodo } from "../query/reorder-todo";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { cn } from "@/lib/utils";
import { flattenNotesToPlainText } from "@/lib/richNotes";
import { useLocale } from "@/lib/navigation";
import { useSearchParams } from "react-router-dom";
import {
  buildTimelineSections,
  compareTodosWithinDay,
  findSectionKeyForDayKey,
} from "@/lib/timeline/buildTimelineSections";
import {
  TODO_FOCUS_DATE_QUERY_PARAM,
  TODO_FOCUS_TASK_QUERY_PARAM,
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
  // Weekday + month + day, no year — matches the timeline section headers (and
  // Android/iOS) so Overdue dates read the same everywhere. Commas dropped to
  // mirror the native header style ("Mon Jun 8").
  return new Intl.DateTimeFormat(locale, {
    weekday: "short",
    month: "short",
    day: "numeric",
    timeZone: timeZone || "UTC",
  })
    .formatToParts(date)
    .filter((part) => !(part.type === "literal" && /,/.test(part.value)))
    .map((part) => (part.type === "literal" ? part.value.replace(/,/g, "") : part.value))
    .join(" ")
    .replace(/\s+/g, " ")
    .trim();
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

  // Same day group: fall back to the FIXED within-day todo ordering (pinned,
  // due asc, priority, modified desc, id) — see src/lib/taskSort.ts.
  return compareTodosWithinDay(a.todo, b.todo);
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

const isPriorityTask = (priority: string | null | undefined) => {
  const normalized = (priority || "").trim().toLowerCase();
  return normalized === "medium" ||
    normalized === "high" ||
    normalized === "important" ||
    normalized === "urgent";
};

const isOverdueTask = (due: Date) => due < new Date();

// `emptyTitle`/`emptyBody` are `app` keys, not copy: the empty scene says the
// same thing here as it does on Android and iOS, in whichever language.
const SCOPE_CONFIG: Record<
  TimelineScope,
  { icon: React.ElementType; heading: string; emptyTitle: string; emptyBody: string }
> = {
  today: { icon: Sun, heading: "today", emptyTitle: "todayEmpty", emptyBody: "todayEmptyBody" },
  overdue: { icon: Clock3, heading: "Overdue", emptyTitle: "overdueEmpty", emptyBody: "overdueEmptyBody" },
  scheduled: { icon: CalendarClock, heading: "Scheduled", emptyTitle: "scheduledEmpty", emptyBody: "scheduledEmptyBody" },
  all: { icon: Layers, heading: "All Tasks", emptyTitle: "allTasksEmpty", emptyBody: "allTasksEmptyBody" },
  priority: { icon: Flag, heading: "priority", emptyTitle: "priorityEmpty", emptyBody: "priorityEmptyBody" },
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
  const { todos, todoLoading } = useTodoTimeline();

  const timeline = isTimelineScope(scope);

  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);
  const [earlierExpanded, setEarlierExpanded] = useState(false);
  // Empty date buckets are drop targets and nothing else, so they exist only for
  // the length of a drag.
  const [dragActive, setDragActive] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const sentinelRef = useRef<HTMLDivElement | null>(null);
  const { icon: ScopeIcon, emptyTitle, emptyBody, heading: scopeHeading } = SCOPE_CONFIG[scope];
  const pageHeading = scope === "today" || scope === "priority" ? appDict(scopeHeading) : scopeHeading;
  const barSlots = useNativePageBarSlots();
  const focusedTaskId = searchParams.get(TODO_FOCUS_TASK_QUERY_PARAM);
  const focusedDateKey = useMemo(() => {
    const value = searchParams.get(TODO_FOCUS_DATE_QUERY_PARAM);
    return isTodoFocusDateKey(value) ? value : null;
  }, [searchParams]);
  // Search is scoped to this screen: it narrows the tasks this scope already
  // shows and reaches nothing outside them, so the priority screen searches
  // priority tasks and the overdue screen searches overdue ones.
  const scopedTodos = useMemo(() => {
    const inScope =
      scope === "priority" ? todos.filter((todo) => isPriorityTask(todo.priority)) : todos;
    const query = searchQuery.trim().toLowerCase();
    if (!query) return inScope;
    return inScope.filter((todo) => {
      const title = todo.title.toLowerCase();
      const description = flattenNotesToPlainText(todo.description).toLowerCase();
      return title.includes(query) || description.includes(query);
    });
  }, [scope, searchQuery, todos]);

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

  const scopeFilteredItems = useMemo(() => {
    if (scope === "today") {
      return timelineItems.filter((item) => item.dayDiff === 0);
    }
    if (scope === "overdue") {
      return timelineItems
        .filter((item) => isOverdueTask(item.todo.due))
        .sort(compareOverdueTimelineItems);
    }
    if (scope === "scheduled") {
      const now = new Date();
      return timelineItems.filter((item) => item.todo.due >= now);
    }
    return timelineItems;
  }, [timelineItems, scope]);

  // Today screen: Morning (<12) / Afternoon (12–18) / Tonight (≥18), matching native.
  const todayBuckets = useMemo(() => {
    if (scope !== "today") return [];
    const groups: Record<"Morning" | "Afternoon" | "Tonight", TodoItemType[]> = {
      Morning: [],
      Afternoon: [],
      Tonight: [],
    };
    for (const item of scopeFilteredItems) {
      const hour = getTimeZoneDate(item.todo.due, userTZ?.timeZone).getHours();
      const label = hour < 12 ? "Morning" : hour < 18 ? "Afternoon" : "Tonight";
      groups[label].push(item.todo);
    }
    return (["Morning", "Afternoon", "Tonight"] as const).map((label) => ({
      label,
      todos: groups[label],
    }));
  }, [scope, scopeFilteredItems, userTZ?.timeZone]);

  // The native date-bucketed timeline (All / Priority / Scheduled).
  const timelineSections = useMemo(() => {
    if (!timeline) return [];
    // Every scope now shows only the dates that hold tasks; the empty buckets
    // come back for the length of a drag so there is somewhere to drop.
    return buildTimelineSections({
      todos: timelineItems.map((item) => item.todo),
      locale,
      timeZone: userTZ?.timeZone,
      futureOnly: scope === "scheduled",
      placesEarlierBeforeToday: scope !== "scheduled",
      includeEmptyDropTargets: dragActive,
      todayLabel: appDict("today"),
      tomorrowLabel: appDict("tomorrow"),
    });
  }, [appDict, dragActive, locale, scope, timeline, timelineItems, userTZ?.timeZone]);

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

  // What Select all reaches: the rows this screen has actually rendered, in
  // display order. Both branches read from `visibleTimelineItems`, so the two
  // stay in step — the bucketed scopes through the timeline's own sections
  // (collapsed Earlier included), Today/Overdue directly.
  //
  // Deliberately NOT the whole unpaged set. Today/Overdue render `PAGE_SIZE` at
  // a time behind an IntersectionObserver, so selecting the unpaged set let one
  // tap of Select all + Delete reach tasks the user had never scrolled to — 60
  // rows staged from 10 on screen. Android and iOS have no paging, so there
  // "everything on screen" and "everything in the scope" are the same set; this
  // keeps web's Select all honest against the same sentence in the guide.
  const selectableTodos = useMemo(
    () =>
      timeline
        ? timelineSections.flatMap((section) => section.todos)
        : visibleTimelineItems.map((item) => item.todo),
    [timeline, timelineSections, visibleTimelineItems],
  );

  const hasMore = !timeline && visibleCount < scopeFilteredItems.length;
  const isSearching = Boolean(searchQuery.trim());
  // Render the date buckets only when this scope actually has tasks; an empty
  // scope shows the native-style centered empty message instead.
  const showTimeline = timeline && hasScopedTasks;
  // Every scope shows the same native-style centered empty message when there
  // are no tasks (Today also keeps its Morning/Afternoon/Tonight headers above).
  const showEmpty = !todoLoading && !hasScopedTasks && !isSearching;
  // A search that turns nothing up is a different state from an empty scope:
  // the scope may be full, this word just is not in it.
  const showNoResults = !todoLoading && !hasScopedTasks && isSearching;
  const { completedTodos } = useCompletedTodo();
  const completedTodayCount = useMemo(
    () =>
      completedTodos.filter((todo) => isSameDay(todo.completedAt, new Date()))
        .length,
    [completedTodos],
  );
  const isDayDone = scope === "today" && showEmpty && completedTodayCount > 0;

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
    // `dragActive` is in the guard, not just the deps: `timelineSections` now
    // changes identity at drag start and again at drag end, and this effect
    // would smooth-scroll a bucket to the top of the viewport with the user's
    // finger down. It is reachable — deleting a task pushes
    // /app/scheduled?focusDate=…&focusMode=deleted and never clears the param,
    // so focusDate can sit in the URL with no focusTask, which is exactly this
    // effect's condition. Worse, the day it targets is usually empty at rest
    // and only becomes findable when the drag conjures its bucket.
    if (!timeline || !focusedDateKey || focusedTaskId || dragActive) {
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
  }, [dragActive, focusedDateKey, focusedTaskId, timeline, timelineSections, userTZ?.timeZone]);

  return (
    <TodoMutationProvider
      useCompleteTodo={useCompleteTodo}
      useDeleteTodo={useDeleteTodo}
      useEditTodo={useEditTodo}
      useEditTodoInstance={useEditTodoInstance}
      usePrioritizeTodo={usePrioritizeTodo}
      useReorderTodo={useReorderTodo}
    >
      <TaskSelectionProvider rows={selectableTodos}>
        <div className="mb-20">
          <ScreenWatermark icon={ScopeIcon} />
          {/* The search field is this page's pinned bar, so the header below
              renders only the block that scrolls away and docks its title into
              it — the same split the custom list uses. */}
          <MobileSearchHeader
            searchQuery={searchQuery}
            onSearchChange={setSearchQuery}
            placeholder={`${appDict("searchIn")} ${pageHeading}...`}
            // Safe to read the searched set here: the bar clears its query as it
            // collapses, so while the magnifier is the thing on screen this is
            // "does the scope hold anything at all". Held back until the first
            // load settles, or the button would blink out and back on every visit.
            searchUnavailable={!todoLoading && !hasScopedTasks}
            pageCollapse={{
              ...barSlots,
              title: pageHeading,
              accentColor: timelineScopeAccentColors[scope],
            }}
            trailingAction={
              <div className="flex shrink-0 items-center gap-2">
                {/* Explicit entry point, never a long-press: that gesture is
                    drag-to-reschedule on the native clients, and the three
                    surfaces enter selection the same way. */}
                <BulkSelectButton />
                <SummaryButton mode={scope} />
              </div>
            }
          />

          <NativePageHeader
            title={pageHeading}
            accentColor={timelineScopeAccentColors[scope]}
            icon={ScopeIcon}
            barSlots={barSlots}
          />

          {/* Stood down while a query finds nothing: a week-summary card sitting
              above "no matching tasks" reads as a result. Same reason the three
              time-of-day drop targets are suppressed below. */}
          {scope === "today" && !showNoResults && <WeekInReviewCard />}

          {todoLoading && <TodoListLoading heading={pageHeading} />}

          {showTimeline && (
            <TimelineSections
              sections={timelineSections}
              timeZone={userTZ?.timeZone}
              focusedTaskId={focusedTaskId}
              focusedDateKey={focusedDateKey}
              // A live query outranks a shut bucket: these screens open with
              // Earlier closed, and a task the search turns up in there must not
              // stay hidden behind its header. Native makes the same call.
              earlierExpanded={earlierExpanded || isSearching}
              onToggleEarlier={() => setEarlierExpanded((value) => !value)}
              onDragActiveChange={setDragActive}
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
                </div>
                <TodoGroup
                  todos={section.todos}
                  overdue={section.dayDiff < 0}
                  perTaskOverdue={section.dayDiff === 0}
                  highlightedTodoId={focusedTaskId}
                  showOverdueTag={false}
                  className="border-b border-border/60 pb-1"
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
                </div>
                <TodoGroup
                  todos={section.todos}
                  overdue={true}
                  highlightedTodoId={focusedTaskId}
                  showOverdueTag={false}
                  className="border-b border-border/60 pb-1"
                />
              </section>
            ))}

          {/* The three time buckets are drop targets, so they stand empty on a
              quiet day — but under a search that found nothing they would read as
              three results, so they go with the tasks. */}
          {scope === "today" && !showNoResults && (
            <TodayBucketDndContext timeZone={userTZ?.timeZone}>
              {todayBuckets.map((bucket, index) => (
                <TodayBucketDroppable
                  key={bucket.label}
                  bucket={bucket.label}
                  targetHour={
                    TODAY_BUCKETS.find((b) => b.label === bucket.label)?.targetHour ?? 9
                  }
                  isFirst={index === 0}
                >
                  {bucket.todos.map((todo) => (
                    <DraggableTodayTask
                      key={todo.id}
                      todo={todo}
                      currentBucket={bucket.label}
                      highlighted={focusedTaskId === todo.id}
                    />
                  ))}
                </TodayBucketDroppable>
              ))}
            </TodayBucketDndContext>
          )}

          {/* Native-style centered empty message — for Today it sits below the
              Morning/Afternoon/Tonight headers; for other scopes it's the only body.
              Day Done: "finished everything" earns a calm payoff state instead of
              the generic no-tasks message. */}
          {showEmpty && (
            <EmptyState
              // Day Done keeps its own glyph and its date line: it is a payoff,
              // not an absence, and the scope's own icon would undersell it.
              icon={isDayDone ? CheckCheck : ScopeIcon}
              accentColor={timelineScopeAccentColors[scope]}
              title={isDayDone ? appDict("allDoneToday") : appDict(emptyTitle)}
              description={
                isDayDone
                  ? new Intl.DateTimeFormat(locale, {
                      weekday: "long",
                      day: "numeric",
                      month: "long",
                    }).format(new Date())
                  : appDict(emptyBody)
              }
              // Finishing the scope is a payoff, not an absence: the confetti is
              // for the tick that emptied it, not for a day with nothing in it.
              celebrate={taskJustCompleted()}
            />
          )}

          {/* No search results — the scope's own tasks simply do not carry this
              word. */}
          {showNoResults && (
            <EmptyState
              icon={Search}
              accentColor={timelineScopeAccentColors[scope]}
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

          {hasMore && (
            <div ref={sentinelRef} className="flex h-12 items-center justify-center">
              <span className="text-xs text-muted-foreground">Loading more tasks...</span>
            </div>
          )}
        </div>
      </TaskSelectionProvider>
    </TodoMutationProvider>
  );
};

export default AllTasksTimelineContainer;

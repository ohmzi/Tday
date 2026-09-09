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
import { useCelebrateEmptyTransition } from "@/hooks/use-celebrate-empty-transition";
import { timelineScopeAccentColors } from "@/components/app/nativeScreenTheme";
import SummaryButton from "@/features/summary/SummaryButton";
import WeekInReviewCard from "@/features/summary/WeekInReviewCard";
import TodoListLoading from "@/components/todo/component/TodoListLoading";
import TodoGroup from "@/components/todo/component/TodoGroup";
import TimelineSections from "@/components/todo/dnd/TimelineSections";
import TodayEarlierSection from "./TodayEarlierSection";
import { useEarlierExpandHandoff } from "../lib/useEarlierExpandHandoff";
import {
  TODAY_EARLIER_EXIT_MS,
  shouldShowTodayEmptyIllustration,
} from "../lib/todayEarlierIllustration";
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

// Stable identity so the memo below doesn't hand back a fresh empty array on
// every render for scopes other than Today.
const NO_EARLIER_TODOS: TodoItemType[] = [];

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
  // Generalizes the plain `useState(false)` this used to be: All/Priority/
  // Scheduled call `toggle(false)` below and get the exact same immediate
  // flip they always had; only Today's own Earlier passes a real
  // `illustrationShowing` value, which is what engages the requirement-3
  // hand-off. See `useEarlierExpandHandoff`'s own doc comment.
  const {
    expanded: earlierExpanded,
    handoffPending: earlierHandoffPending,
    toggle: toggleEarlierExpanded,
    setExpandedImmediately: setEarlierExpandedImmediately,
  } = useEarlierExpandHandoff(TODAY_EARLIER_EXIT_MS);
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
  // All three buckets stay visible (even empty ones) as long as the day holds at
  // least one task, so they read as live drop targets alongside the others. But
  // when the whole day is empty, `scopeFilteredItems` is already the same
  // dayDiff===0 set `hasScopedTasks` checks below — so this returns no buckets in
  // lockstep with `showEmpty`, letting the empty-state illustration own the
  // screen instead of three headerless buckets sitting above it.
  const todayBuckets = useMemo(() => {
    if (scope !== "today" || scopeFilteredItems.length === 0) return [];
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

  // Today's own "Earlier" bucket (requirement 2): reuses the exact same
  // section-building code path All/Priority/Scheduled already use for their
  // own Earlier bucket (`buildTimelineSections`'s `kind: "earlier"`, built
  // from `dayKey < todayKey`) rather than a fresh definition of "overdue" —
  // which also keeps it disjoint from Today's own `dayDiff === 0` set below,
  // so a task due earlier today (already past its time, but still *today*)
  // is never duplicated between the two. This day-boundary rule is NOT the
  // same set the standalone Overdue screen shows: that screen's `scope ===
  // "overdue"` branch below filters on `isOverdueTask` (`due < now`), a
  // timestamp comparison, so a task due earlier today but still pending is
  // Overdue there while staying out of this Earlier bucket on purpose. The
  // only thing shared with every other scope (Overdue included) is the raw
  // `timelineItems` array itself, sourced from the same `useTodoTimeline()`
  // query — no separate fetch, just a different filter applied downstream.
  const todayEarlierSection = useMemo(() => {
    if (scope !== "today") return null;
    const sections = buildTimelineSections({
      todos: timelineItems.map((item) => item.todo),
      locale,
      timeZone: userTZ?.timeZone,
      futureOnly: false,
      placesEarlierBeforeToday: true,
      includeEmptyDropTargets: false,
      todayLabel: appDict("today"),
      tomorrowLabel: appDict("tomorrow"),
    });
    return sections.find((section) => section.kind === "earlier") ?? null;
  }, [appDict, locale, scope, timelineItems, userTZ?.timeZone]);

  const earlierItems = todayEarlierSection?.todos ?? NO_EARLIER_TODOS;
  // NOT folded into `scopeFilteredItems`/`hasScopedTasks` below — see
  // `shouldShowTodayEmptyIllustration`'s own doc comment for why that
  // separation is exactly what keeps requirement 1 intact.
  const todayHasEarlierItems = scope === "today" && earlierItems.length > 0;

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
  // Remote sibling of `taskJustCompleted()` below — fires for a completion on
  // another device or by a collaborator, not just this tab's own tap.
  const remoteEmptied = useCelebrateEmptyTransition(!hasScopedTasks);
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
  // Finishing the scope is a payoff, not an absence: the confetti is for the
  // tick that emptied it, not for a day with nothing in it. Whether that tick
  // happened here, on another device, or from a collaborator on a shared
  // list. Hoisted (rather than inlined on `<EmptyState celebrate>` below) so
  // Today's own illustration/Earlier hand-off reads the exact same signal —
  // see `shouldShowTodayEmptyIllustration`.
  const celebrate = taskJustCompleted() || remoteEmptied;
  // Requirements 1-3: who owns the empty-state slot once `showEmpty` is true.
  // Degenerates to plain `showEmpty` whenever `todayHasEarlierItems` is false
  // (every non-Today scope, and Today with no overdue tasks), so this is a
  // no-op everywhere except the new Earlier interaction.
  const showEmptyIllustration = shouldShowTodayEmptyIllustration({
    showEmpty,
    hasEarlierItems: todayHasEarlierItems,
    earlierExpanded,
    earlierHandoffPending,
    celebrate,
  });

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
      setEarlierExpandedImmediately(true);
    }
  }, [focusedTaskId, setEarlierExpandedImmediately, timeline, timelineSections]);

  // Same, for Today's own Earlier: a deep-linked/focused overdue task should
  // not sit hidden behind a collapsed header. Immediate (no hand-off beat) —
  // there is no illustration to sequence against on a direct navigation.
  useEffect(() => {
    if (scope !== "today" || !focusedTaskId) return;
    if (earlierItems.some((todo) => todo.id === focusedTaskId)) {
      setEarlierExpandedImmediately(true);
    }
  }, [earlierItems, focusedTaskId, scope, setEarlierExpandedImmediately]);

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
              // `illustrationShowing: false` — these scopes have no
              // Today-style illustration to hand off from, so this is the
              // exact plain immediate toggle they always had.
              onToggleEarlier={() => toggleEarlierExpanded(false)}
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

          {/* The three time buckets are drop targets, so they stay visible (even
              empty ones) as long as the day holds at least one task — but under a
              search that found nothing they would read as three results, so they
              go with the tasks. On a genuinely empty day `todayBuckets` is `[]`
              (see the note above it), so nothing renders here at all. */}
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

          {/* Native-style centered empty message — for Today, `showEmpty` only
              fires when the day has zero tasks, the same condition that leaves
              `todayBuckets` empty, so this never renders below headerless
              Morning/Afternoon/Tonight sections; for other scopes it's the only
              body. Day Done: "finished everything" earns a calm payoff state
              instead of the generic no-tasks message.

              `showEmptyIllustration` (not `showEmpty` directly): identical to
              `showEmpty` everywhere except Today with a non-empty, expanded
              Earlier — see `shouldShowTodayEmptyIllustration`. The wrapper div
              only ever carries the exit animation while `earlierHandoffPending`
              is genuinely true (i.e. only for Today), so it is inert elsewhere. */}
          {showEmptyIllustration && (
            <div
              className={cn(earlierHandoffPending && "tday-empty-exit")}
              style={
                earlierHandoffPending
                  ? { animationDuration: `${TODAY_EARLIER_EXIT_MS}ms` }
                  : undefined
              }
            >
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
                celebrate={celebrate}
              />
            </div>
          )}

          {/* Today's own "Earlier" bucket (requirement 2), always reachable at
              the bottom of the screen whenever it holds anything — independent
              of `showEmptyIllustration` above, so it renders the same whether
              Today still has pending tasks, is empty with the illustration
              showing, or is empty with Earlier already expanded (in which case
              its rows are what fills that slot — see the illustration block
              above). `!showNoResults` mirrors the gate already used for the
              time-of-day buckets above: a search with no results goes with the
              tasks, not with this. */}
          {scope === "today" && !showNoResults && todayHasEarlierItems && (
            <TodayEarlierSection
              todos={earlierItems}
              expanded={earlierExpanded && !earlierHandoffPending}
              onToggle={() => toggleEarlierExpanded(showEmptyIllustration)}
              highlightedTodoId={focusedTaskId}
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

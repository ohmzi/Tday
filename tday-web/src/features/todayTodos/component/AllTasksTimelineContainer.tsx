import React, { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { CalendarClock, Clock3, Flag, Layers, Search, Sun } from "lucide-react";
import NativePageHeader, { useNativePageBarSlots } from "@/components/app/NativePageHeader";
import MobileSearchHeader from "@/components/ui/MobileSearchHeader";
import ScreenWatermark from "@/components/app/ScreenWatermark";
import EmptyState from "@/components/app/EmptyState";
import { timelineScopeAccentColors } from "@/components/app/nativeScreenTheme";
import SummaryButton from "@/features/summary/SummaryButton";
import WeekInReviewCard from "@/features/summary/WeekInReviewCard";
import TodoListLoading from "@/components/todo/component/TodoListLoading";
import TimelineSections from "@/components/todo/dnd/TimelineSections";
import TodayEarlierSection from "./TodayEarlierSection";
import TimelineEmptyState from "./TimelineEmptyState";
import OverdueDaySections from "./OverdueDaySections";
import TodayTimeBuckets from "./TodayTimeBuckets";
import { useEarlierExpandHandoff } from "../lib/useEarlierExpandHandoff";
import { useTodayEarlierBucket } from "../lib/useTodayEarlierBucket";
import { useTimelinePaging } from "../lib/useTimelinePaging";
import { useScopedTimelineItems } from "../lib/useScopedTimelineItems";
import { useTodayBuckets } from "../lib/useTodayBuckets";
import { useTimelineSections } from "../lib/useTimelineSections";
import { useTimelineEmptyState } from "../lib/useTimelineEmptyState";
import { isTimelineScope } from "../lib/timelineScopeHelpers";
import { TODAY_EARLIER_EXIT_MS } from "../lib/todayEarlierIllustration";
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
import { useLocale } from "@/lib/navigation";
import { useSearchParams } from "react-router-dom";
import {
  TODO_FOCUS_DATE_QUERY_PARAM,
  TODO_FOCUS_TASK_QUERY_PARAM,
  isTodoFocusDateKey,
} from "@/lib/todoToastNavigation";

export type TimelineItem = {
  todo: TodoItemType;
  dayDiff: number;
  dayKey: string;
  label: string;
};

export type TimelineSection = {
  key: string;
  label: string;
  dayDiff: number;
  todos: TodoItemType[];
};

export type TimelineScope = "today" | "scheduled" | "all" | "priority" | "overdue";

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

// Today/Priority headings are locale keys (translated); the rest are already
// the display string in `SCOPE_CONFIG`.
const getPageHeading = (
  scope: TimelineScope,
  scopeHeading: string,
  appDict: (key: string) => string,
) => (scope === "today" || scope === "priority" ? appDict(scopeHeading) : scopeHeading);

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

  // One state machine shared by every scope's own Earlier toggle: each call
  // site below passes `showEmptyIllustration` — this scope's own "is the
  // illustration currently on screen" — as `illustrationShowing`, so an
  // expand only engages the requirement-3 hand-off on the scopes where that
  // can actually be true right now, and stays the exact plain immediate flip
  // everywhere else (`illustrationShowing: false`). See
  // `useEarlierExpandHandoff`'s own doc comment.
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
  const { icon: ScopeIcon, emptyTitle, emptyBody, heading: scopeHeading } = SCOPE_CONFIG[scope];
  const pageHeading = getPageHeading(scope, scopeHeading, appDict);
  const barSlots = useNativePageBarSlots();
  const focusedTaskId = searchParams.get(TODO_FOCUS_TASK_QUERY_PARAM);
  const focusedDateKey = useMemo(() => {
    const value = searchParams.get(TODO_FOCUS_DATE_QUERY_PARAM);
    return isTodoFocusDateKey(value) ? value : null;
  }, [searchParams]);

  const { timelineItems, scopeFilteredItems } = useScopedTimelineItems({
    scope,
    todos,
    searchQuery,
    locale,
    timeZone: userTZ?.timeZone,
    appDict,
  });

  const todayBuckets = useTodayBuckets({
    scope,
    scopeFilteredItems,
    timeZone: userTZ?.timeZone,
  });

  // The native date-bucketed timeline (All / Priority / Scheduled).
  const timelineSections = useTimelineSections({
    timeline,
    timelineItems,
    scope,
    locale,
    timeZone: userTZ?.timeZone,
    dragActive,
    appDict,
    focusedTaskId,
    focusedDateKey,
    setEarlierExpandedImmediately,
  });

  // Today's own "Earlier" bucket (requirement 2), plus its deep-link
  // auto-expand behavior — see `useTodayEarlierBucket`'s own doc comment for
  // why it reuses `buildTimelineSections` instead of a fresh "overdue"
  // definition, and how that set differs from the standalone Overdue
  // screen's `isOverdueTask` filter.
  const { earlierItems, todayHasEarlierItems } = useTodayEarlierBucket({
    scope,
    timelineItems,
    locale,
    timeZone: userTZ?.timeZone,
    appDict,
    focusedTaskId,
    setEarlierExpandedImmediately,
  });

  // Today/Overdue's own paging, plus what Select all reaches — see
  // `useTimelinePaging`'s own doc comment.
  const { earlierSections, regularSections, hasMore, sentinelRef, selectableTodos } =
    useTimelinePaging({
      scopeFilteredItems,
      timeline,
      timelineSections,
      focusedDateKey,
      focusedTaskId,
    });

  const isSearching = Boolean(searchQuery.trim());

  // Every scope's empty/loading/no-results/celebration derivation — see
  // `useTimelineEmptyState`'s own doc comment.
  const {
    hasScopedTasks,
    showTimeline,
    showNoResults,
    showTodayScope,
    isDayDone,
    celebrate,
    showEmptyIllustration,
    showTodayEarlierSection,
  } = useTimelineEmptyState({
    scope,
    scopeFilteredItems,
    timeline,
    todoLoading,
    isSearching,
    earlierExpanded,
    earlierHandoffPending,
    todayHasEarlierItems,
  });

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
          {showTodayScope && <WeekInReviewCard />}

          {todoLoading && <TodoListLoading heading={pageHeading} />}

          {/* The three time buckets are drop targets, so they stay visible (even
              empty ones) as long as the day holds at least one task — but under a
              search that found nothing they would read as three results, so they
              go with the tasks. On a genuinely empty day `todayBuckets` is `[]`
              (see the note above it), so nothing renders here at all. */}
          {showTodayScope && (
            <TodayTimeBuckets
              todayBuckets={todayBuckets}
              timeZone={userTZ?.timeZone}
              focusedTaskId={focusedTaskId}
            />
          )}

          {/* Native-style centered empty message — for Today, `showEmpty` only
              fires when the day has zero tasks, the same condition that leaves
              `todayBuckets` empty, so this never renders below headerless
              Morning/Afternoon/Tonight sections; for other scopes it's the only
              body. Day Done: "finished everything" earns a calm payoff state
              instead of the generic no-tasks message.

              `showEmptyIllustration` (not `showEmpty` directly): identical to
              `showEmpty` everywhere except a scope with a non-empty, expanded
              Earlier bucket — see `shouldShowTodayEmptyIllustration`. The
              wrapper div only ever carries the exit animation while
              `earlierHandoffPending` is genuinely true, so it is inert outside
              an Earlier hand-off.

              Rendered BEFORE the Earlier-holding blocks below (Today's own
              `TodayEarlierSection`, and All/Priority/Scheduled's Earlier
              bucket inside `TimelineSections`) so the illustration always sits
              above Earlier — never simultaneously replaced by it — matching
              requirement 3's hand-off order. Harmless when both are showing
              current tasks too: `showEmptyIllustration` is false whenever
              `showTimeline`/`showTodayEarlierSection` render anything besides
              a bare collapsed Earlier header, so the two blocks are never both
              "the body" of a populated screen at once. */}
          {showEmptyIllustration && (
            <TimelineEmptyState
              icon={ScopeIcon}
              accentColor={timelineScopeAccentColors[scope]}
              isDayDone={isDayDone}
              celebrate={celebrate}
              earlierHandoffPending={earlierHandoffPending}
              locale={locale}
              emptyTitle={emptyTitle}
              emptyBody={emptyBody}
              appDict={appDict}
            />
          )}

          {showTimeline && (
            <TimelineSections
              sections={timelineSections}
              timeZone={userTZ?.timeZone}
              focusedTaskId={focusedTaskId}
              focusedDateKey={focusedDateKey}
              // A live query outranks a shut bucket: these screens open with
              // Earlier closed, and a task the search turns up in there must not
              // stay hidden behind its header. Native makes the same call.
              // `!earlierHandoffPending`: mid hand-off, Earlier's own rows stay
              // hidden until the illustration above has actually finished
              // exiting — same requirement-3 sequencing Today's own Earlier
              // section observes.
              earlierExpanded={(earlierExpanded && !earlierHandoffPending) || isSearching}
              // Passes `showEmptyIllustration` through exactly like Today's own
              // `TodayEarlierSection` does below: when the illustration
              // currently owns the slot, expanding Earlier hands off through
              // it first (requirement 3) instead of the plain immediate toggle
              // this used to always be — see `useEarlierExpandHandoff`'s own
              // doc comment for why a single boolean is enough to cover both.
              onToggleEarlier={() => toggleEarlierExpanded(showEmptyIllustration)}
              onDragActiveChange={setDragActive}
            />
          )}

          {scope === "overdue" && (
            <OverdueDaySections
              regularSections={regularSections}
              earlierSections={earlierSections}
              focusedDateKey={focusedDateKey}
              focusedTaskId={focusedTaskId}
            />
          )}

          {/* Today's own "Earlier" bucket (requirement 2) — see
              `useTimelineEmptyState`'s own doc comment for `showTodayEarlierSection`. */}
          {showTodayEarlierSection && (
            <TodayEarlierSection
              todos={earlierItems}
              label={appDict("overdue")}
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

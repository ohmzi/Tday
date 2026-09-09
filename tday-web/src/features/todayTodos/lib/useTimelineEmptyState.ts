import { useMemo } from "react";
import { isSameDay } from "date-fns";
import { useCompletedTodo } from "@/features/completed/query/get-completedTodo";
import { useCelebrateEmptyTransition } from "@/hooks/use-celebrate-empty-transition";
import { taskJustCompleted } from "@/lib/task-completion-signal";
import { shouldShowTodayEmptyIllustration } from "./todayEarlierIllustration";
import { isTimelineScope, splitEarlierItems } from "./timelineScopeHelpers";
import type { TimelineItem, TimelineScope } from "../component/AllTasksTimelineContainer";

/**
 * Every scope's empty/loading/no-results/celebration derivation, in one
 * place: whether the scope has anything at all (`hasScopedTasks`), which of
 * the three body states owns the screen (`showTimeline` / `showEmpty` /
 * `showNoResults`), Today's own "day done" payoff (`isDayDone`, `celebrate`),
 * and — folding in whichever scope's own Earlier bucket applies — who owns
 * the empty-state slot once `showEmpty` is true (`showEmptyIllustration`, see
 * `shouldShowTodayEmptyIllustration`'s own doc comment for the full
 * requirements-1-3 rationale) plus whether Today's own separate Earlier
 * section component should render (`showTodayEarlierSection`).
 *
 * Earlier/empty-state parity (All/Priority/Scheduled): unlike Today —
 * whose own `dayDiff === 0` scope is Earlier-free by construction, with
 * overdue tasks fetched into a wholly separate `todayHasEarlierItems`/
 * `earlierItems` pair (see `useTodayEarlierBucket`) — All/Priority's
 * `scopeFilteredItems` already comes back with overdue tasks mixed in (see
 * `useScopedTimelineItems`'s own doc comment): they fall out of the same
 * `buildTimelineSections` bucketing downstream in `useTimelineSections`
 * instead of a second fetch. So "this scope's own zero" has to read the same
 * `dayDiff < 0` rule that bucketing already sorts by (`getTimelinePriority`
 * in `timelineScopeHelpers.ts`) rather than treat every item in the array as
 * equally "current" — `hasNonEarlierScopedTasks` below is that reduction.
 * Scheduled's own `scopeFilteredItems` never contains a `dayDiff < 0` item to
 * begin with (its `buildTimelineSections` call passes `futureOnly: true`, so
 * it never builds an Earlier bucket at all — confirmed by reading
 * `buildTimelineSections`, not assumed), so this degenerates to a no-op
 * there. The standalone Overdue screen keeps its pre-existing flat
 * definition — it has no nested Earlier concept for this feature to touch.
 */
export function useTimelineEmptyState({
  scope,
  scopeFilteredItems,
  timeline,
  todoLoading,
  isSearching,
  earlierExpanded,
  earlierHandoffPending,
  todayHasEarlierItems,
}: {
  scope: TimelineScope;
  scopeFilteredItems: TimelineItem[];
  timeline: boolean;
  todoLoading: boolean;
  isSearching: boolean;
  earlierExpanded: boolean;
  earlierHandoffPending: boolean;
  /** Today's own separately-fetched Earlier signal — see `useTodayEarlierBucket`. Unused for every other scope. */
  todayHasEarlierItems: boolean;
}) {
  const hasScopedTasks = useMemo(() => {
    if (scope === "today") {
      return scopeFilteredItems.some((item) => item.dayDiff === 0);
    }
    return scopeFilteredItems.length > 0;
  }, [scopeFilteredItems, scope]);

  // Render the date buckets only when this scope actually has tasks (its own
  // Earlier bucket included, since that is what `TimelineSections` renders
  // when nothing else in the scope does — see `AllTasksTimelineContainer`'s
  // JSX ordering comment); an empty scope shows the native-style centered
  // empty message instead.
  const showTimeline = timeline && hasScopedTasks;
  // `splitEarlierItems` (`timelineScopeHelpers.ts`) is the same `dayDiff < 0`
  // reduction `buildTimelineSections` buckets by — reused here, not
  // reinvented, for the scopes whose Earlier bucket is a display-time subset
  // of `scopeFilteredItems`.
  const { hasEarlierItems: timelineHasEarlierItems, hasCurrentItems: timelineHasCurrentItems } =
    useMemo(
      () => (isTimelineScope(scope) ? splitEarlierItems(scopeFilteredItems) : { hasEarlierItems: false, hasCurrentItems: false }),
      [scope, scopeFilteredItems],
    );
  // Generalizes `todayHasEarlierItems` (Today's own, separately-sourced
  // signal) to every scope whose Earlier bucket is that same display-time
  // subset instead — see this hook's own doc comment.
  const hasEarlierItems = scope === "today" ? todayHasEarlierItems : timelineHasEarlierItems;
  // Requirement 1, generalized: the scope's own "zero" excludes whatever its
  // Earlier bucket already holds, so a screen with only overdue tasks left
  // still shows the "all done" illustration instead of nothing at all.
  const hasNonEarlierScopedTasks = isTimelineScope(scope) ? timelineHasCurrentItems : hasScopedTasks;
  // Every scope shows the same native-style centered empty message when there
  // are no non-Earlier tasks (Today also keeps its Morning/Afternoon/Tonight
  // headers above).
  const showEmpty = !todoLoading && !hasNonEarlierScopedTasks && !isSearching;
  // Remote sibling of `taskJustCompleted()` below — fires for a completion on
  // another device or by a collaborator, not just this tab's own tap.
  // Requirement 4: watches the non-Earlier count, so finishing every current
  // task still celebrates however many overdue tasks Earlier still holds.
  const remoteEmptied = useCelebrateEmptyTransition(!hasNonEarlierScopedTasks);
  // A search that turns nothing up is a different state from an empty scope:
  // the scope may be full, this word just is not in it. Deliberately still
  // the full (Earlier-included) count: a query that matches only an overdue
  // task is a result, not "no results" — it surfaces inside Earlier, forced
  // open by the search (see `earlierExpanded || isSearching` below).
  const showNoResults = !todoLoading && !hasScopedTasks && isSearching;
  // Today-only chrome that a search-with-no-results state stands down
  // together: the week summary, the three time-of-day buckets, and Earlier's
  // own header all share this exact gate below.
  const showTodayScope = scope === "today" && !showNoResults;
  const { completedTodos } = useCompletedTodo();
  const completedTodayCount = useMemo(
    () => completedTodos.filter((todo) => isSameDay(todo.completedAt, new Date())).length,
    [completedTodos],
  );
  const isDayDone = scope === "today" && showEmpty && completedTodayCount > 0;
  // Finishing the scope is a payoff, not an absence: the confetti is for the
  // tick that emptied it, not for a day with nothing in it. Whether that tick
  // happened here, on another device, or from a collaborator on a shared
  // list. Hoisted (rather than inlined on `<EmptyState celebrate>`) so
  // Today's own illustration/Earlier hand-off reads the exact same signal —
  // see `shouldShowTodayEmptyIllustration`.
  const celebrate = taskJustCompleted() || remoteEmptied;
  // Requirements 1-3: who owns the empty-state slot once `showEmpty` is true.
  // Degenerates to plain `showEmpty` whenever `hasEarlierItems` is false (any
  // scope with no Earlier bucket, or none of Today/All/Priority holding
  // overdue tasks right now), so this is a no-op everywhere except the
  // Earlier interaction — now shared by every scope whose Earlier bucket can
  // hold tasks while its own "current" set is empty, not just Today's.
  const showEmptyIllustration = shouldShowTodayEmptyIllustration({
    showEmpty,
    hasEarlierItems,
    earlierExpanded,
    earlierHandoffPending,
    celebrate,
  });
  // Today's own "Earlier" bucket (requirement 2): always reachable at the
  // bottom of the screen whenever it holds anything — independent of
  // `showEmptyIllustration`, so it renders the same whether Today still has
  // pending tasks, is empty with the illustration showing, or is empty with
  // Earlier already expanded (in which case its rows are what fills that slot
  // — see the illustration block). `showTodayScope` mirrors the gate already
  // used for the time-of-day buckets: a search with no results goes with the
  // tasks, not with this.
  const showTodayEarlierSection = showTodayScope && todayHasEarlierItems;

  return {
    hasScopedTasks,
    showTimeline,
    showEmpty,
    showNoResults,
    showTodayScope,
    isDayDone,
    celebrate,
    showEmptyIllustration,
    showTodayEarlierSection,
  };
}

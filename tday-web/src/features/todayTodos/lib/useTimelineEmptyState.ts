import { useMemo } from "react";
import { isSameDay } from "date-fns";
import { useCompletedTodo } from "@/features/completed/query/get-completedTodo";
import { useCelebrateEmptyTransition } from "@/hooks/use-celebrate-empty-transition";
import { taskJustCompleted } from "@/lib/task-completion-signal";
import { shouldShowTodayEmptyIllustration } from "./todayEarlierIllustration";
import type { TimelineItem, TimelineScope } from "../component/AllTasksTimelineContainer";

/**
 * Every scope's empty/loading/no-results/celebration derivation, in one
 * place: whether the scope has anything at all (`hasScopedTasks`), which of
 * the three body states owns the screen (`showTimeline` / `showEmpty` /
 * `showNoResults`), Today's own "day done" payoff (`isDayDone`, `celebrate`),
 * and — folding in Today's separate Earlier bucket — who owns the empty-state
 * slot once `showEmpty` is true (`showEmptyIllustration`, see
 * `shouldShowTodayEmptyIllustration`'s own doc comment for the full
 * requirements-1-3 rationale) plus whether Today's own Earlier section itself
 * should render (`showTodayEarlierSection`).
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
  todayHasEarlierItems: boolean;
}) {
  const hasScopedTasks = useMemo(() => {
    if (scope === "today") {
      return scopeFilteredItems.some((item) => item.dayDiff === 0);
    }
    return scopeFilteredItems.length > 0;
  }, [scopeFilteredItems, scope]);

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
  // Degenerates to plain `showEmpty` whenever `todayHasEarlierItems` is false
  // (every non-Today scope, and Today with no overdue tasks), so this is a
  // no-op everywhere except the Earlier interaction.
  const showEmptyIllustration = shouldShowTodayEmptyIllustration({
    showEmpty,
    hasEarlierItems: todayHasEarlierItems,
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

import { useMemo } from "react";
import { isSameDay } from "date-fns";
import { useCompletedTodo } from "@/features/completed/query/get-completedTodo";
import {
  useArrivalCancel,
  useEmptyTransitionOpenedAt,
} from "@/hooks/use-celebrate-empty-transition";
import {
  celebrationCancelledAtMs,
  shouldCelebrateEmptyState,
  useCompletionOpenedAt,
} from "@/lib/task-completion-signal";
import {
  earlierSlotChangesHands,
  shouldShowTodayEmptyIllustration,
} from "./todayEarlierIllustration";
import { useCelebrationSceneExit, type EarlierHandoff } from "./useEarlierExpandHandoff";
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
  earlierHandoff,
  beginSceneExit,
  todayHasEarlierItems,
  pendingRowCount,
}: {
  scope: TimelineScope;
  scopeFilteredItems: TimelineItem[];
  timeline: boolean;
  todoLoading: boolean;
  isSearching: boolean;
  earlierExpanded: boolean;
  earlierHandoff: EarlierHandoff;
  /** `useEarlierExpandHandoff`'s own `beginSceneExit` — see `useCelebrationSceneExit`. */
  beginSceneExit: () => void;
  /** Today's own separately-fetched Earlier signal — see `useTodayEarlierBucket`. Unused for every other scope. */
  todayHasEarlierItems: boolean;
  /**
   * The RAW, search-immune count of pending rows this screen can show, EVERY
   * bucket included — Earlier's overdue rows with the rest. The cancel's input,
   * and nothing else's: see `useArrivalCancel`, and note that this is
   * deliberately not any of the Earlier-excluding reductions below, because the
   * arrival it watches for is exactly the one those are written not to see.
   */
  pendingRowCount: number;
}) {
  // Before anything is derived: a row landing on this screen ends the
  // celebration, whether it came back from an undo, from a collaborator or from
  // the user's own typing. Counted, never inferred from the emptiness below —
  // an undone OVERDUE row moves none of it.
  useArrivalCancel(pendingRowCount);
  const hasScopedTasks = useMemo(() => {
    if (scope === "today") {
      return scopeFilteredItems.some((item) => item.dayDiff === 0);
    }
    return scopeFilteredItems.length > 0;
  }, [scopeFilteredItems, scope]);

  // Render the date buckets only when this scope actually has tasks (its own
  // Earlier bucket included, since that is what `TimelineSections` renders
  // when nothing else in the scope does — which is also why the sections come
  // FIRST in the JSX and the empty scene after them: the bucket carries the
  // Earlier header, and a header that moves when it is tapped is the bug that
  // ordering exists to avoid. See `AllTasksTimelineContainer`'s JSX ordering
  // comment, rewritten alongside this one); an empty scope shows the
  // native-style centered empty message instead.
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
  //
  // `todoLoading` is `useTodoTimeline`'s TanStack v5 `isLoading`
  // (`get-todo-timeline.ts`): `isPending && isFetching`, and `status` leaves
  // `pending` for good the moment `data !== undefined`. It therefore says "this
  // scope has no answer yet" and not "a request is in flight", which is the
  // distinction the whole gate rests on — an empty state is an ANSWER, and a
  // revalidation over cached rows must not withdraw one. This one gate governs
  // Today, All, Priority, Scheduled and Overdue together, so a term that
  // confused the two here would blank five screens on one refresh — which is
  // what the copy-pasted Android and iOS versions of it did. `isFetching` is
  // that term, and `useTodoTimeline` does not even return it. See
  // `useFloaterEmptyState` for the full argument and
  // `tests/guardrails/web-empty-state-refresh-immunity.test.ts` for the pin.
  const showEmpty = !todoLoading && !hasNonEarlierScopedTasks && !isSearching;
  // Remote sibling of the completion window below — fires for a completion on
  // another device or by a collaborator, not just this tab's own tap.
  // Requirement 4: watches the non-Earlier count, so finishing every current
  // task still celebrates however many overdue tasks Earlier still holds. The
  // CANCEL above deliberately watches a different number; see `useArrivalCancel`.
  const remoteEmptiedAt = useEmptyTransitionOpenedAt(!hasNonEarlierScopedTasks);
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
  //
  // Both windows consult the cancel, because an end that only one of them
  // observes is not an end (`useCelebrationWindowExpiry`'s own doc comment
  // makes the same argument about the clock). `shouldCelebrateEmptyState` is
  // where the two stamps and the cancel meet, and it is pure so the ordering —
  // undo ends it, a LATER completion re-opens it — is a unit test rather than a
  // claim about a container.
  const completionOpenedAt = useCompletionOpenedAt();
  const celebrate = shouldCelebrateEmptyState({
    completionOpenedAtMs: completionOpenedAt,
    remoteEmptiedAtMs: remoteEmptiedAt,
    cancelledAtMs: celebrationCancelledAtMs(),
  });
  // The window has an end now, so the scene it holds gets to leave over one.
  // The only screen shape where the end takes anything off the slot is this
  // one: empty scope, Earlier open, the scene sharing the slot with those rows
  // purely on the strength of the celebration. (It sits below them now rather
  // than above; what matters here is that it is there at all, and that the
  // window ending is what takes it away.)
  const sceneHeldForExit = useCelebrationSceneExit({
    celebrate,
    sceneLeavesWithTheWindow: showEmpty && hasEarlierItems && earlierExpanded,
    beginSceneExit,
  });
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
    earlierHandoff,
    celebrate,
    sceneHeldForExit,
  });
  // What a tap on Earlier's header does to the SLOT — the argument
  // `useEarlierExpandHandoff` sequences on, derived here from the same three
  // signals the block above reads so the two answers cannot drift. See its own
  // doc comment for why one boolean covers both directions of the swap.
  const slotChangesHands = earlierSlotChangesHands({
    showEmpty,
    hasEarlierItems,
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
    earlierSlotChangesHands: slotChangesHands,
    showTodayEarlierSection,
  };
}

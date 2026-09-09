import { useCelebrateEmptyTransition } from "@/hooks/use-celebrate-empty-transition";
import { taskJustCompleted } from "@/lib/task-completion-signal";
import { useEarlierExpandHandoff } from "@/features/todayTodos/lib/useEarlierExpandHandoff";
import {
  TODAY_EARLIER_EXIT_MS,
  shouldShowTodayEmptyIllustration,
} from "@/features/todayTodos/lib/todayEarlierIllustration";

/**
 * `ListContainer`'s empty/celebration/Earlier-hand-off derivation — Earlier
 * empty-state parity with Today/All/Priority/Scheduled (see
 * `shouldShowTodayEmptyIllustration`'s own doc comment for the full
 * requirements-1-3 rationale). Owns the Earlier expand/collapse state machine
 * (`useEarlierExpandHandoff`) since nothing outside this hook needs to read
 * it directly — the toggle callback it returns is wired straight into
 * `TimelineSections`' `onToggleEarlier`.
 *
 * `hasNonEarlierListTodos` (search-reactive) and `hasNonEarlierRawListTodos`
 * (search-immune) come in as two distinct booleans from
 * `useListEarlierSection` — see that hook's own doc comment. This hook is
 * exactly where their separation matters: `showEmpty` legitimately reads the
 * search-reactive one, while `remoteEmptied` MUST read only the raw one.
 * Never fold the two together here.
 */
export function useListEmptyState({
  listTodosLoading,
  isSearching,
  hasEarlierItems,
  hasNonEarlierListTodos,
  hasNonEarlierRawListTodos,
}: {
  listTodosLoading: boolean;
  isSearching: boolean;
  hasEarlierItems: boolean;
  /** Search-reactive: from `filteredTodos`. Feeds only `showEmpty` below. */
  hasNonEarlierListTodos: boolean;
  /** Search-immune: from the raw `listTodos`. Feeds only `remoteEmptied` below. */
  hasNonEarlierRawListTodos: boolean;
}) {
  const {
    expanded: earlierExpanded,
    handoffPending: earlierHandoffPending,
    toggle: toggleEarlierExpanded,
  } = useEarlierExpandHandoff(TODAY_EARLIER_EXIT_MS);

  // Remote sibling of `taskJustCompleted()` below — fires for a completion on
  // another device or by a collaborator, not just this tab's own tap.
  // Requirement 4: watches the non-Earlier count, so finishing every current
  // task still celebrates however many overdue tasks Earlier still holds.
  // Fed the search-immune signal, never `hasNonEarlierListTodos` — see this
  // module's own doc comment.
  const remoteEmptied = useCelebrateEmptyTransition(!hasNonEarlierRawListTodos);

  // Requirement 1, generalized from Today: zero non-Earlier tasks, not
  // loading, not mid-search — a list with only overdue tasks left still
  // earns the "all done" illustration, same as All/Priority/Scheduled.
  const showEmpty = !listTodosLoading && !isSearching && !hasNonEarlierListTodos;
  // Finishing the list is a payoff, not an absence: the confetti is for the
  // tick that emptied it, not for a list that was already empty. Hoisted so
  // the illustration/Earlier hand-off below reads the exact same signal — see
  // `shouldShowTodayEmptyIllustration`.
  const celebrate = taskJustCompleted() || remoteEmptied;
  // Requirements 1-3: who owns the empty-state slot once `showEmpty` is
  // true — the exact function Today/All/Priority/Scheduled call, reused
  // rather than a parallel List-only decision.
  const showEmptyIllustration = shouldShowTodayEmptyIllustration({
    showEmpty,
    hasEarlierItems,
    earlierExpanded,
    earlierHandoffPending,
    celebrate,
  });

  return {
    earlierExpanded,
    earlierHandoffPending,
    toggleEarlierExpanded,
    celebrate,
    showEmptyIllustration,
  };
}

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
  useCelebrationSceneExit,
  useEarlierExpandHandoff,
} from "@/features/todayTodos/lib/useEarlierExpandHandoff";
import {
  OVERDUE_ROWS_FADE_MS,
  TODAY_EARLIER_EXIT_MS,
  earlierSlotChangesHands,
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
  pendingRowCount,
}: {
  listTodosLoading: boolean;
  isSearching: boolean;
  hasEarlierItems: boolean;
  /** Search-reactive: from `filteredTodos`. Feeds only `showEmpty` below. */
  hasNonEarlierListTodos: boolean;
  /** Search-immune: from the raw `listTodos`. Feeds only `remoteEmptiedAt` below. */
  hasNonEarlierRawListTodos: boolean;
  /**
   * The RAW, search-immune count of rows this list holds, EVERY bucket included
   * — Earlier's overdue rows with the rest, so `listTodos.length` and not either
   * of the two Earlier-excluding booleans above. The cancel's input and nothing
   * else's: see `useArrivalCancel`.
   */
  pendingRowCount: number;
}) {
  // Before anything is derived: a row landing on this list ends the
  // celebration, whether it came back from an undo, from a collaborator or from
  // the user's own typing. Counted, never inferred from the emptiness below —
  // an undone OVERDUE row moves none of it.
  useArrivalCancel(pendingRowCount);
  const {
    expanded: earlierExpanded,
    handoff: earlierHandoff,
    toggle: toggleEarlierExpanded,
    beginSceneExit,
  } = useEarlierExpandHandoff(TODAY_EARLIER_EXIT_MS, OVERDUE_ROWS_FADE_MS);

  // Remote sibling of `taskJustCompleted()` below — fires for a completion on
  // another device or by a collaborator, not just this tab's own tap.
  // Requirement 4: watches the non-Earlier count, so finishing every current
  // task still celebrates however many overdue tasks Earlier still holds.
  // Fed the search-immune signal, never `hasNonEarlierListTodos` — see this
  // module's own doc comment. The CANCEL above deliberately watches a different
  // number again; see `useArrivalCancel`.
  const remoteEmptiedAt = useEmptyTransitionOpenedAt(!hasNonEarlierRawListTodos);

  // Requirement 1, generalized from Today: zero non-Earlier tasks, not
  // loading, not mid-search — a list with only overdue tasks left still
  // earns the "all done" illustration, same as All/Priority/Scheduled.
  const showEmpty = !listTodosLoading && !isSearching && !hasNonEarlierListTodos;
  // Finishing the list is a payoff, not an absence: the confetti is for the
  // tick that emptied it, not for a list that was already empty. Hoisted so
  // the illustration/Earlier hand-off below reads the exact same signal — see
  // `shouldShowTodayEmptyIllustration`.
  //
  // Both windows consult the cancel, because an end that only one of them
  // observes is not an end — the same argument `useCelebrationWindowExpiry`
  // makes about the clock. `shouldCelebrateEmptyState` is where the two stamps
  // and the cancel meet, and the scoped screens call it with the same three
  // arguments rather than restating the rule.
  const completionOpenedAt = useCompletionOpenedAt();
  const celebrate = shouldCelebrateEmptyState({
    completionOpenedAtMs: completionOpenedAt,
    remoteEmptiedAtMs: remoteEmptiedAt,
    cancelledAtMs: celebrationCancelledAtMs(),
  });
  // The window has an end now, so the scene it holds gets to leave over one —
  // the same derivation the scoped screens make; see `useCelebrationSceneExit`.
  const sceneHeldForExit = useCelebrationSceneExit({
    celebrate,
    sceneLeavesWithTheWindow: showEmpty && hasEarlierItems && earlierExpanded,
    beginSceneExit,
  });
  // Requirements 1-3: who owns the empty-state slot once `showEmpty` is
  // true — the exact function Today/All/Priority/Scheduled call, reused
  // rather than a parallel List-only decision.
  const showEmptyIllustration = shouldShowTodayEmptyIllustration({
    showEmpty,
    hasEarlierItems,
    earlierExpanded,
    earlierHandoff,
    celebrate,
    sceneHeldForExit,
  });

  // What a tap on Earlier's header does to the SLOT — the same derivation
  // `useTimelineEmptyState` makes for the scoped screens, reused rather than
  // restated; see its own doc comment for why one boolean covers both
  // directions of the swap.
  const slotChangesHands = earlierSlotChangesHands({
    showEmpty,
    hasEarlierItems,
    celebrate,
  });

  return {
    showEmpty,
    earlierExpanded,
    earlierHandoff,
    earlierSlotChangesHands: slotChangesHands,
    toggleEarlierExpanded,
    celebrate,
    showEmptyIllustration,
  };
}

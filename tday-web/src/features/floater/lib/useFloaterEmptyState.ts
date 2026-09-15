import {
  useArrivalCancel,
  useEmptyTransitionOpenedAt,
} from "@/hooks/use-celebrate-empty-transition";
import { useFadeUnmount } from "@/hooks/useFadeUnmount";
import {
  celebrationCancelledAtMs,
  shouldCelebrateEmptyState,
  useCompletionOpenedAt,
} from "@/lib/task-completion-signal";
import { DURATION_MS } from "@/lib/motion";
import { emptySceneLeavesOnCancel } from "@/features/todayTodos/lib/todayEarlierIllustration";

/**
 * The Anytime feeds' empty/celebration derivation — `useListEmptyState`'s
 * sibling for the two screens that have no Earlier bucket at all.
 *
 * One hook for both of them (`NativeFloaterTaskHomeDashboard`, the Anytime tab's
 * own feed, and `FloaterListContainer`, one Anytime list) because they make the
 * identical decision from the identical inputs, and the bug this exists to close
 * is what happens when two screens make "the same" decision separately. Both
 * read `celebrate={taskJustCompleted() || remoteEmptied}` until this landed:
 * two windows OR'd together with nothing observing the opposite transition, so
 * an undo put the row back and the paper went on flying over it for the rest of
 * the four seconds. The cancel stamp the completion and create mutations on
 * these very feeds were already writing (`complete-floater.ts`,
 * `create-floater.ts`) was read by nobody here.
 *
 * WHAT IS SIMPLER HERE THAN ON THE SCOPED SCREENS, said out loud so the next
 * hand does not go looking for the missing half. There is no Earlier/overdue
 * bucket on an Anytime feed — an Anytime task has no date, so it can never be
 * overdue — which means the reported bug's sharp case (a restored OVERDUE row
 * leaving the emptiness predicate untouched) cannot arise. Every restored row
 * here makes the feed non-empty, so the scene always leaves and always has to
 * leave over the burst's own fade. That is why `showEmpty` is handed to
 * `emptySceneLeavesOnCancel` twice: on the scoped screens "is the scope
 * finished" and "does the scene own this slot" are genuinely two questions, and
 * here they are one. Passing the same value twice is the honest spelling of
 * that; collapsing the parameter would make the shared function worse for the
 * caller that needs both.
 *
 * The COUNT is still what the cancel watches, not the emptiness, and it is still
 * the raw search-immune one. A search that hides every row must never read as a
 * finished feed, and a row arriving while a query stands must still end a
 * celebration the query is merely hiding the end of — see `useArrivalCancel`.
 */
export function useFloaterEmptyState({
  isLoading,
  isSearching,
  pendingRowCount,
}: {
  isLoading: boolean;
  /** A query is standing. Feeds only `showEmpty`, never either window below. */
  isSearching: boolean;
  /**
   * The RAW, search-immune count of pending (not completed) rows this feed
   * holds. The cancel's input, and the emptiness both windows are armed from.
   */
  pendingRowCount: number;
}) {
  // Before anything is derived: a row landing on this feed ends the celebration,
  // whether it came back from an undo, from a collaborator or from the user's
  // own typing. The backstop half — the local mutations stamp the cancel
  // themselves, because they restore the row through an invalidate and a
  // refetch and the cancel must not wait on a network round trip.
  useArrivalCancel(pendingRowCount);

  const isEmpty = pendingRowCount === 0;
  // Remote sibling of the completion window below — fires for a completion on
  // another device or by a collaborator, not just this tab's own tap. Fed the
  // raw count, never the filtered one: typing a query is not a collaborator
  // emptying the list.
  const remoteEmptiedAt = useEmptyTransitionOpenedAt(isEmpty);
  const completionOpenedAt = useCompletionOpenedAt();
  // Finishing a feed is a payoff, not an absence: the confetti is for the tick
  // that emptied it, not for a feed that was already empty. Both windows consult
  // the cancel, because an end only one of them observes is not an end — see
  // `shouldCelebrateEmptyState`, which is where the three stamps meet for every
  // screen rather than each screen restating the rule.
  const celebrate = shouldCelebrateEmptyState({
    completionOpenedAtMs: completionOpenedAt,
    remoteEmptiedAtMs: remoteEmptiedAt,
    cancelledAtMs: celebrationCancelledAtMs(),
  });

  const showEmpty = !isLoading && !isSearching && isEmpty;
  // The burst leaves over a fade rather than between two frames, and the scene
  // it flies inside is leaving in that same frame — the scene's mount guard here
  // IS the emptiness test, so the undo would otherwise take the canvas out from
  // under its own envelope. Held in the tree for exactly that fade.
  // `useFadeUnmount` already returns false outright under reduced motion, where
  // nothing was painted and no wait may survive in front of the restored row.
  const sceneStillMounted = useFadeUnmount(showEmpty, DURATION_MS.quick);
  const sceneLeavingOnCancel = emptySceneLeavesOnCancel({
    sceneStillMounted,
    showEmptyIllustration: showEmpty,
    showEmpty,
  });

  return { showEmpty, celebrate, sceneLeavingOnCancel };
}

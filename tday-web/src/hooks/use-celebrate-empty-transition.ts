import { useRef } from "react";
import {
  CELEBRATION_WINDOW_MS,
  markCelebrationCancelled,
  useCelebrationWindowExpiry,
  wasDeletedLocallyJustNow,
} from "@/lib/task-completion-signal";

/**
 * Remote sibling of `markTaskCompleted`/`taskJustCompleted`
 * (`@/lib/task-completion-signal`): whether the list a container is
 * rendering just transitioned from having tasks to having none, however that
 * happened.
 *
 * `taskJustCompleted` is precise because only a completion mutation ever
 * calls `markTaskCompleted`. This can't be as precise: a container's `todos`
 * prop updates identically whether a task was completed on this tab,
 * completed on another device, completed by a collaborator on a shared list,
 * deleted, or moved to a different list — nothing in the refreshed list says
 * which. So instead of gating on *why* the count changed, this watches
 * *whether* it did, the same technique `TodoListViewModel.hydrateFromCache`
 * uses on iOS and Android: a plain previous-value-vs-current-value
 * comparison.
 *
 * That would make deleting (or moving away) the last task celebrate too —
 * wrong, since every platform treats that as a different ending from
 * completing it. The `wasDeletedLocallyJustNow` check below closes that for
 * those mutations run from *this tab* (see `markTaskDeletedLocally`'s own
 * doc comment for exactly which ones), the same way `taskJustCompleted` is
 * already closed to them by never being set outside a complete mutation. A
 * *remote* delete or move-away of the last task still slips through as a
 * false celebration: this tab has no marker for a mutation that happened on
 * someone else's — a generic "the cache changed" signal carries no reason.
 * This is not a web-only gap: iOS's `remoteEmptiedAt` and Android's
 * `remoteEmptiedAtMs` are set from the exact same kind of reason-free
 * "the cache changed, re-read it" signal (see
 * `TodoListViewModel.hydrateFromExternalCacheChange` on both), so a *remote*
 * delete of the last task false-celebrates there too — their own doc
 * comments say so. Closing it fully on any platform would need the server to
 * say *why* a list changed, not just *that* it did.
 *
 * The comparison runs during render, not in an effect — the same render that
 * discovers `isEmpty` just turned `true` already has the answer, which is
 * what lets the very `<EmptyState celebrate>` render that mounts on this
 * transition already receive `true` instead of catching up a tick later.
 * Mutating a ref conditionally during render like this is a standard React
 * pattern for remembering a previous prop to compare against (see "Adjusting
 * state when a prop changes" in the React docs) — it does not schedule a
 * render itself, it just piggybacks on the render `isEmpty` already caused.
 *
 * Component-local by construction (a `useRef`, not module state, unlike
 * `task-completion-signal.ts`): the previous value starts fresh every time a
 * container mounts, so a list that emptied while its screen was not mounted
 * does not hand back a stale celebration when the user opens it again.
 *
 * Hands back WHEN the window opened, or 0 while it is shut, rather than a plain
 * boolean: `celebrate` on the scope and list screens is the OR of this window
 * and the completion one, and a cancel has to be compared against whichever of
 * the two opened last (`shouldCelebrateEmptyState`). `useCelebrateEmptyTransition`
 * below is the boolean, for the callers that never make that comparison.
 *
 * Nothing re-arms this behind a cancel, which is why there is no counterpart
 * here to the native clients' "clear `remoteEmptiedAt` on an arrival": the
 * stamp is written on an EDGE (`wasEmptyRef` flipping) and not held as a flag,
 * so an undo that leaves `isEmpty` true — the overdue case — writes no new
 * stamp to be cancelled a second time, and one that flips it false has to flip
 * it back through a fresh completion to write one at all.
 */
export function useEmptyTransitionOpenedAt(
  isEmpty: boolean,
  windowMs = CELEBRATION_WINDOW_MS,
): number {
  const wasEmptyRef = useRef(isEmpty);
  const emptiedAtRef = useRef(0);

  if (wasEmptyRef.current !== isEmpty) {
    if (!wasEmptyRef.current && isEmpty && !wasDeletedLocallyJustNow()) {
      emptiedAtRef.current = Date.now();
    }
    wasEmptyRef.current = isEmpty;
  }

  // The other half of the same window as `useTaskJustCompleted`'s, and it needs
  // the same clock for the same reason: a comparison against `Date.now()` in a
  // render body ends when somebody else happens to render, which is not an
  // ending anybody designed. Handed the ref's current value rather than a piece
  // of state — the assignment above has already happened by the time the effect
  // inside reads its argument.
  useCelebrationWindowExpiry(emptiedAtRef.current, windowMs);

  const open = emptiedAtRef.current !== 0 && Date.now() - emptiedAtRef.current < windowMs;
  return open ? emptiedAtRef.current : 0;
}

/**
 * The same window as a plain boolean, for the two floater feeds — the callers
 * that hand `celebrate` straight to a scene they were drawing anyway and never
 * have to compare this opening against another one.
 *
 * The scope and list screens take the stamp instead: `celebrate` there is the
 * OR of this window and the completion window, and the cancel has to be
 * compared against whichever of them opened last. See
 * `shouldCelebrateEmptyState` (`@/lib/task-completion-signal`).
 */
export function useCelebrateEmptyTransition(
  isEmpty: boolean,
  windowMs = CELEBRATION_WINDOW_MS,
): boolean {
  return useEmptyTransitionOpenedAt(isEmpty, windowMs) !== 0;
}

/**
 * The other direction of the same comparison, and the half the celebration
 * never had: a pending row ARRIVING on this screen ends the celebration,
 * however it got here.
 *
 * COUNTS, never emptiness, and that distinction is the entire reported bug. The
 * screen's "this scope is finished" predicate deliberately excludes the
 * Earlier/overdue bucket — `hasNonEarlierListTodos`, `timelineHasCurrentItems`,
 * Android's `nonEarlierSectionsEmpty`, iOS's `hasNoPendingItems` — because
 * finishing today's work while overdue tasks wait still earns the payoff
 * (requirement 4). So undoing the completion of an OVERDUE task puts a row back
 * on screen and moves that predicate not at all: nothing transitions, `showEmpty`
 * stays true, and the paper goes on flying over a row the user can see. The
 * mirror-image fix — watching for empty→non-empty the way
 * `useEmptyTransitionOpenedAt` above watches for non-empty→empty — misses
 * exactly that case, because the count is non-zero on both sides of it. Only
 * the COUNT moves, so only the count is watched, and it is fed the RAW,
 * search-immune, every-bucket total rather than anything the emptiness
 * predicate reads.
 *
 * The backstop half of a two-part detection. The local paths stamp
 * `markCelebrationCancelled` themselves — the completion-undo closures and the
 * create mutations — because those restore the row through an invalidate and a
 * refetch, and the cancel must not wait on a network round trip. This catches
 * everything else: a collaborator's add, a collaborator's UNDO, another device,
 * a sync, and any future arrival path whose author forgets the first half. The
 * two overlap on purpose; `markCelebrationCancelled` is idempotent in the only
 * way that matters, since a second stamp inside the same window changes no
 * answer.
 *
 * Compared during render rather than in an effect, for the reason
 * `useEmptyTransitionOpenedAt` gives above: the render that discovers the row
 * is back is the render that has to stop celebrating, and an effect is one
 * commit too late — one commit being exactly long enough to draw the frame this
 * exists to prevent. Writing module state from a render body is the one thing
 * here worth a second look, and it is safe on the same terms as that hook's own
 * ref write: it schedules nothing, React may discard the render, and a
 * discarded render's stamp is at worst a cancel that was going to be written
 * again a moment later anyway.
 *
 * Component-local previous value (a `useRef`, not module state) so a screen
 * that grew while it was unmounted does not cancel a celebration the user
 * earns the instant they open it.
 */
export function useArrivalCancel(pendingCount: number): void {
  const previousCountRef = useRef(pendingCount);

  if (pendingCount > previousCountRef.current) markCelebrationCancelled();
  previousCountRef.current = pendingCount;
}

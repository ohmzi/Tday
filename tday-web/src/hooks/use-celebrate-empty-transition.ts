import { useRef } from "react";
import { wasDeletedLocallyJustNow } from "@/lib/task-completion-signal";

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
 */
export function useCelebrateEmptyTransition(isEmpty: boolean, windowMs = 4000): boolean {
  const wasEmptyRef = useRef(isEmpty);
  const emptiedAtRef = useRef(0);

  if (wasEmptyRef.current !== isEmpty) {
    if (!wasEmptyRef.current && isEmpty && !wasDeletedLocallyJustNow()) {
      emptiedAtRef.current = Date.now();
    }
    wasEmptyRef.current = isEmpty;
  }

  return emptiedAtRef.current !== 0 && Date.now() - emptiedAtRef.current < windowMs;
}

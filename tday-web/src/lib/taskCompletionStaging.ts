import { useLayoutEffect, useSyncExternalStore } from "react";
import { prefersReducedMotion } from "@/lib/prefersReducedMotion";
import {
  TASK_COMPLETION_CHECK_TO_STRIKE_MS,
  TASK_COMPLETION_COLLAPSE_MS,
  TASK_COMPLETION_FADE_MS,
  TASK_COMPLETION_STRIKE_TO_FADE_MS,
  TASK_COMPLETION_TOTAL_MS,
} from "@/lib/taskCompletionTiming";

/**
 * How long a committed hold waits for its row to leave the DOM before releasing anyway.
 *
 * A row can survive the commit: a screen whose cache the prune does not touch keeps it mounted.
 * Without a bound that row would sit at the removing frame, invisible, for as long as it stayed
 * mounted. The bound is the exit itself plus one fade of margin. By then the row has finished its
 * collapse and React Query has had every chance to remove it, so a row still there is one that
 * genuinely belongs on screen, and releasing it returns it to the list. Built from the exit rungs
 * rather than a new number, so it moves with the sequence it guards.
 */
const COMMITTED_HOLD_FALLBACK_MS = TASK_COMPLETION_COLLAPSE_MS + TASK_COMPLETION_FADE_MS;

/**
 * The in-flight half of checking a task off, held outside every component that could unmount.
 *
 * Ticking a task acknowledges it instantly and commits it just under a second later, at the end
 * of the staged check-off sequence (see `taskCompletionTiming.ts`). The row is not entitled to
 * that window: a filter flips, a section collapses, a parent re-keys its children, the user
 * navigates — any of those unmounts it mid-sequence. While the phase and its timers lived in the row's own
 * `useState` + `useRef<number[]>`, React's unmount cleanup cleared them, and with the last timer
 * went the commit: nothing pruned, no toast, no request. The user ticked a task, watched it go
 * green, and got it back. That is data loss, not a dropped frame.
 *
 * The fix is the same one iOS took (`TodoListScreen.completionPhases` plus a detached `Task`):
 * stage the completion somewhere that outlives the row, and let the row *read* the phase rather
 * than own it. On web that is this module — a subscribable signal in the same family as
 * `task-completion-signal.ts` and `bulk/bulk-selection-signal.ts`. A completion is keyed by task
 * id, so a row that unmounts and comes back finds its own sequence still running at the right
 * point instead of a clean slate, and the guard against a second tap survives the round trip too.
 *
 * The commit callback is captured when the sequence is staged, not when it fires. Everything it
 * closes over — the query client, the toast, the mutation — belongs to the app, not to the row,
 * so it is still perfectly valid to call after the row has gone.
 */
export type TaskCompletionPhase = "checked" | "struck" | "removing";

type StagedCompletion = {
  phase: TaskCompletionPhase;
  timers: number[];
  /**
   * Set once the commit has pruned the row out of the caches while a row was still mounted for
   * it. The entry then stays `removing` until that row unmounts or the fallback fires — see
   * `retireIfCommitted`.
   */
  committed: boolean;
  /** The fallback release armed by a committed hold, so a clean unmount can cancel it. */
  fallbackTimer?: number;
};

const staged = new Map<string, StagedCompletion>();
/** How many mounted rows are subscribed to each task id. */
const mountedRows = new Map<string, number>();
const listeners = new Set<() => void>();

function emit(): void {
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function isRowMounted(id: string): boolean {
  return (mountedRows.get(id) ?? 0) > 0;
}

/**
 * Drops a committed entry once no row is showing it any more.
 *
 * This is what lets the removing frame outlive the commit. Writing the prune and clearing the
 * phase in the same tick exposed the row, for one render, at full height and full ink: the
 * commit's cache write has not reached React yet, so the row re-read "no phase", snapped back to
 * its open box, pushed the rows below down, and only then was pruned — the jump the staged
 * sequence exists to remove. So the entry is held until the row that was collapsing has actually
 * left the DOM, and the unmount is the event that retires it. Undo can only arrive after that
 * (it is raised by the same commit, five seconds on), so an undone row remounts with no entry and
 * comes back untouched.
 */
function retireIfCommitted(id: string): void {
  const entry = staged.get(id);
  if (!entry?.committed || isRowMounted(id)) return;
  release(id, entry);
}

/**
 * Ends a committed sequence: drops the entry, cancels its fallback, and tells subscribers.
 *
 * Guarded on identity so a fallback or unmount that arrives after the entry was already released
 * (and possibly re-staged for the same id) cannot tear down a newer sequence.
 */
function release(id: string, entry: StagedCompletion): void {
  if (staged.get(id) !== entry) return;
  if (entry.fallbackTimer !== undefined) window.clearTimeout(entry.fallbackTimer);
  staged.delete(id);
  emit();
}

function advanceTo(id: string, phase: TaskCompletionPhase): void {
  const entry = staged.get(id);
  // Gone already — the sequence committed, or was never staged on this id.
  if (!entry) return;
  entry.phase = phase;
  emit();
}

/** How far through the check-off sequence this task is, or null if it is not being completed. */
export function getTaskCompletionPhase(id: string): TaskCompletionPhase | null {
  return staged.get(id)?.phase ?? null;
}

/**
 * Starts the check-off sequence for one task and schedules its commit.
 *
 * A second call for a task already in flight is ignored — that is the double-tap guard, and it has
 * to live here rather than in the row for the same reason the timers do.
 */
export function stageTaskCompletion(id: string, commit: () => void): void {
  if (staged.has(id)) return;

  const removeAt = TASK_COMPLETION_CHECK_TO_STRIKE_MS + TASK_COMPLETION_STRIKE_TO_FADE_MS;
  // The last leg of the sequence is the collapse, and with reduce-motion on there is no collapse:
  // the row is handed its finished frame — no ink, no box — the instant the phase flips. Waiting
  // out a trip nobody is taking is `docs/motion.md`'s fifth idiom rule broken from the other side,
  // and the reader made to sit through the pause would be the one who asked for less motion, not
  // more. So the commit follows the frame instead of the animation that is switched off.
  //
  // Read here rather than subscribed, because this is the moment the timers arm and a `setTimeout`
  // cannot change its mind afterwards. A preference that flips mid-sequence is the row's problem —
  // it has the hook, and drawing the finished frame is all that is left to get right by then.
  const commitAt = prefersReducedMotion() ? removeAt : TASK_COMPLETION_TOTAL_MS;

  const entry: StagedCompletion = { phase: "checked", timers: [], committed: false };
  staged.set(id, entry);
  entry.timers.push(
    window.setTimeout(() => advanceTo(id, "struck"), TASK_COMPLETION_CHECK_TO_STRIKE_MS),
    window.setTimeout(() => advanceTo(id, "removing"), removeAt),
    window.setTimeout(() => {
      // The commit prunes the row and raises the undo toast; Undo refetches it back in, so the
      // entry must not outlive a successful commit into a state where that row comes back still
      // ticked and struck. Whether it can be dropped here or has to wait for the row to unmount is
      // decided below, once the commit has run.
      let committed = false;
      try {
        commit();
        committed = true;
      } finally {
        if (committed && isRowMounted(id)) {
          // The row is still on screen holding its removing frame while React Query drops it.
          // If it survives the commit, the fallback below releases it once the exit has run.
          entry.committed = true;
          entry.fallbackTimer = window.setTimeout(
            () => release(id, entry),
            COMMITTED_HOLD_FALLBACK_MS,
          );
        } else {
          staged.delete(id);
        }
        // Subscribers hear about the end of the sequence even if the commit threw. Skipping the
        // notification would leave any row still on screen rendering the last phase it was told
        // about — faded to nothing, with no way back.
        emit();
      }
    }, commitAt),
  );
  emit();
}

/**
 * Subscribes a row to its own task's check-off phase, and registers the row as holding it.
 *
 * The registration is what `retireIfCommitted` waits on: a committed sequence is only released
 * once the last row showing it has unmounted.
 */
export function useTaskCompletionPhase(id: string): TaskCompletionPhase | null {
  // Layout, not passive: registration has to be in place before the browser paints the row and
  // before any commit can run against it. A passive effect could let a commit land in the gap
  // between paint and effect, find no mounted row, release the hold, and flash the row back.
  useLayoutEffect(() => {
    mountedRows.set(id, (mountedRows.get(id) ?? 0) + 1);
    return () => {
      const remaining = (mountedRows.get(id) ?? 1) - 1;
      if (remaining > 0) mountedRows.set(id, remaining);
      else mountedRows.delete(id);
      retireIfCommitted(id);
    };
  }, [id]);
  const read = () => getTaskCompletionPhase(id);
  return useSyncExternalStore(subscribe, read, read);
}

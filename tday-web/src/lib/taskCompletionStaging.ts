import { useSyncExternalStore } from "react";
import {
  TASK_COMPLETION_CHECK_TO_STRIKE_MS,
  TASK_COMPLETION_STRIKE_TO_FADE_MS,
  TASK_COMPLETION_TOTAL_MS,
} from "@/lib/taskCompletionTiming";

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
};

const staged = new Map<string, StagedCompletion>();
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

  const entry: StagedCompletion = { phase: "checked", timers: [] };
  staged.set(id, entry);
  entry.timers.push(
    window.setTimeout(() => advanceTo(id, "struck"), TASK_COMPLETION_CHECK_TO_STRIKE_MS),
    window.setTimeout(
      () => advanceTo(id, "removing"),
      TASK_COMPLETION_CHECK_TO_STRIKE_MS + TASK_COMPLETION_STRIKE_TO_FADE_MS,
    ),
    window.setTimeout(() => {
      // The entry goes before the commit runs, so the sequence cannot outlive itself. It matters
      // for Undo: committing stages the row out of the caches and raises the undo toast, and Undo
      // refetches the row back in. It has to arrive looking untouched — a leftover phase would
      // put it back still ticked and struck, which is the opposite of what Undo promised.
      staged.delete(id);
      try {
        commit();
      } finally {
        // Subscribers hear about the end of the sequence even if the commit threw. Skipping the
        // notification would leave any row still on screen rendering the last phase it was told
        // about — faded to nothing, with no way back.
        emit();
      }
    }, TASK_COMPLETION_TOTAL_MS),
  );
  emit();
}

/** Subscribes a row to its own task's check-off phase. */
export function useTaskCompletionPhase(id: string): TaskCompletionPhase | null {
  const read = () => getTaskCompletionPhase(id);
  return useSyncExternalStore(subscribe, read, read);
}

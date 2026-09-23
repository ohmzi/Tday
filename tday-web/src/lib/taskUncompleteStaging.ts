import { useSyncExternalStore } from "react";
import { prefersReducedMotion } from "@/lib/prefersReducedMotion";
import {
  TASK_COMPLETION_CHECK_TO_STRIKE_MS,
  TASK_COMPLETION_STRIKE_TO_FADE_MS,
  TASK_COMPLETION_TOTAL_MS,
} from "@/lib/taskCompletionTiming";

/**
 * The in-flight half of restoring a completed task, held outside every component that could
 * unmount — the un-complete twin of `taskCompletionStaging.ts`. Read that module's doc comment
 * first; this one only spells out what is different in the reverse direction.
 *
 * Restoring plays the check-off sequence backwards — unchecked (circle empties) → unstruck (the
 * rule lifts) → removing (ink out, box shut) — and only then sends the PATCH that actually
 * un-completes the task, same as completing waits to send its own. The two Completed-history rows
 * that play this sequence have exactly the same unmount exposure the scheduled row used to have:
 * the history screen filters by a live search query, and a query typed mid-restore can drop a row
 * out of the filtered list before its own local timers were due to fire. While the phase and its
 * timers lived in the row's own `useState` + `useRef<number[]>`, that unmount cleared them, and
 * with the last timer went the commit: the user watched the circle empty and the strike lift, the
 * row vanished from the search results, and the task was never actually un-completed — it just
 * came back the next time the history refetched.
 *
 * Same fix as the forward direction: the phase lives in a module-level `Map` keyed by task id, a
 * row reads it through `useTaskUncompletePhase` (a `useSyncExternalStore` subscription) instead of
 * owning it, and a restore is started with `stageTaskUncompletion`. A row that unmounts and
 * remounts (the search query clears again, say) finds its own sequence still running at the right
 * point instead of a clean slate.
 */
export type TaskUncompletePhase = "unchecked" | "unstruck" | "removing";

type StagedUncompletion = {
  phase: TaskUncompletePhase;
  timers: number[];
};

const staged = new Map<string, StagedUncompletion>();
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

function advanceTo(id: string, phase: TaskUncompletePhase): void {
  const entry = staged.get(id);
  // Gone already — the sequence committed, or was never staged on this id.
  if (!entry) return;
  entry.phase = phase;
  emit();
}

/** How far through the restore sequence this task is, or null if it is not being restored. */
export function getTaskUncompletePhase(id: string): TaskUncompletePhase | null {
  return staged.get(id)?.phase ?? null;
}

/**
 * Starts the restore sequence for one completed task and schedules its commit.
 *
 * A second call for a task already in flight is ignored — the double-tap guard, which has to
 * live here rather than in the row for the same reason the timers do.
 */
export function stageTaskUncompletion(id: string, commit: () => void): void {
  if (staged.has(id)) return;

  const removeAt = TASK_COMPLETION_CHECK_TO_STRIKE_MS + TASK_COMPLETION_STRIKE_TO_FADE_MS;
  // Same cut the forward direction makes, argued in full there: under reduce-motion there is no
  // collapse to wait out, so the commit follows the frame instead of the animation that is off.
  const commitAt = prefersReducedMotion() ? removeAt : TASK_COMPLETION_TOTAL_MS;

  const entry: StagedUncompletion = { phase: "unchecked", timers: [] };
  staged.set(id, entry);
  entry.timers.push(
    window.setTimeout(() => advanceTo(id, "unstruck"), TASK_COMPLETION_CHECK_TO_STRIKE_MS),
    window.setTimeout(() => advanceTo(id, "removing"), removeAt),
    window.setTimeout(() => {
      // The entry goes before the commit runs, for the same reason as the forward direction: a
      // row that remounts mid-flight while the commit is still pending must not find a leftover
      // phase sitting on a task the commit has not yet actually restored.
      staged.delete(id);
      try {
        commit();
      } finally {
        emit();
      }
    }, commitAt),
  );
  emit();
}

/** Subscribes a row to its own task's restore phase. */
export function useTaskUncompletePhase(id: string): TaskUncompletePhase | null {
  const read = () => getTaskUncompletePhase(id);
  return useSyncExternalStore(subscribe, read, read);
}

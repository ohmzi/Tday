import { useSyncExternalStore } from "react";

/**
 * Whether a task list is currently in selection mode.
 *
 * The selection itself lives in `TaskSelectionProvider`, screen-local, and the
 * screen is the only thing that needs it. But the bottom chrome the selection
 * bar replaces — `RootDock` and `TaskFloatingActionButton` — is mounted by
 * `NativeAppShell`, an ancestor of every screen, and the design note is explicit
 * that the two must never overlap. Threading a boolean up through the shell
 * would mean putting a provider above the router for one flag, so this is a
 * module-level signal in the same family as `task-completion-signal.ts`.
 *
 * Unlike that one it *is* subscribable: the shell has to re-render to take its
 * dock away, which is the whole point.
 */
let active = false;
const listeners = new Set<() => void>();

export function setBulkSelectionActive(next: boolean): void {
  if (active === next) return;
  active = next;
  listeners.forEach((listener) => listener());
}

export function getBulkSelectionActive(): boolean {
  return active;
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** True while a task list has selection mode open. */
export function useBulkSelectionActive(): boolean {
  return useSyncExternalStore(subscribe, getBulkSelectionActive, getBulkSelectionActive);
}

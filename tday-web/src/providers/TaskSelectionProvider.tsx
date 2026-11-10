import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import BulkSelectionBar from "@/components/todo/bulk/BulkSelectionBar";
import {
  BULK_MAX_SELECTION,
  isBulkSelectionAtCap,
} from "@/lib/bulk/bulk-selection-policy";
import { setBulkSelectionActive } from "@/lib/bulk/bulk-selection-signal";
import type { TodoItemType } from "@/types";

const EMPTY_SELECTION: ReadonlySet<string> = new Set<string>();

type TaskSelectionContextValue = {
  /** A provider is mounted and this screen offers selection at all. */
  available: boolean;
  selectionMode: boolean;
  /** The selected rows, in the screen's display order. */
  selectedRows: TodoItemType[];
  selectedCount: number;
  /** True once the cap is reached and further taps are refused. */
  atCap: boolean;
  /** Everything Select all could reach is already selected. */
  allSelected: boolean;
  isSelected: (rowId: string) => boolean;
  toggle: (rowId: string) => void;
  selectAll: () => void;
  deselectAll: () => void;
  enterSelection: () => void;
  exitSelection: () => void;
};

const INERT: TaskSelectionContextValue = {
  available: false,
  selectionMode: false,
  selectedRows: [],
  selectedCount: 0,
  atCap: false,
  allSelected: false,
  isSelected: () => false,
  toggle: () => {},
  selectAll: () => {},
  deselectAll: () => {},
  enterSelection: () => {},
  exitSelection: () => {},
};

const TaskSelectionContext = createContext<TaskSelectionContextValue>(INERT);

/**
 * Deliberately returns an inert value rather than throwing outside a provider.
 * `TodoItemCard` is shared with screens the first cut leaves out — the scheduled
 * home feed's Today preview, the calendar, completed history (§8) — and those
 * must go on behaving exactly as they do today rather than needing a provider
 * mounted for a mode they do not offer.
 */
export function useTaskSelection(): TaskSelectionContextValue {
  return useContext(TaskSelectionContext);
}

/**
 * Screen-local multi-select for a task list. See `docs/design/bulk-selection.md`
 * §2 for the model this implements; the parts worth restating here:
 *
 * - The set holds **row ids** (`${todoId}:${instanceEpochMs}`), but every action
 *   is handed the full row object — a request needs the canonical id and the
 *   occurrence's instance date, neither of which survives a bare id.
 * - `rows` is the screen's whole search- and scope-filtered result set, not the
 *   paged slice, so Select all means "everything this screen holds".
 * - Selection never outlives the screen: this unmounts on navigation, and the
 *   set is re-intersected with the visible rows on every change so a task that
 *   synced away drops out silently.
 */
export default function TaskSelectionProvider({
  rows,
  readOnly = false,
  scopeListId = null,
  children,
}: {
  rows: TodoItemType[];
  readOnly?: boolean;
  /**
   * The list this screen is scoped to, when it is a list screen. Its rows carry
   * `listID === null` (`ListTodoDto` has no such field), so without this a move
   * could not tell where they came from.
   */
  scopeListId?: string | null;
  children: React.ReactNode;
}) {
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedIds, setSelectedIds] =
    useState<ReadonlySet<string>>(EMPTY_SELECTION);

  const rowById = useMemo(
    () => new Map(rows.map((row) => [row.id, row])),
    [rows],
  );

  const exitSelection = useCallback(() => {
    setSelectionMode(false);
    setSelectedIds(EMPTY_SELECTION);
  }, []);

  const enterSelection = useCallback(() => {
    setSelectedIds(EMPTY_SELECTION);
    setSelectionMode(true);
  }, []);

  const toggle = useCallback((rowId: string) => {
    setSelectedIds((previous) => {
      const next = new Set(previous);
      if (next.has(rowId)) {
        next.delete(rowId);
        return next;
      }
      // At the cap an unselected row simply does not take. No toast, no error —
      // the bar already says why (§2.4).
      if (isBulkSelectionAtCap(next.size)) return previous;
      next.add(rowId);
      return next;
    });
  }, []);

  const selectAll = useCallback(() => {
    // In display order from the top, so which hundred you get is deterministic.
    setSelectedIds(
      new Set(rows.slice(0, BULK_MAX_SELECTION).map((row) => row.id)),
    );
  }, [rows]);

  const deselectAll = useCallback(() => {
    setSelectedIds(EMPTY_SELECTION);
  }, []);

  // A viewer on a shared list has no mutation affordances at all, so the mode
  // closes the moment the screen turns read-only.
  useEffect(() => {
    if (readOnly) exitSelection();
  }, [exitSelection, readOnly]);

  // Nothing left on screen to act on.
  useEffect(() => {
    if (selectionMode && rows.length === 0) exitSelection();
  }, [exitSelection, rows.length, selectionMode]);

  // Re-intersect with what is actually visible: a task completed on another
  // device, pruned by a sibling mutation, or filtered out by a search edit must
  // leave the selection silently. If that empties it, the mode has nothing left
  // and closes itself (§2.5).
  useEffect(() => {
    if (!selectionMode || selectedIds.size === 0) return;
    const survivors = new Set<string>();
    let dropped = false;
    selectedIds.forEach((rowId) => {
      if (rowById.has(rowId)) survivors.add(rowId);
      else dropped = true;
    });
    if (!dropped) return;
    if (survivors.size === 0) {
      exitSelection();
      return;
    }
    setSelectedIds(survivors);
  }, [exitSelection, rowById, selectedIds, selectionMode]);

  // Tell the app shell to stand its dock and FAB down while the selection bar
  // owns that slot.
  useEffect(() => {
    setBulkSelectionActive(selectionMode);
    return () => setBulkSelectionActive(false);
  }, [selectionMode]);

  useEffect(() => {
    if (!selectionMode) return;
    const onKeyDown = (event: KeyboardEvent) => {
      // The search bar handles Escape first when its field is open, and marks
      // the event handled; closing the search and the selection on one press
      // would be two undos for one keystroke.
      if (event.key !== "Escape" || event.defaultPrevented) return;
      exitSelection();
    };
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [exitSelection, selectionMode]);

  const selectedRows = useMemo(
    () => rows.filter((row) => selectedIds.has(row.id)),
    [rows, selectedIds],
  );

  const value = useMemo<TaskSelectionContextValue>(() => {
    const reachable = Math.min(rows.length, BULK_MAX_SELECTION);
    return {
      available: !readOnly && rows.length > 0,
      selectionMode,
      selectedRows,
      selectedCount: selectedRows.length,
      atCap: isBulkSelectionAtCap(selectedRows.length),
      allSelected: reachable > 0 && selectedRows.length >= reachable,
      isSelected: (rowId: string) => selectedIds.has(rowId),
      toggle,
      selectAll,
      deselectAll,
      enterSelection,
      exitSelection,
    };
  }, [
    deselectAll,
    enterSelection,
    exitSelection,
    readOnly,
    rows.length,
    selectAll,
    selectedIds,
    selectedRows,
    selectionMode,
    toggle,
  ]);

  return (
    <TaskSelectionContext.Provider value={value}>
      {children}
      <BulkSelectionBar scopeListId={scopeListId} />
    </TaskSelectionContext.Provider>
  );
}

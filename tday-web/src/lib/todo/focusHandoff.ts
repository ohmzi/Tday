import { useEffect, useRef } from "react";
import { useSearchParams } from "react-router-dom";
import {
  TODO_FOCUS_DATE_QUERY_PARAM,
  TODO_FOCUS_INSTANCE_QUERY_PARAM,
  TODO_FOCUS_MODE_QUERY_PARAM,
  TODO_FOCUS_TASK_QUERY_PARAM,
} from "@/lib/todoToastNavigation";

/**
 * How long a focused row keeps its focus params before they are cleared. A
 * timer, not a motion value: the highlight itself fades through its own
 * transition once the params are gone.
 */
export const FOCUS_PARAM_HOLD_MS = 1500;

const FOCUS_PARAM_KEYS = [
  TODO_FOCUS_TASK_QUERY_PARAM,
  TODO_FOCUS_INSTANCE_QUERY_PARAM,
  TODO_FOCUS_DATE_QUERY_PARAM,
  TODO_FOCUS_MODE_QUERY_PARAM,
];

/**
 * Clears the focus params after the destination has highlighted the row. The
 * history entry is replaced, so a reload or a refresh does not focus or scroll
 * again. Unmounting before the hold elapses cancels the clear.
 */
export function useFocusParamHandoff(focusTask: string | null) {
  const [, setSearchParams] = useSearchParams();

  useEffect(() => {
    if (!focusTask) return;
    const timer = window.setTimeout(() => {
      setSearchParams(
        (previous) => {
          const next = new URLSearchParams(previous);
          for (const key of FOCUS_PARAM_KEYS) next.delete(key);
          return next;
        },
        { replace: true },
      );
    }, FOCUS_PARAM_HOLD_MS);
    return () => window.clearTimeout(timer);
  }, [focusTask, setSearchParams]);
}

/**
 * Opens a collapsed Earlier bucket for the focused row, once per focused row.
 *
 * Refreshes hand the bucket a new array, and the focus param can still be in the
 * URL during the hold, so a plain effect would re-open a section the user had
 * just closed. The handled id is remembered until the focus is gone.
 */
export function useExpandEarlierForFocus({
  focusedRowId,
  earlierRowIds,
  setExpandedImmediately,
}: {
  focusedRowId: string | null;
  earlierRowIds: readonly string[];
  setExpandedImmediately: (expanded: boolean) => void;
}) {
  const handledRef = useRef<string | null>(null);

  useEffect(() => {
    if (!focusedRowId) {
      handledRef.current = null;
      return;
    }
    if (handledRef.current === focusedRowId) return;
    if (!earlierRowIds.includes(focusedRowId)) return;
    handledRef.current = focusedRowId;
    setExpandedImmediately(true);
  }, [earlierRowIds, focusedRowId, setExpandedImmediately]);
}

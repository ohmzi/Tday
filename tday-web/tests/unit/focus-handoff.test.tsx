// @vitest-environment jsdom

/**
 * Focus handoff for search results and deep links. Three rules are pinned here:
 * a recurring series lights up only the occurrence that matched, the focus params
 * are cleared once the destination has applied them, and a collapsed Earlier
 * bucket is opened once per focus, so a refresh never re-opens it.
 */

import type { ReactNode } from "react";
import { act, renderHook } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  FOCUS_PARAM_HOLD_MS,
  useExpandEarlierForFocus,
  useFocusParamHandoff,
} from "@/lib/todo/focusHandoff";
import { resolveFocusedRowId } from "@/lib/todo/todo-id";

const OCCURRENCE_A = "todo-r:1000";
const OCCURRENCE_B = "todo-r:2000";
const PLAIN = "todo-p:undefined";

describe("resolveFocusedRowId", () => {
  it("picks only the occurrence that matches the focused instance", () => {
    const rows = [OCCURRENCE_A, OCCURRENCE_B, PLAIN];

    expect(resolveFocusedRowId(rows, "todo-r", "2000")).toBe(OCCURRENCE_B);
    expect(resolveFocusedRowId(rows, "todo-r", "1000")).toBe(OCCURRENCE_A);
  });

  it("falls back to the first occurrence when the instance is not shown", () => {
    const rows = [OCCURRENCE_A, OCCURRENCE_B];

    expect(resolveFocusedRowId(rows, "todo-r", "9999")).toBe(OCCURRENCE_A);
    expect(resolveFocusedRowId(rows, "todo-r", null)).toBe(OCCURRENCE_A);
  });

  it("reads an occurrence suffix carried on the focus param itself", () => {
    expect(resolveFocusedRowId([OCCURRENCE_A, OCCURRENCE_B], "todo-r:2000", null)).toBe(
      OCCURRENCE_B,
    );
  });

  it("resolves a plain task and returns null when nothing matches", () => {
    expect(resolveFocusedRowId([PLAIN], "todo-p")).toBe(PLAIN);
    expect(resolveFocusedRowId([PLAIN], "missing")).toBeNull();
    expect(resolveFocusedRowId([PLAIN], null)).toBeNull();
  });
});

function renderWithLocation<T>(hook: () => T, initialEntry: string) {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter>
  );
  return renderHook(() => ({ value: hook(), search: useLocation().search }), { wrapper });
}

describe("useFocusParamHandoff", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("clears the focus params after the hold, and keeps unrelated params", () => {
    const { result } = renderWithLocation(
      () => useFocusParamHandoff("todo-r"),
      "/app/list/list-1?focusTask=todo-r&focusInstance=2000&focusDate=2026-08-21&other=kept",
    );

    expect(result.current.search).toContain("focusTask=todo-r");

    act(() => {
      vi.advanceTimersByTime(FOCUS_PARAM_HOLD_MS);
    });

    expect(result.current.search).not.toContain("focusTask");
    expect(result.current.search).not.toContain("focusInstance");
    expect(result.current.search).not.toContain("focusDate");
    expect(result.current.search).toContain("other=kept");
  });

  it("does not clear anything when there is no focus", () => {
    const { result } = renderWithLocation(
      () => useFocusParamHandoff(null),
      "/app/list/list-1?other=kept",
    );

    act(() => {
      vi.advanceTimersByTime(FOCUS_PARAM_HOLD_MS * 2);
    });

    expect(result.current.search).toBe("?other=kept");
  });
});

describe("useExpandEarlierForFocus", () => {
  it("opens Earlier once for a focused row, and not again when the list refreshes", () => {
    const setExpanded = vi.fn();
    const first = ["overdue-1", "overdue-2"];

    const { rerender } = renderHook(
      ({ rows }: { rows: string[] }) =>
        useExpandEarlierForFocus({
          focusedRowId: "overdue-1",
          earlierRowIds: rows,
          setExpandedImmediately: setExpanded,
        }),
      { initialProps: { rows: first } },
    );

    expect(setExpanded).toHaveBeenCalledTimes(1);
    expect(setExpanded).toHaveBeenCalledWith(true);

    // A refresh hands back a new array while the focus param is still in the URL.
    rerender({ rows: [...first] });
    rerender({ rows: [...first] });

    expect(setExpanded).toHaveBeenCalledTimes(1);
  });

  it("opens Earlier again for a new focus after the old one was cleared", () => {
    const setExpanded = vi.fn();
    const rows = ["overdue-1"];

    const { rerender } = renderHook(
      ({ focus }: { focus: string | null }) =>
        useExpandEarlierForFocus({
          focusedRowId: focus,
          earlierRowIds: rows,
          setExpandedImmediately: setExpanded,
        }),
      { initialProps: { focus: "overdue-1" as string | null } },
    );

    rerender({ focus: null });
    rerender({ focus: "overdue-1" });

    expect(setExpanded).toHaveBeenCalledTimes(2);
  });

  it("does nothing when the focused row is not in the Earlier bucket", () => {
    const setExpanded = vi.fn();

    renderHook(() =>
      useExpandEarlierForFocus({
        focusedRowId: "today-1",
        earlierRowIds: ["overdue-1"],
        setExpandedImmediately: setExpanded,
      }),
    );

    expect(setExpanded).not.toHaveBeenCalled();
  });
});

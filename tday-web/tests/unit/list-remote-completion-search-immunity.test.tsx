// @vitest-environment jsdom

/**
 * PR #160 review finding: before Earlier empty-state parity landed on the
 * List screen, `ListContainer`'s remote-completion celebration signal was
 * `useCelebrateEmptyTransition(listTodos.length === 0)` — built from the RAW,
 * unfiltered list, immune to whatever the user has typed into the search box.
 * The parity change swapped that for a signal derived from the
 * search-filtered set (`hasNonEarlierListTodos`, built off `filteredTodos`).
 *
 * `useCelebrateEmptyTransition` is a plain previous-vs-current ref comparison
 * with no notion of *why* its input changed. Feeding it a search-filtered
 * count reintroduces a real regression: type a query that happens to match
 * none of the current tasks and the filtered count drops to zero from typing
 * alone — the ref records a "just emptied" transition that has nothing to do
 * with a completion. If the last current task is then genuinely completed
 * *remotely* (another device/collaborator) while that same non-matching
 * search is still active, the filtered signal stays zero→zero — no new
 * transition — so the real completion's confetti is silently dropped by the
 * time the user clears the search and the illustration actually appears.
 *
 * The fix (`hasNonEarlierTimelineTodos` in
 * `@/lib/timeline/buildTimelineSections`, fed the RAW `listTodos` in
 * `ListContainer`) restores List's pre-existing immunity to the search box
 * for this one signal, while every other derivation (the visible
 * illustration/timeline gating) keeps legitimately reacting to the query.
 *
 * This test exercises the exact composition `ListContainer` now uses
 * (`hasNonEarlierTimelineTodos` + `useCelebrateEmptyTransition`) end to end,
 * and contrasts it with the search-filtered composition the PR briefly
 * shipped, to document the regression the fix closes.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderHook } from "@testing-library/react";
import { hasNonEarlierTimelineTodos } from "@/lib/timeline/buildTimelineSections";
import { useCelebrateEmptyTransition } from "@/hooks/use-celebrate-empty-transition";
import type { TodoItemType } from "@/types";

function makeTodo(id: string, dueIso: string): TodoItemType {
  return {
    id,
    title: id,
    description: null,
    pinned: false,
    createdAt: new Date(dueIso),
    order: 0,
    priority: "Low",
    due: new Date(dueIso),
    rrule: null,
    timeZone: "UTC",
    userID: "u1",
    completed: false,
    exdates: [],
  } as unknown as TodoItemType;
}

const buildArgs = {
  locale: "en-US",
  timeZone: "UTC",
  futureOnly: false,
  placesEarlierBeforeToday: true,
  includeEmptyDropTargets: false,
  todayLabel: "Today",
  tomorrowLabel: "Tomorrow",
};

/** The fixed composition: `remoteEmptied` fed the RAW list. */
function useFixedRemoteEmptied(rawTodos: TodoItemType[]) {
  const hasNonEarlierRaw = hasNonEarlierTimelineTodos({ ...buildArgs, todos: rawTodos });
  return useCelebrateEmptyTransition(!hasNonEarlierRaw);
}

/** The regression this PR briefly shipped: `remoteEmptied` fed the search-filtered list. */
function useBuggyRemoteEmptied(filteredTodos: TodoItemType[]) {
  const hasNonEarlierFiltered = hasNonEarlierTimelineTodos({ ...buildArgs, todos: filteredTodos });
  return useCelebrateEmptyTransition(!hasNonEarlierFiltered);
}

describe("List remote-completion celebration stays immune to the search box", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    // Tuesday 2026-06-02, noon UTC.
    vi.setSystemTime(new Date("2026-06-02T12:00:00.000Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("a non-matching search does not fake an empty transition, and a real remote completion during that search still celebrates", () => {
    const overdue = makeTodo("overdue", "2026-05-30T09:00:00.000Z");
    const current = makeTodo("current", "2026-06-02T15:00:00.000Z");

    const { result, rerender } = renderHook(
      ({ rawTodos }: { rawTodos: TodoItemType[] }) => useFixedRemoteEmptied(rawTodos),
      { initialProps: { rawTodos: [overdue, current] } },
    );

    // A current task remains: not emptied.
    expect(result.current).toBe(false);

    // The user types a search query matching none of the current tasks.
    // This signal is built from the RAW list, so a search-only re-render
    // (the raw list itself is unchanged) must not move it at all.
    rerender({ rawTodos: [overdue, current] });
    expect(result.current).toBe(false);

    // Remote completion: another device/collaborator finishes the last
    // current task while that non-matching search is still active.
    rerender({ rawTodos: [overdue] });
    expect(result.current).toBe(true);

    // The search is cleared afterward, still within the celebration window —
    // the transition was already recorded and does not depend on search
    // state to keep celebrating.
    rerender({ rawTodos: [overdue] });
    expect(result.current).toBe(true);
  });

  it("contrast: feeding the search-filtered set instead drops the real completion's celebration (the regression this fixes)", () => {
    const overdue = makeTodo("overdue", "2026-05-30T09:00:00.000Z");
    const current = makeTodo("current", "2026-06-02T15:00:00.000Z");

    const { result, rerender } = renderHook(
      ({ filteredTodos }: { filteredTodos: TodoItemType[] }) => useBuggyRemoteEmptied(filteredTodos),
      { initialProps: { filteredTodos: [overdue, current] } },
    );
    expect(result.current).toBe(false);

    // Typing a non-matching search filters the current task out of the set
    // this (buggy) signal watches — it flips to "no current tasks" from
    // typing alone, well before any real completion happened.
    rerender({ filteredTodos: [overdue] });
    expect(result.current).toBe(true); // bogus celebration, triggered by typing

    // Time passes — long enough for that bogus window to expire — while the
    // search is still active and still matches nothing. The comparison runs
    // during render (see the hook's own doc comment), so re-render with the
    // clock advanced to actually re-evaluate it.
    vi.setSystemTime(new Date("2026-06-02T12:00:05.000Z")); // +5s > the 4s window
    rerender({ filteredTodos: [overdue] });
    expect(result.current).toBe(false);

    // The real remote completion now happens, but the filtered set was
    // already "no current tasks" the instant before (still filtered to just
    // the overdue task) — zero→zero, no new transition recorded. The
    // completion's confetti is silently dropped.
    rerender({ filteredTodos: [overdue] });
    expect(result.current).toBe(false);
  });
});

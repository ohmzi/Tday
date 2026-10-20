// @vitest-environment jsdom

/**
 * The celebration's missing ENDING: a pending task coming back.
 *
 * A celebration says "this list is finished". It was OPENED by a transition — a
 * completion, or a list emptying under a collaborator's hands — and only ever
 * CLOSED by re-reading a static predicate plus a four-second window. Nothing
 * anywhere observed the opposite transition, so an undo restored the row through
 * the query cache and the paper went on flying over it.
 *
 * The sharp case is the reported one, and the first two tests below are it.
 * Every client's "this scope is finished" predicate deliberately excludes the
 * Earlier/overdue bucket, because finishing today's work while overdue tasks
 * wait still earns the payoff (requirement 4). So an undone OVERDUE row changes
 * that predicate not at all: `showEmpty` is true on both sides of it and nothing
 * transitions. The SECOND of the two is the one that tells a cancel from a
 * transition — the screen holds rows on both sides of the undo as well, so a fix
 * written as the mirror of the non-empty → empty edge fires exactly never there,
 * while passing the first test by accident. What moves in both is the COUNT,
 * which is why the count is what is watched.
 *
 * Driven through `useListEmptyState` rather than a container: that hook is where
 * `showEmpty` and `celebrate` are derived side by side, and the bug is precisely
 * a disagreement between those two. A container would add a screen's worth of
 * query mocking to reach the same two lines.
 */

import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useListEmptyState } from "@/features/list/lib/useListEmptyState";
import {
  CELEBRATION_WINDOW_MS,
  markCelebrationCancelled,
  markTaskCompleted,
  shouldCelebrateEmptyState,
} from "@/lib/task-completion-signal";

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  // The module's three stamps are module state and outlive a test. Every test
  // below opens with a completion and a cancel far enough in the past to be
  // irrelevant, so none of them inherits the previous one's ordering.
  markTaskCompleted();
  markCelebrationCancelled();
  vi.advanceTimersByTime(CELEBRATION_WINDOW_MS * 2);
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

type ListState = {
  /** Every row the list holds, Earlier's overdue ones included. */
  pendingRowCount: number;
  /** The list holds at least one row that is NOT overdue. */
  hasCurrent: boolean;
  /** The list holds at least one overdue row. */
  hasOverdue: boolean;
};

function mountList(initial: ListState) {
  return renderHook(
    (state: ListState) =>
      useListEmptyState({
        listTodosLoading: false,
        isSearching: false,
        hasEarlierItems: state.hasOverdue,
        hasNonEarlierListTodos: state.hasCurrent,
        hasNonEarlierRawListTodos: state.hasCurrent,
        pendingRowCount: state.pendingRowCount,
      }),
    { initialProps: initial },
  );
}

describe("an undo while the paper is still in the air", () => {
  it("ends the celebration over a restored OVERDUE row, which leaves the scope 'empty'", () => {
    // One overdue task and nothing else — the screenshot's list.
    const { result, rerender } = mountList({
      pendingRowCount: 1,
      hasCurrent: false,
      hasOverdue: true,
    });
    expect(result.current.celebrate).toBe(false);

    // Ticked off. The list is finished, the scene comes up, the burst is thrown.
    markTaskCompleted();
    rerender({ pendingRowCount: 0, hasCurrent: false, hasOverdue: false });
    expect(result.current.celebrate).toBe(true);
    expect(result.current.showEmpty).toBe(true);

    // Undo. The row is back and the user can see it — and the emptiness
    // predicate has not moved a millimetre, because an overdue row lives in
    // Earlier and Earlier is excluded from it by design. This is the assertion
    // the reported bug fails and a transition-shaped fix would fail with it:
    // `showEmpty` STILL true, and `celebrate` false anyway.
    rerender({ pendingRowCount: 1, hasCurrent: false, hasOverdue: true });
    expect(result.current.showEmpty).toBe(true);
    expect(result.current.celebrate).toBe(false);
  });

  it("ends it when the screen was never empty of rows on EITHER side of the undo", () => {
    // The case that separates a cancel from a transition. Two overdue rows and
    // no current ones: the scope already reads as finished, so ticking one off
    // opens the window with a row still on screen — and undoing it puts a
    // SECOND row back beside the first. Neither `showEmpty` nor "is this list
    // empty of rows" moves at any point, so a fix written as the mirror of
    // `useEmptyTransitionOpenedAt`'s non-empty → empty edge fires exactly never
    // here. Only the count moves: 2, 1, 2.
    const { result, rerender } = mountList({
      pendingRowCount: 2,
      hasCurrent: false,
      hasOverdue: true,
    });

    markTaskCompleted();
    rerender({ pendingRowCount: 1, hasCurrent: false, hasOverdue: true });
    expect(result.current.celebrate).toBe(true);
    expect(result.current.showEmpty).toBe(true);

    rerender({ pendingRowCount: 2, hasCurrent: false, hasOverdue: true });
    expect(result.current.showEmpty).toBe(true);
    expect(result.current.celebrate).toBe(false);
  });

  it("ends it over a restored CURRENT row too", () => {
    const { result, rerender } = mountList({
      pendingRowCount: 1,
      hasCurrent: true,
      hasOverdue: false,
    });

    markTaskCompleted();
    rerender({ pendingRowCount: 0, hasCurrent: false, hasOverdue: false });
    expect(result.current.celebrate).toBe(true);

    // The scope does stop being empty here, which is what made this half of the
    // defect invisible: the scene goes, so nobody reported the burst being cut
    // out of mid-air behind it. `celebrate` still has to be false — it is what
    // `Confetti`'s `play` is wired to, and a burst that is never told the
    // celebration ended has nothing to fade.
    rerender({ pendingRowCount: 1, hasCurrent: true, hasOverdue: false });
    expect(result.current.showEmpty).toBe(false);
    expect(result.current.celebrate).toBe(false);
  });

  it("does not cancel the completion that is still to come", () => {
    // A cancel is compared against the opening stamp, never subtracted from a
    // flag, so a LATER completion re-opens the window without anything being
    // reset. Otherwise one undo would leave the screen unable to celebrate for
    // the rest of the session.
    const { result, rerender } = mountList({
      pendingRowCount: 1,
      hasCurrent: true,
      hasOverdue: false,
    });

    markTaskCompleted();
    rerender({ pendingRowCount: 0, hasCurrent: false, hasOverdue: false });
    rerender({ pendingRowCount: 1, hasCurrent: true, hasOverdue: false });
    expect(result.current.celebrate).toBe(false);

    // Ticked off again, a moment later.
    act(() => {
      vi.advanceTimersByTime(10);
    });
    markTaskCompleted();
    rerender({ pendingRowCount: 0, hasCurrent: false, hasOverdue: false });
    expect(result.current.celebrate).toBe(true);
  });

  it("ends the REMOTE window as well, which is the other half of the same OR", () => {
    // Nothing was completed on this tab at all: the list emptied under a
    // collaborator's hands, which arms `useEmptyTransitionOpenedAt` instead. An
    // ending only one of the two windows observes is not an ending — the OR
    // stays true on the other's stale read.
    const { result, rerender } = mountList({
      pendingRowCount: 1,
      hasCurrent: true,
      hasOverdue: false,
    });

    rerender({ pendingRowCount: 0, hasCurrent: false, hasOverdue: false });
    expect(result.current.celebrate).toBe(true);

    // ...and then the collaborator puts one back, or adds one. Either way a row
    // arrived, and the cache-change signal that carries it says nothing about
    // which — deliberately, because both make "finished" false.
    rerender({ pendingRowCount: 1, hasCurrent: false, hasOverdue: true });
    expect(result.current.showEmpty).toBe(true);
    expect(result.current.celebrate).toBe(false);
  });

  it("is not fired by rows LEAVING, which is what opens a celebration", () => {
    // The guard on the backstop: it watches for a count RISE. A falling count is
    // the completion itself, and cancelling on it would cancel every burst in
    // the frame it was thrown.
    const { result, rerender } = mountList({
      pendingRowCount: 3,
      hasCurrent: true,
      hasOverdue: false,
    });

    markTaskCompleted();
    rerender({ pendingRowCount: 2, hasCurrent: true, hasOverdue: false });
    rerender({ pendingRowCount: 1, hasCurrent: true, hasOverdue: false });
    rerender({ pendingRowCount: 0, hasCurrent: false, hasOverdue: false });
    expect(result.current.celebrate).toBe(true);
  });
});

/**
 * The decision itself, with no hook, no clock and no screen around it — the
 * shape Android's `shouldCelebrateEmptyState` and iOS's have, for the reason
 * they have it: the ordering rule is the part that can be wrong, and the part
 * that cannot be proven by running the app.
 *
 * The two opening stamps arrive already zeroed once their own window has shut
 * (`useCompletionOpenedAt`, `useEmptyTransitionOpenedAt`), so what is left here
 * is purely which of the three stamps is newest.
 */
describe("shouldCelebrateEmptyState", () => {
  it("celebrates on either window, with nothing having arrived", () => {
    expect(
      shouldCelebrateEmptyState({
        completionOpenedAtMs: 1_000,
        remoteEmptiedAtMs: 0,
        cancelledAtMs: 0,
      }),
    ).toBe(true);
    expect(
      shouldCelebrateEmptyState({
        completionOpenedAtMs: 0,
        remoteEmptiedAtMs: 1_000,
        cancelledAtMs: 0,
      }),
    ).toBe(true);
  });

  it("celebrates nothing with both windows shut", () => {
    expect(
      shouldCelebrateEmptyState({
        completionOpenedAtMs: 0,
        remoteEmptiedAtMs: 0,
        cancelledAtMs: 0,
      }),
    ).toBe(false);
  });

  it("is cancelled by an arrival after the opening", () => {
    expect(
      shouldCelebrateEmptyState({
        completionOpenedAtMs: 1_000,
        remoteEmptiedAtMs: 0,
        cancelledAtMs: 1_001,
      }),
    ).toBe(false);
  });

  it("gives a same-millisecond arrival the win", () => {
    // `>=` and not `>`. An undo always follows the completion it undoes, but a
    // fake clock, a test or a fast machine will put both on one tick, and a
    // same-tick arrival must lose to nothing.
    expect(
      shouldCelebrateEmptyState({
        completionOpenedAtMs: 1_000,
        remoteEmptiedAtMs: 0,
        cancelledAtMs: 1_000,
      }),
    ).toBe(false);
  });

  it("re-opens on a completion newer than the cancel, with nothing reset", () => {
    expect(
      shouldCelebrateEmptyState({
        completionOpenedAtMs: 1_002,
        remoteEmptiedAtMs: 0,
        cancelledAtMs: 1_001,
      }),
    ).toBe(true);
  });

  it("compares the cancel against the NEWER of the two openings, not the older", () => {
    // A cancel that landed between the two openings has been overtaken. Reading
    // it against the older stamp would suppress a celebration that a later
    // event legitimately opened — and reading it against only the completion
    // stamp would let the remote branch celebrate over a restored row.
    expect(
      shouldCelebrateEmptyState({
        completionOpenedAtMs: 1_000,
        remoteEmptiedAtMs: 1_002,
        cancelledAtMs: 1_001,
      }),
    ).toBe(true);
    expect(
      shouldCelebrateEmptyState({
        completionOpenedAtMs: 1_002,
        remoteEmptiedAtMs: 1_000,
        cancelledAtMs: 1_003,
      }),
    ).toBe(false);
  });
});

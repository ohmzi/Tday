// @vitest-environment jsdom

/**
 * The same missing ENDING, on the two feeds that were left out of it.
 *
 * `undo-cancels-celebration.test.tsx` pins the scoped screens. These two — the
 * Anytime tab's own feed and one Anytime list — reached the celebration by a
 * different road: `celebrate={taskJustCompleted() || remoteEmptied}`, two
 * windows OR'd together with nothing observing a row coming back. The cancel
 * stamp their own mutations were already writing (`complete-floater.ts`,
 * `create-floater.ts`) was read by nobody on the screens those mutations serve,
 * so the reported bug was entirely unfixed here while the code that was supposed
 * to fix it sat one directory away.
 *
 * Two things are pinned, because two things were wrong and fixing one is half a
 * fix. That the celebration ENDS on the arrival, and that the scene it is
 * painted into is still in the tree while the paper fades — the mount guard on
 * these feeds IS the emptiness test, so the undo unmounted the canvas on the
 * arrival frame and the envelope never ran.
 *
 * Driven through `useFloaterEmptyState` rather than either container: that hook
 * is where both screens' `showEmpty`, `celebrate` and linger are derived side by
 * side, and it is the thing the two of them now share. A container would add a
 * screen's worth of query mocking to reach the same three lines.
 *
 * What is NOT pinned here, deliberately: the overdue case. An Anytime task has
 * no date, so it can never be overdue and there is no Earlier bucket on either
 * feed — every restored row makes the feed non-empty. That is why the scene
 * always leaves here and why the linger matters on every undo rather than on
 * one shape of it.
 */

import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useFloaterEmptyState } from "@/features/floater/lib/useFloaterEmptyState";
import { DURATION_MS } from "@/lib/motion";
import {
  CELEBRATION_WINDOW_MS,
  markCelebrationCancelled,
  markTaskCompleted,
} from "@/lib/task-completion-signal";
import { installReducedMotion } from "../setup/reduced-motion";

const realMatchMedia = window.matchMedia;

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  // The three stamps are module state and outlive a test. Every test below opens
  // with a completion and a cancel far enough in the past to be irrelevant, so
  // none of them inherits the previous one's ordering.
  markTaskCompleted();
  markCelebrationCancelled();
  vi.advanceTimersByTime(CELEBRATION_WINDOW_MS * 2);
});

afterEach(() => {
  cleanup();
  vi.useRealTimers();
  window.matchMedia = realMatchMedia;
});

type FeedState = { pendingRowCount: number; isSearching?: boolean };

function mountFeed(initial: FeedState) {
  return renderHook(
    (state: FeedState) =>
      useFloaterEmptyState({
        isLoading: false,
        isSearching: state.isSearching ?? false,
        pendingRowCount: state.pendingRowCount,
      }),
    { initialProps: initial },
  );
}

describe("an undo on an Anytime feed while the paper is still in the air", () => {
  it("ends the celebration the moment the task comes back", () => {
    const { result, rerender } = mountFeed({ pendingRowCount: 1 });
    expect(result.current.celebrate).toBe(false);

    markTaskCompleted();
    rerender({ pendingRowCount: 0 });
    expect(result.current.celebrate).toBe(true);
    expect(result.current.showEmpty).toBe(true);

    rerender({ pendingRowCount: 1 });
    expect(result.current.showEmpty).toBe(false);
    expect(result.current.celebrate).toBe(false);
  });

  it("holds the scene for the burst's own fade, then lets it go", () => {
    const { result, rerender } = mountFeed({ pendingRowCount: 1 });

    markTaskCompleted();
    rerender({ pendingRowCount: 0 });
    expect(result.current.sceneLeavingOnCancel).toBe(false);

    // The undo. The feed is not empty any more, so the scene is on its way out —
    // but it is still in the tree, because the paper inside it is still fading
    // and an unmount here cuts the fade one layer down.
    rerender({ pendingRowCount: 1 });
    expect(result.current.showEmpty).toBe(false);
    expect(result.current.sceneLeavingOnCancel).toBe(true);

    act(() => {
      vi.advanceTimersByTime(DURATION_MS.quick + 1);
    });
    expect(result.current.sceneLeavingOnCancel).toBe(false);
  });

  it("ends it when the feed was never empty of rows on EITHER side of the undo", () => {
    // The case that separates a cancel from a transition, and the reason the
    // COUNT is what is watched. Three tasks, one ticked off, one undone: nothing
    // about "is this feed empty" moves at any point, so a fix written as the
    // mirror of the non-empty → empty edge fires exactly never. Only the count
    // does: 3, 2, 3.
    const { result, rerender } = mountFeed({ pendingRowCount: 3 });

    markTaskCompleted();
    rerender({ pendingRowCount: 2 });
    rerender({ pendingRowCount: 3 });
    expect(result.current.celebrate).toBe(false);
  });

  it("does not cancel the completion that is still to come", () => {
    // A cancel is compared against the opening stamp, never subtracted from a
    // flag, so a LATER completion re-opens the window with nothing reset.
    const { result, rerender } = mountFeed({ pendingRowCount: 1 });

    markTaskCompleted();
    rerender({ pendingRowCount: 0 });
    rerender({ pendingRowCount: 1 });
    expect(result.current.celebrate).toBe(false);

    act(() => {
      vi.advanceTimersByTime(10);
    });
    markTaskCompleted();
    rerender({ pendingRowCount: 0 });
    expect(result.current.celebrate).toBe(true);
  });

  it("ends the REMOTE window too, which is the other half of the same OR", () => {
    // Nothing was completed on this tab: the feed emptied under a collaborator's
    // hands, which arms the other window. An ending only one of the two observes
    // is not an ending.
    const { result, rerender } = mountFeed({ pendingRowCount: 1 });

    rerender({ pendingRowCount: 0 });
    expect(result.current.celebrate).toBe(true);

    rerender({ pendingRowCount: 1 });
    expect(result.current.celebrate).toBe(false);
  });

  it("is not fired by rows LEAVING, which is what opens a celebration", () => {
    const { result, rerender } = mountFeed({ pendingRowCount: 3 });

    markTaskCompleted();
    rerender({ pendingRowCount: 2 });
    rerender({ pendingRowCount: 1 });
    rerender({ pendingRowCount: 0 });
    expect(result.current.celebrate).toBe(true);
  });

  it("holds nothing at all under reduced motion", () => {
    // The fifth idiom rule: nothing was painted, so no wait may survive in front
    // of the restored row. `useFadeUnmount` returns false outright here, which is
    // what takes the scene away on the cancel frame instead of one Quick later.
    installReducedMotion(true);
    const { result, rerender } = mountFeed({ pendingRowCount: 1 });

    markTaskCompleted();
    rerender({ pendingRowCount: 0 });
    rerender({ pendingRowCount: 1 });
    expect(result.current.celebrate).toBe(false);
    expect(result.current.sceneLeavingOnCancel).toBe(false);
  });
});

describe("a search query, which is not a finished feed", () => {
  it("arms neither window by hiding every row", () => {
    // The remote window watches the RAW count, so a query that matches nothing
    // is not a collaborator emptying the feed — nothing opens, and the scene is
    // not drawn either, because `showEmpty` reads the query as well.
    //
    // Nothing is completed in this test on purpose. `celebrate` is a fact about
    // the app's last few seconds rather than about this screen (a completion on
    // ANY feed opens the window; `showEmpty` is what decides whether a scene is
    // drawn to spend it on), so opening one here would prove nothing about the
    // query.
    const { result, rerender } = mountFeed({ pendingRowCount: 3 });

    rerender({ pendingRowCount: 3, isSearching: true });
    expect(result.current.showEmpty).toBe(false);
    expect(result.current.celebrate).toBe(false);
  });

  it("still ends one when a row arrives behind a standing query", () => {
    // The mirror of the above, and the reason the cancel is fed the raw count:
    // a task the user adds while searching is still a task arriving on a feed
    // whose celebration says it is finished.
    const { result, rerender } = mountFeed({ pendingRowCount: 1 });

    markTaskCompleted();
    rerender({ pendingRowCount: 0 });
    expect(result.current.celebrate).toBe(true);

    rerender({ pendingRowCount: 1, isSearching: true });
    expect(result.current.celebrate).toBe(false);
  });
});

// @vitest-environment jsdom

/**
 * A committed completion keeps its removing phase until the row has actually left the DOM.
 *
 * The commit prunes the row out of the caches, and React Query hands that write to React a tick
 * later. If the phase were cleared in the same tick as the commit, the still-mounted row would
 * re-read "no phase" for one render, snap back to its open box and push the rows below down, and
 * only then be removed — the jump the staged check-off sequence exists to take out. These tests pin
 * the hold: removing survives the commit while a row is mounted for it, and is released by that
 * row's unmount (or immediately, when no row is mounted).
 */

import { act, cleanup, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  getTaskCompletionPhase,
  stageTaskCompletion,
  useTaskCompletionPhase,
} from "@/lib/taskCompletionStaging";
import { TASK_COMPLETION_TOTAL_MS } from "@/lib/taskCompletionTiming";

describe("a committed completion's exit", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => {
    vi.useRealTimers();
    cleanup();
  });

  it("holds the removing phase after the commit while the row is still mounted", async () => {
    const commit = vi.fn();
    const id = "hold-mounted";
    const { unmount } = renderHook(() => useTaskCompletionPhase(id));

    stageTaskCompletion(id, commit);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS);
    });

    // The prune has run...
    expect(commit).toHaveBeenCalledTimes(1);
    // ...and the row that is still on screen has not been handed its finished, un-collapsed frame.
    expect(getTaskCompletionPhase(id)).toBe("removing");

    // Once the row leaves the DOM the sequence is over, so a remount (an undo) comes back clean.
    unmount();
    expect(getTaskCompletionPhase(id)).toBeNull();
  });

  it("is released at the commit when no row is showing it", async () => {
    const commit = vi.fn();
    const id = "hold-unmounted";

    stageTaskCompletion(id, commit);
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS);
    });

    expect(commit).toHaveBeenCalledTimes(1);
    expect(getTaskCompletionPhase(id)).toBeNull();
  });

  it("releases the sequence when the commit throws, so the row is not left faded", async () => {
    const id = "hold-throws";
    const { result, unmount } = renderHook(() => useTaskCompletionPhase(id));

    stageTaskCompletion(id, () => {
      throw new Error("commit failed");
    });
    await act(async () => {
      await expect(vi.advanceTimersByTimeAsync(TASK_COMPLETION_TOTAL_MS)).rejects.toThrow(
        "commit failed",
      );
    });

    expect(getTaskCompletionPhase(id)).toBeNull();
    expect(result.current).toBeNull();
    unmount();
  });
});

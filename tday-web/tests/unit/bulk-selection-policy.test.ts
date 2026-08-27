import { describe, expect, it } from "vitest";
import {
  BULK_MAX_CONCURRENCY,
  BULK_MAX_SELECTION,
  bulkActionAppliesToRecurring,
  bulkActionRequiresConfirmation,
  distinctSourceListCount,
  effectiveBulkSelection,
  isBulkSelectionAtCap,
  type BulkAction,
} from "@/lib/bulk/bulk-selection-policy";
import { runBulkFanOut } from "@/lib/bulk/run-bulk-fan-out";

const ACTIONS: BulkAction[] = ["complete", "delete", "priority", "move"];

type Row = {
  id: string;
  rrule: string | null;
  listID: string | null;
  instanceDate?: Date | null;
};

const row = (
  id: string,
  rrule: string | null = null,
  listID: string | null = null,
  instanceDate: Date | null = null,
): Row => ({
  id,
  rrule,
  listID,
  instanceDate,
});

const isRecurring = (candidate: Row) => Boolean(candidate.rrule);
const hasInstanceDate = (candidate: Row) => Boolean(candidate.instanceDate);

describe("bulk selection policy", () => {
  it("mirrors the shared Kotlin literals", () => {
    // BulkSelectionPolicy.kt is the source of truth; if it moves, this moves.
    expect(BULK_MAX_SELECTION).toBe(100);
    expect(BULK_MAX_CONCURRENCY).toBe(4);
  });

  it("keeps bulk delete guarded — the safety invariant", () => {
    // The load-bearing one. If anyone ever makes bulk delete unguarded, or
    // teaches it to touch a recurring series, this goes red.
    expect(bulkActionRequiresConfirmation("delete", 1)).toBe(true);
    expect(bulkActionRequiresConfirmation("delete", 5)).toBe(true);
    expect(bulkActionAppliesToRecurring("delete")).toBe(false);

    const unguarded = ACTIONS.filter(
      (action) => !bulkActionRequiresConfirmation(action, 1),
    );
    expect(unguarded).toEqual(["complete", "priority", "move"]);
  });

  it("asks before a move only when the sources differ", () => {
    expect(bulkActionRequiresConfirmation("move", 1)).toBe(false);
    expect(bulkActionRequiresConfirmation("move", 2)).toBe(true);
  });

  it("counts 'no list' as a source of its own", () => {
    expect(distinctSourceListCount([null, null])).toBe(1);
    expect(distinctSourceListCount([null, "list-a"])).toBe(2);
    expect(distinctSourceListCount(["list-a", "list-a", undefined])).toBe(2);
  });

  it("lets only complete address a recurring occurrence", () => {
    expect(bulkActionAppliesToRecurring("complete")).toBe(true);
    expect(bulkActionAppliesToRecurring("priority")).toBe(false);
    expect(bulkActionAppliesToRecurring("move")).toBe(false);
  });

  it("drops recurring rows from delete, priority and move", () => {
    const selection = [row("a"), row("b", "FREQ=DAILY"), row("c")];
    for (const action of ["delete", "priority", "move"] as BulkAction[]) {
      expect(
        effectiveBulkSelection(action, selection, isRecurring).map((r) => r.id),
      ).toEqual(["a", "c"]);
    }
  });

  it("completes a recurring row only when it is a real occurrence", () => {
    const occurrence = row("b", "FREQ=DAILY", null, new Date("2026-08-20T09:00:00Z"));
    const template = row("t", "FREQ=DAILY");
    const selection = [row("a"), occurrence, template, row("c")];

    // The template has no instanceDate, so `completeTodo` would insert a
    // CompletedTodos row, mark nothing complete and leave the task on screen.
    expect(
      effectiveBulkSelection(
        "complete",
        selection,
        isRecurring,
        hasInstanceDate,
      ).map((r) => r.id),
    ).toEqual(["a", "b", "c"]);
  });

  it("treats a recurring row as uncompletable when no occurrence test is given", () => {
    // The default has to be the safe answer: a caller that has not thought
    // about instanceDate must not get the phantom-history-row behaviour.
    const selection = [row("a"), row("b", "FREQ=DAILY")];
    expect(
      effectiveBulkSelection("complete", selection, isRecurring).map((r) => r.id),
    ).toEqual(["a"]);
  });

  it("caps in display order from the top, deterministically", () => {
    const selection = Array.from({ length: 150 }, (_, index) =>
      row(`todo-${index}`),
    );
    const capped = effectiveBulkSelection("delete", selection, isRecurring);
    expect(capped).toHaveLength(BULK_MAX_SELECTION);
    expect(capped[0].id).toBe("todo-0");
    expect(capped[BULK_MAX_SELECTION - 1].id).toBe(`todo-${BULK_MAX_SELECTION - 1}`);
  });

  it("does not mutate the selection it was handed", () => {
    const selection = [row("a"), row("b")];
    effectiveBulkSelection("complete", selection, isRecurring).push(row("c"));
    expect(selection).toHaveLength(2);
  });

  it("refuses further taps at the cap", () => {
    expect(isBulkSelectionAtCap(BULK_MAX_SELECTION - 1)).toBe(false);
    expect(isBulkSelectionAtCap(BULK_MAX_SELECTION)).toBe(true);
  });
});

describe("runBulkFanOut", () => {
  it("runs every item and reports how many threw", async () => {
    const seen: string[] = [];
    const result = await runBulkFanOut(
      ["a", "b", "c", "d", "e"],
      async (item) => {
        seen.push(item);
        if (item === "b" || item === "d") throw new Error("nope");
      },
      2,
    );

    // A failure part way through must not abandon the rest of the batch:
    // a half-applied destructive action is worse than one that finishes and
    // says what it could not do.
    expect(seen.sort()).toEqual(["a", "b", "c", "d", "e"]);
    expect(result).toEqual({ total: 5, failed: 2 });
  });

  it("never exceeds the concurrency it was given", async () => {
    let inFlight = 0;
    let peak = 0;
    await runBulkFanOut(
      Array.from({ length: 20 }, (_, index) => index),
      async () => {
        inFlight += 1;
        peak = Math.max(peak, inFlight);
        await Promise.resolve();
        await Promise.resolve();
        inFlight -= 1;
      },
      BULK_MAX_CONCURRENCY,
    );
    expect(peak).toBeLessThanOrEqual(BULK_MAX_CONCURRENCY);
  });

  it("does nothing for an empty selection", async () => {
    const run = async () => {
      throw new Error("should not be called");
    };
    await expect(runBulkFanOut([], run)).resolves.toEqual({
      total: 0,
      failed: 0,
    });
  });
});

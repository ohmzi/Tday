import { describe, expect, it } from "vitest";

import { compareFloaters, priorityRank, type TaskSortKey } from "@/lib/taskSort";

function key(overrides: Partial<TaskSortKey> = {}): TaskSortKey {
  return {
    id: "a",
    pinned: false,
    dueEpochMs: null,
    priorityRank: 0,
    updatedAtEpochMs: null,
    ...overrides,
  };
}

describe("priorityRank", () => {
  it("ranks the four recognized tiers High < Medium < Low < Lowest", () => {
    expect(priorityRank("High")).toBe(0);
    expect(priorityRank("Medium")).toBe(1);
    expect(priorityRank("Low")).toBe(2);
    expect(priorityRank("Lowest")).toBe(3);
  });

  it("is tolerant of legacy/server spellings and case", () => {
    expect(priorityRank("urgent")).toBe(0);
    expect(priorityRank("IMPORTANT")).toBe(1);
    expect(priorityRank(" lowest ")).toBe(3);
  });

  // CRITICAL cross-platform contract: unrecognized/absent input is a different
  // concept from a user explicitly choosing the new "Lowest" tier, and must keep
  // degrading to Low's rank (2), never fall to the Lowest tier's rank (3).
  it("degrades unrecognized or absent priority to Low's rank, not Lowest's", () => {
    expect(priorityRank(undefined)).toBe(2);
    expect(priorityRank(null)).toBe(2);
    expect(priorityRank("")).toBe(2);
    expect(priorityRank("some-garbage-value")).toBe(2);
  });
});

describe("compareFloaters priority ordering", () => {
  it("sorts Lowest after Low, and unrecognized priority alongside Low", () => {
    const rows: TaskSortKey[] = [
      key({ id: "lowest", priorityRank: priorityRank("Lowest") }),
      key({ id: "high", priorityRank: priorityRank("High") }),
      key({ id: "garbage", priorityRank: priorityRank("not-a-real-priority") }),
      key({ id: "low", priorityRank: priorityRank("Low") }),
      key({ id: "medium", priorityRank: priorityRank("Medium") }),
    ];

    const sorted = [...rows].sort(compareFloaters).map((r) => r.id);

    expect(sorted[0]).toBe("high");
    expect(sorted[1]).toBe("medium");
    // "garbage" degrades to Low's rank, so it ties with "low" and the id
    // tiebreak decides the order between them ("garbage" < "low" lexically).
    expect(sorted[2]).toBe("garbage");
    expect(sorted[3]).toBe("low");
    expect(sorted[4]).toBe("lowest");
  });
});

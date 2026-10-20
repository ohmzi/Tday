import { describe, expect, it } from "vitest";

import { sortFloaters } from "@/lib/local/localFloaters";
import type { LocalFloaterRow } from "@/lib/local/localDb";

function row(id: string, priority: string, overrides: Partial<LocalFloaterRow> = {}): LocalFloaterRow {
  return {
    id,
    title: id,
    description: null,
    pinned: false,
    priority,
    completed: false,
    order: 0,
    listID: null,
    createdAt: "2026-01-01T00:00:00.000Z",
    updatedAt: "2026-01-01T00:00:00.000Z",
    ...overrides,
  };
}

describe("localFloaters sortFloaters", () => {
  it("sorts High, Medium, Low, Lowest in that order (priority desc)", () => {
    const rows = [
      row("lowest", "Lowest"),
      row("low", "Low"),
      row("high", "High"),
      row("medium", "Medium"),
    ];

    expect(sortFloaters(rows).map((r) => r.id)).toEqual([
      "high",
      "medium",
      "low",
      "lowest",
    ]);
  });

  // CRITICAL: this map's fallback for a genuinely unrecognized priority string
  // must land on Low's rank, not the new Lowest tier's rank — the same
  // "unknown degrades to Normal" invariant taskSort.ts enforces.
  it("treats an unrecognized priority string as Low's rank, not Lowest's", () => {
    const rows = [row("lowest", "Lowest"), row("garbage", "not-a-real-priority")];

    const sorted = sortFloaters(rows).map((r) => r.id);
    // "garbage" ties with Low's rank, which still sorts strictly ahead of the
    // explicit Lowest tier.
    expect(sorted).toEqual(["garbage", "lowest"]);
  });

  it("keeps pinned-first and manual order as tiebreaks within the same priority", () => {
    const rows = [
      row("a", "Lowest", { pinned: false, order: 2 }),
      row("b", "Lowest", { pinned: true, order: 5 }),
      row("c", "Lowest", { pinned: false, order: 1 }),
    ];

    expect(sortFloaters(rows).map((r) => r.id)).toEqual(["b", "c", "a"]);
  });
});

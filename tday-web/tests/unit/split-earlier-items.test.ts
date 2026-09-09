import { describe, expect, it } from "vitest";
import { splitEarlierItems } from "@/features/todayTodos/lib/timelineScopeHelpers";

/**
 * Earlier/empty-state parity (Scheduled/Priority/All/Lists): `splitEarlierItems`
 * is the single reduction `useTimelineEmptyState` (All/Priority/Scheduled) and
 * `ListContainer` (custom Lists) both call to decide "this scope's own zero"
 * without folding in whatever its Earlier bucket already holds — see
 * `useTimelineEmptyState`'s own doc comment for the full requirement-1
 * rationale. It mirrors `getTimelinePriority`'s own `dayDiff < 0` rule (the
 * same rule `buildTimelineSections` buckets a task into "earlier" by), not a
 * fresh definition.
 */
describe("splitEarlierItems", () => {
  it("an empty set has neither earlier nor current items", () => {
    expect(splitEarlierItems([])).toEqual({ hasEarlierItems: false, hasCurrentItems: false });
  });

  it("all items on or after today (dayDiff >= 0): current only", () => {
    expect(splitEarlierItems([{ dayDiff: 0 }, { dayDiff: 1 }, { dayDiff: 30 }])).toEqual({
      hasEarlierItems: false,
      hasCurrentItems: true,
    });
  });

  it("all items before today (dayDiff < 0): earlier only — the exact shape a screen with only overdue tasks left produces", () => {
    expect(splitEarlierItems([{ dayDiff: -1 }, { dayDiff: -5 }])).toEqual({
      hasEarlierItems: true,
      hasCurrentItems: false,
    });
  });

  it("a mix of both reports both", () => {
    expect(splitEarlierItems([{ dayDiff: -2 }, { dayDiff: 0 }, { dayDiff: 3 }])).toEqual({
      hasEarlierItems: true,
      hasCurrentItems: true,
    });
  });

  it("dayDiff === 0 (today) counts as current, not earlier", () => {
    expect(splitEarlierItems([{ dayDiff: 0 }])).toEqual({
      hasEarlierItems: false,
      hasCurrentItems: true,
    });
  });
});

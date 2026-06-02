import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  buildTimelineSections,
  findSectionKeyForDayKey,
} from "@/lib/timeline/buildTimelineSections";
import type { TodoItemType } from "@/types";

function makeTodo(id: string, dueIso: string, overrides: Partial<TodoItemType> = {}): TodoItemType {
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
    ...overrides,
  };
}

const baseArgs = {
  locale: "en-US",
  timeZone: "UTC",
  todayLabel: "Today",
  tomorrowLabel: "Tomorrow",
};

describe("buildTimelineSections", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    // Tuesday 2026-06-02, noon UTC.
    vi.setSystemTime(new Date("2026-06-02T12:00:00.000Z"));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("orders buckets Earlier → Today → Tomorrow → +2..+6 → Rest of month → future months", () => {
    const todos = [
      makeTodo("past", "2026-05-30T09:00:00.000Z"),
      makeTodo("today", "2026-06-02T15:00:00.000Z"),
      makeTodo("plus3", "2026-06-05T10:00:00.000Z"),
      makeTodo("restOfMonth", "2026-06-12T10:00:00.000Z"),
      makeTodo("nextMonth", "2026-07-15T10:00:00.000Z"),
      makeTodo("twoMonths", "2026-08-20T10:00:00.000Z"),
    ];

    const sections = buildTimelineSections({
      ...baseArgs,
      todos,
      futureOnly: false,
      placesEarlierBeforeToday: true,
    });

    // Earlier first, collapsible, targets yesterday.
    expect(sections[0]).toMatchObject({
      kind: "earlier",
      label: "Earlier",
      collapsible: true,
      targetDayKey: "2026-06-01",
    });
    expect(sections[0].todos.map((t) => t.id)).toEqual(["past"]);

    // Today bucket.
    const today = sections.find((s) => s.key === "2026-06-02");
    expect(today).toMatchObject({ kind: "day", label: "Today", dayDiff: 0, targetDayKey: "2026-06-02" });
    expect(today?.todos.map((t) => t.id)).toEqual(["today"]);

    // Tomorrow bucket (empty drop target still rendered).
    expect(sections.find((s) => s.key === "2026-06-03")).toMatchObject({
      label: "Tomorrow",
      dayDiff: 1,
      todos: [],
    });

    // +2..+6 individual day buckets exist; the +3 task lands in 06-05.
    ["2026-06-04", "2026-06-05", "2026-06-06", "2026-06-07", "2026-06-08"].forEach((key) => {
      expect(sections.some((s) => s.key === key)).toBe(true);
    });
    expect(sections.find((s) => s.key === "2026-06-05")?.todos.map((t) => t.id)).toEqual(["plus3"]);

    // Rest of June: day +7 horizon (06-09) onward, in the current month.
    const rest = sections.find((s) => s.kind === "rest");
    expect(rest).toMatchObject({ targetDayKey: "2026-06-09" });
    expect(rest?.label).toContain("June");
    expect(rest?.todos.map((t) => t.id)).toEqual(["restOfMonth"]);

    // Future months run through at least December; July/August hold their tasks.
    const july = sections.find((s) => s.key === "month-24319");
    expect(july).toMatchObject({ targetDayKey: "2026-07-01" });
    expect(july?.label).toContain("July");
    expect(july?.todos.map((t) => t.id)).toEqual(["nextMonth"]);
    expect(sections.find((s) => s.kind === "month" && s.label.includes("August"))?.todos.map((t) => t.id)).toEqual([
      "twoMonths",
    ]);
    expect(sections.some((s) => s.kind === "month" && s.label.includes("December"))).toBe(true);
  });

  it("omits Earlier and never surfaces the past when futureOnly is set", () => {
    const todos = [
      makeTodo("past", "2026-05-30T09:00:00.000Z"),
      makeTodo("today", "2026-06-02T15:00:00.000Z"),
    ];

    const sections = buildTimelineSections({
      ...baseArgs,
      todos,
      futureOnly: true,
      placesEarlierBeforeToday: false,
    });

    expect(sections.some((s) => s.kind === "earlier")).toBe(false);
    const allTodoIds = sections.flatMap((s) => s.todos.map((t) => t.id));
    expect(allTodoIds).not.toContain("past");
    expect(allTodoIds).toContain("today");
  });

  it("makes Rest of month non-droppable once the +7 horizon rolls into next month", () => {
    vi.setSystemTime(new Date("2026-06-28T12:00:00.000Z")); // horizon = 2026-07-05

    const sections = buildTimelineSections({
      ...baseArgs,
      todos: [],
      futureOnly: false,
      placesEarlierBeforeToday: true,
    });

    // No remaining June days at/after the horizon → Rest-of-month is dropped.
    expect(sections.some((s) => s.kind === "rest")).toBe(false);
    // The +2..+6 day buckets cross into July as individual day sections.
    expect(sections.some((s) => s.key === "2026-07-01")).toBe(true);
  });

  it("findSectionKeyForDayKey resolves a day inside an aggregate bucket", () => {
    const todos = [makeTodo("restOfMonth", "2026-06-12T10:00:00.000Z")];
    const sections = buildTimelineSections({
      ...baseArgs,
      todos,
      futureOnly: false,
      placesEarlierBeforeToday: true,
    });

    // A direct day bucket resolves to itself.
    expect(findSectionKeyForDayKey(sections, "2026-06-05", "UTC")).toBe("2026-06-05");
    // A day folded into "Rest of June" resolves to that aggregate section.
    const rest = sections.find((s) => s.kind === "rest");
    expect(findSectionKeyForDayKey(sections, "2026-06-12", "UTC")).toBe(rest?.key);
  });
});

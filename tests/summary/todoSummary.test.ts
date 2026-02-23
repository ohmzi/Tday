import {
  buildFallbackSummary,
  buildReadableTaskSummary,
  buildSummaryTaskCandidates,
  filterTodosForSummaryMode,
} from "@/lib/todoSummary";
import { TodoItemType } from "@/types";

function makeTodo(
  id: string,
  params: Partial<TodoItemType> = {},
): TodoItemType {
  const baseNow = new Date("2026-02-22T10:00:00.000Z");
  return {
    id,
    title: `Task ${id}`,
    description: null,
    pinned: false,
    createdAt: baseNow,
    order: 0,
    priority: "Low",
    dtstart: new Date("2026-02-22T09:00:00.000Z"),
    durationMinutes: 60,
    due: new Date("2026-02-22T11:00:00.000Z"),
    rrule: null,
    timeZone: "UTC",
    userID: "user-1",
    completed: false,
    exdates: [],
    instances: [],
    listID: null,
    ...params,
  };
}

describe("todoSummary mode filtering", () => {
  const now = new Date("2026-02-22T10:00:00.000Z");
  const todos = [
    makeTodo("today-overdue", {
      due: new Date("2026-02-22T06:00:00.000Z"),
      dtstart: new Date("2026-02-22T05:00:00.000Z"),
      priority: "Low",
    }),
    makeTodo("today", {
      due: new Date("2026-02-22T11:00:00.000Z"),
      dtstart: new Date("2026-02-22T08:00:00.000Z"),
      priority: "Low",
    }),
    makeTodo("scheduled", {
      due: new Date("2026-02-24T11:00:00.000Z"),
      dtstart: new Date("2026-02-24T08:00:00.000Z"),
      priority: "Low",
    }),
    makeTodo("priority-medium", {
      due: new Date("2026-02-23T11:00:00.000Z"),
      dtstart: new Date("2026-02-23T08:00:00.000Z"),
      priority: "Medium",
    }),
    makeTodo("priority-high", {
      due: new Date("2026-02-23T12:00:00.000Z"),
      dtstart: new Date("2026-02-23T08:00:00.000Z"),
      priority: "High",
    }),
    makeTodo("past", {
      due: new Date("2026-02-20T11:00:00.000Z"),
      dtstart: new Date("2026-02-20T08:00:00.000Z"),
      priority: "Low",
    }),
    makeTodo("done", {
      completed: true,
      due: new Date("2026-02-24T11:00:00.000Z"),
    }),
  ];

  test("today mode uses due + start window semantics", () => {
    const filtered = filterTodosForSummaryMode({
      mode: "today",
      todos,
      timeZone: "UTC",
      now,
    });
    expect(filtered.map((todo) => todo.id)).toEqual(["today-overdue", "today"]);
  });

  test("scheduled mode includes all of today and future tasks", () => {
    const filtered = filterTodosForSummaryMode({
      mode: "scheduled",
      todos,
      timeZone: "UTC",
      now,
    });
    expect(filtered.map((todo) => todo.id)).toEqual([
      "today-overdue",
      "today",
      "priority-medium",
      "priority-high",
      "scheduled",
    ]);
  });

  test("all mode includes past, present, and future tasks", () => {
    const filtered = filterTodosForSummaryMode({
      mode: "all",
      todos,
      timeZone: "UTC",
      now,
    });
    expect(filtered.map((todo) => todo.id)).toEqual([
      "past",
      "today-overdue",
      "today",
      "priority-medium",
      "priority-high",
      "scheduled",
    ]);
  });

  test("priority mode includes medium and high priority semantics", () => {
    const filtered = filterTodosForSummaryMode({
      mode: "priority",
      todos,
      timeZone: "UTC",
      now,
    });
    expect(filtered.map((todo) => todo.id)).toEqual([
      "priority-medium",
      "priority-high",
    ]);
  });

  test("fallback summary is human-readable and deterministic", () => {
    const filtered = filterTodosForSummaryMode({
      mode: "today",
      todos,
      timeZone: "UTC",
      now,
    });
    const summary = buildFallbackSummary({
      mode: "today",
      todos: filtered,
      timeZone: "UTC",
      now,
    });
    expect(summary).toContain("Start with");
    expect(summary).toContain("due today");
  });

  test("all mode prioritizes near-term due tasks over far future high priority", () => {
    const candidates = buildSummaryTaskCandidates(
      [
        makeTodo("far-high", {
          due: new Date("2026-03-22T11:00:00.000Z"),
          dtstart: new Date("2026-03-22T08:00:00.000Z"),
          priority: "High",
        }),
        makeTodo("today-low", {
          due: new Date("2026-02-22T21:00:00.000Z"),
          dtstart: new Date("2026-02-22T20:00:00.000Z"),
          priority: "Low",
        }),
      ],
      {
        mode: "all",
        timeZone: "UTC",
        now,
      },
    );

    expect(candidates[0]?.title).toBe("Task today-low");
    expect(candidates[1]?.title).toBe("Task far-high");
  });

  test("priority mode keeps all qualifying tasks for summary coverage", () => {
    const candidates = buildSummaryTaskCandidates(
      [
        makeTodo("overdue-1", {
          due: new Date("2026-02-20T09:00:00.000Z"),
          dtstart: new Date("2026-02-20T08:00:00.000Z"),
          priority: "High",
        }),
        makeTodo("overdue-2", {
          due: new Date("2026-02-21T09:00:00.000Z"),
          dtstart: new Date("2026-02-21T08:00:00.000Z"),
          priority: "Medium",
        }),
        makeTodo("overdue-3", {
          due: new Date("2026-02-21T23:00:00.000Z"),
          dtstart: new Date("2026-02-21T22:00:00.000Z"),
          priority: "High",
        }),
        makeTodo("future-1", {
          due: new Date("2026-02-27T08:00:00.000Z"),
          dtstart: new Date("2026-02-27T07:00:00.000Z"),
          priority: "Medium",
        }),
        makeTodo("future-2", {
          due: new Date("2026-02-27T20:00:00.000Z"),
          dtstart: new Date("2026-02-27T19:00:00.000Z"),
          priority: "High",
        }),
        makeTodo("future-3", {
          due: new Date("2026-03-01T09:00:00.000Z"),
          dtstart: new Date("2026-03-01T08:00:00.000Z"),
          priority: "High",
        }),
      ],
      {
        mode: "priority",
        timeZone: "UTC",
        now,
      },
    );

    expect(candidates.length).toBe(6);
    expect(candidates.some((candidate) => (candidate.dueDayDelta ?? -1) < 0)).toBe(true);
    expect(candidates.some((candidate) => (candidate.dueDayDelta ?? -1) >= 0)).toBe(true);
  });

  test("readable summary groups repeated due dates for next tasks", () => {
    const summary = buildReadableTaskSummary({
      startTask: {
        id: "T1",
        title: "clean the car",
        priorityLabel: "high",
        dueLabel: "due tonight",
        dueEpochMs: 1,
        dueDayKey: "2026-02-22",
        dueDayTarget: "today",
        dueWindowPhrase: "at night",
      },
      thenTasks: [
        {
          id: "T2",
          title: "go fishing",
          priorityLabel: "medium",
          dueLabel: "due on 27th Feb in the morning",
          dueEpochMs: 2,
          dueDayKey: "2026-02-27",
          dueDayTarget: "on 27th Feb",
          dueWindowPhrase: "in the morning",
        },
        {
          id: "T3",
          title: "return book",
          priorityLabel: "high",
          dueLabel: "due on 27th Feb at night",
          dueEpochMs: 3,
          dueDayKey: "2026-02-27",
          dueDayTarget: "on 27th Feb",
          dueWindowPhrase: "at night",
        },
      ],
    });

    expect(summary).toContain("go fishing in the morning and return book at night (both due on 27th Feb)");
    expect(summary.match(/due on 27th Feb/g)?.length).toBe(1);
  });

  test("due labels include time-of-day windows for planning", () => {
    const candidates = buildSummaryTaskCandidates(
      [
        makeTodo("morning", {
          due: new Date("2026-02-22T08:00:00.000Z"),
          dtstart: new Date("2026-02-22T07:00:00.000Z"),
        }),
        makeTodo("afternoon", {
          due: new Date("2026-02-22T15:00:00.000Z"),
          dtstart: new Date("2026-02-22T14:00:00.000Z"),
        }),
        makeTodo("night", {
          due: new Date("2026-02-22T21:00:00.000Z"),
          dtstart: new Date("2026-02-22T20:00:00.000Z"),
        }),
      ],
      {
        mode: "all",
        timeZone: "UTC",
        now,
      },
    );

    expect(candidates.find((candidate) => candidate.title === "Task morning")?.dueLabel)
      .toContain("morning");
    expect(candidates.find((candidate) => candidate.title === "Task afternoon")?.dueLabel)
      .toContain("afternoon");
    expect(candidates.find((candidate) => candidate.title === "Task night")?.dueLabel)
      .toMatch(/tonight|night/);
  });
});

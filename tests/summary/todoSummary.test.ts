import {
  buildFallbackSummary,
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
    expect(filtered.map((todo) => todo.id)).toEqual(["today"]);
  });

  test("scheduled mode excludes overdue items", () => {
    const filtered = filterTodosForSummaryMode({
      mode: "scheduled",
      todos,
      timeZone: "UTC",
      now,
    });
    expect(filtered.map((todo) => todo.id)).toEqual([
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
    expect(summary).toContain("- Start with");
  });
});

import { describe, expect, it } from "vitest";
import {
  buildFloaterSearchResultHref,
  buildScheduledSearchResultHref,
} from "@/lib/todo/searchResultHref";
import { isFocusedTodoRow } from "@/lib/todo/todo-id";

const NOW = new Date("2026-04-10T15:00:00.000Z");
const TZ = "UTC";

describe("buildScheduledSearchResultHref", () => {
  it("opens a listed task on its custom list with the bare task id", () => {
    const href = buildScheduledSearchResultHref(
      {
        id: "task-1:1775833200000",
        due: new Date("2026-04-12T09:00:00.000Z"),
        completed: false,
        listID: "list-7",
      },
      { timeZone: TZ, now: NOW },
    );

    expect(href).toBe("/app/list/list-7?focusTask=task-1");
  });

  it("encodes the list id in the path", () => {
    const href = buildScheduledSearchResultHref(
      { id: "t", due: NOW, completed: false, listID: "a/b c" },
      { timeZone: TZ, now: NOW },
    );

    expect(href).toBe("/app/list/a%2Fb%20c?focusTask=t");
  });

  it("opens an unlisted task due today on Today", () => {
    const href = buildScheduledSearchResultHref(
      { id: "task-2", due: new Date("2026-04-10T20:00:00.000Z"), completed: false },
      { timeZone: TZ, now: NOW },
    );

    expect(href).toBe("/app/today?focusTask=task-2");
  });

  it("opens an unlisted overdue task on Overdue with its day", () => {
    const href = buildScheduledSearchResultHref(
      { id: "task-3", due: new Date("2026-04-07T09:00:00.000Z"), completed: false },
      { timeZone: TZ, now: NOW },
    );

    expect(href).toBe("/app/overdue?focusTask=task-3&focusDate=2026-04-07");
  });

  it("opens an unlisted upcoming task on Scheduled with its day", () => {
    const href = buildScheduledSearchResultHref(
      { id: "task-4:123", due: new Date("2026-04-20T09:00:00.000Z"), completed: false },
      { timeZone: TZ, now: NOW },
    );

    expect(href).toBe("/app/scheduled?focusTask=task-4&focusDate=2026-04-20");
  });

  it("opens an unlisted completed task on All with its day", () => {
    const href = buildScheduledSearchResultHref(
      { id: "task-5", due: new Date("2026-04-07T09:00:00.000Z"), completed: true },
      { timeZone: TZ, now: NOW },
    );

    expect(href).toBe("/app/todo?focusTask=task-5&focusDate=2026-04-07");
  });

  it("classifies the day in the user's timezone, not UTC", () => {
    // 02:30 UTC on the 11th is still the 10th in New York, so this is due today.
    const href = buildScheduledSearchResultHref(
      { id: "task-6", due: new Date("2026-04-11T02:30:00.000Z"), completed: false },
      { timeZone: "America/New_York", now: NOW },
    );

    expect(href).toBe("/app/today?focusTask=task-6");
  });
});

describe("buildFloaterSearchResultHref", () => {
  it("opens a listed floater on its Anytime list with the task id", () => {
    const href = buildFloaterSearchResultHref({ id: "float-1", listID: "fl-2" });

    expect(href).toBe("/app/floater-list/fl-2?focusTask=float-1");
  });

  it("opens an unlisted floater on the Anytime feed", () => {
    const href = buildFloaterSearchResultHref({ id: "float-3", listID: null });

    expect(href).toBe("/app/floater?focusTask=float-3");
  });
});

describe("isFocusedTodoRow", () => {
  it("matches a composite row id against the bare focus id", () => {
    expect(isFocusedTodoRow("task-1", "task-1:1775833200000")).toBe(true);
    expect(isFocusedTodoRow("task-1", "task-1")).toBe(true);
  });

  it("does not match other rows or a missing focus", () => {
    expect(isFocusedTodoRow("task-1", "task-2:1")).toBe(false);
    expect(isFocusedTodoRow(null, "task-1")).toBe(false);
    expect(isFocusedTodoRow("", "task-1")).toBe(false);
  });
});

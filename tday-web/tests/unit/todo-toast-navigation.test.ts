import { describe, expect, it } from "vitest";
import {
  TODO_FOCUS_MODE_DELETED,
  buildScheduledFocusPath,
  buildTodoFocusPath,
  getTodoDayKey,
  getTodoDateSectionId,
  getTodoFocusElementId,
  isTodoFocusDateKey,
} from "@/lib/todoToastNavigation";

describe("todoToastNavigation", () => {
  it("builds a localized day key from the provided timezone", () => {
    const due = new Date("2026-04-03T02:30:00.000Z");

    expect(getTodoDayKey(due, "America/Toronto")).toBe("2026-04-02");
    expect(getTodoDayKey(due, "UTC")).toBe("2026-04-03");
  });

  it("builds a task focus path with the task id and date key", () => {
    const path = buildTodoFocusPath({
      id: "todo-1:undefined",
      due: new Date("2026-04-03T14:30:00.000Z"),
    }, "UTC");

    expect(path).toBe(
      "/app/todo?focusTask=todo-1%3Aundefined&focusDate=2026-04-03",
    );
  });

  it("builds a scheduled delete path for the deleted date", () => {
    const path = buildScheduledFocusPath({
      due: new Date("2026-04-03T14:30:00.000Z"),
    }, "UTC");

    expect(path).toBe(
      `/app/scheduled?focusDate=2026-04-03&focusMode=${TODO_FOCUS_MODE_DELETED}`,
    );
  });

  it("creates stable DOM ids for focused tasks and dates", () => {
    expect(getTodoFocusElementId("todo-1:undefined")).toBe(
      "todo-focus-todo-1%3Aundefined",
    );
    expect(getTodoDateSectionId("2026-04-03")).toBe("todo-date-2026-04-03");
    expect(isTodoFocusDateKey("2026-04-03")).toBe(true);
    expect(isTodoFocusDateKey("not-a-date")).toBe(false);
  });
});

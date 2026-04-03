import { describe, expect, it } from "vitest";
import { moveTodoToDay } from "@/lib/moveTodoToDay";

describe("moveTodoToDay", () => {
  it("preserves the due time and duration in the target timezone", () => {
    const movedTodo = moveTodoToDay(
      {
        dtstart: new Date("2026-04-03T13:30:00.000Z"),
        due: new Date("2026-04-03T15:00:00.000Z"),
        durationMinutes: 90,
      },
      "2026-04-07",
      "America/Toronto",
    );

    expect(movedTodo.durationMinutes).toBe(90);
    expect(movedTodo.due.toISOString()).toBe("2026-04-07T15:00:00.000Z");
    expect(movedTodo.dtstart.toISOString()).toBe("2026-04-07T13:30:00.000Z");
  });

  it("anchors overnight tasks by the due day while keeping duration", () => {
    const movedTodo = moveTodoToDay(
      {
        dtstart: new Date("2026-04-03T02:00:00.000Z"),
        due: new Date("2026-04-03T06:00:00.000Z"),
        durationMinutes: 240,
      },
      "2026-04-10",
      "America/Toronto",
    );

    expect(movedTodo.due.toISOString()).toBe("2026-04-10T06:00:00.000Z");
    expect(movedTodo.dtstart.toISOString()).toBe("2026-04-10T02:00:00.000Z");
    expect(movedTodo.durationMinutes).toBe(240);
  });
});

import { describe, expect, it } from "vitest";
import { moveTodoToDay } from "@/lib/moveTodoToDay";

describe("moveTodoToDay", () => {
  it("preserves due time-of-day when moving to another calendar day (UTC)", () => {
    const movedTodo = moveTodoToDay(
      { due: new Date("2026-04-03T15:00:00.000Z") },
      "2026-04-07",
      "UTC",
    );

    expect(movedTodo.due.toISOString()).toBe("2026-04-07T15:00:00.000Z");
  });

  it("preserves an earlier due time when moving (UTC)", () => {
    const movedTodo = moveTodoToDay(
      { due: new Date("2026-04-03T02:30:00.000Z") },
      "2026-04-10",
      "UTC",
    );

    expect(movedTodo.due.toISOString()).toBe("2026-04-10T02:30:00.000Z");
  });
});

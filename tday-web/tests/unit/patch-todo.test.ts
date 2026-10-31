import { afterEach, describe, expect, it, vi } from "vitest";

const patchMock = vi.fn(() => Promise.resolve({}));

vi.mock("@/lib/api-client", () => ({
  api: {
    PATCH: (...args: unknown[]) => patchMock(...args),
  },
}));

import { patchTodo } from "@/lib/todo/patch-todo";
import type { TodoItemTypeWithDateChecksum } from "@/lib/todo/patch-todo";

function makeRescheduledTodo(
  overrides: Partial<TodoItemTypeWithDateChecksum> = {},
): TodoItemTypeWithDateChecksum {
  const oldDue = new Date("2026-06-02T15:00:00.000Z");
  const newDue = new Date("2026-06-09T15:00:00.000Z");
  return {
    id: "task-1",
    title: "Buy milk",
    description: null, // tasks created without a description carry null
    pinned: false,
    createdAt: oldDue,
    order: 0,
    priority: "Low",
    due: newDue,
    rrule: null,
    timeZone: "UTC",
    userID: "u1",
    completed: false,
    exdates: [],
    instanceDate: null,
    listID: null,
    dateRangeChecksum: oldDue.toISOString(),
    rruleChecksum: null,
    ...overrides,
  };
}

describe("patchTodo", () => {
  afterEach(() => {
    patchMock.mockClear();
  });

  it("PATCHes (does not silently skip) when description is null and flags dateChanged", async () => {
    await patchTodo(makeRescheduledTodo());

    expect(patchMock).toHaveBeenCalledTimes(1);
    const body = JSON.parse(patchMock.mock.calls[0][0].body as string);
    expect(body.id).toBe("task-1");
    expect(body.dateChanged).toBe(true);
    expect(new Date(body.due).toISOString()).toBe("2026-06-09T15:00:00.000Z");
  });

  it("sends a newly set rrule with rruleChanged", async () => {
    await patchTodo(
      makeRescheduledTodo({
        due: new Date("2026-06-02T15:00:00.000Z"),
        dateRangeChecksum: "2026-06-02T15:00:00.000Z",
        rrule: "RRULE:FREQ=DAILY",
        rruleChecksum: null,
      }),
    );

    const body = JSON.parse(patchMock.mock.calls[0][0].body as string);
    expect(body.rrule).toBe("RRULE:FREQ=DAILY");
    expect(body.rruleChanged).toBe(true);
  });

  it("sends a null rrule with rruleChanged when a repeat is cleared", async () => {
    await patchTodo(
      makeRescheduledTodo({
        due: new Date("2026-06-02T15:00:00.000Z"),
        dateRangeChecksum: "2026-06-02T15:00:00.000Z",
        rrule: null,
        rruleChecksum: "RRULE:FREQ=DAILY",
      }),
    );

    const body = JSON.parse(patchMock.mock.calls[0][0].body as string);
    expect(body).toHaveProperty("rrule", null);
    expect(body.rruleChanged).toBe(true);
  });

  it("reports an unchanged rrule as not changed", async () => {
    await patchTodo(
      makeRescheduledTodo({
        due: new Date("2026-06-02T15:00:00.000Z"),
        dateRangeChecksum: "2026-06-02T15:00:00.000Z",
        rrule: "RRULE:FREQ=WEEKLY",
        rruleChecksum: "RRULE:FREQ=WEEKLY",
      }),
    );

    const body = JSON.parse(patchMock.mock.calls[0][0].body as string);
    expect(body.rruleChanged).toBe(false);
  });

  it("canonicalizes a recurring composite id before sending", async () => {
    await patchTodo(makeRescheduledTodo({ id: "task-1:1700000000000" }));

    const body = JSON.parse(patchMock.mock.calls[0][0].body as string);
    expect(body.id).toBe("task-1");
  });
});

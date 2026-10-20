// @vitest-environment jsdom

import type { ReactNode } from "react";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

const getMock = vi.fn();
const patchMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  api: {
    GET: (...args: unknown[]) => getMock(...args),
    PATCH: (...args: unknown[]) => patchMock(...args),
    DELETE: vi.fn(),
  },
}));

import { useList } from "@/features/list/query/get-list-todos";
import { patchTodo } from "@/lib/todo/patch-todo";
import { runBulkFanOut } from "@/lib/bulk/run-bulk-fan-out";
import { effectiveBulkSelection } from "@/lib/bulk/bulk-selection-policy";
import type { TodoItemType } from "@/types";

/**
 * The custom-list screen (`/app/list/:id`) is the one screen whose rows do NOT
 * come from `/api/todo`. Its payload is `ListTodoDto`
 * (shared/src/commonMain/kotlin/com/ohmz/tday/shared/model/ListModels.kt), and
 * these fixtures are built from that field set on purpose: the shared
 * `buildTodo()` helper always supplies `rrule: null`, which is exactly the shape
 * these rows never had, so it could not see any of the bugs below.
 */
const LIST_DTO_ROW = {
  id: "todo-1",
  title: "Water the plants",
  description: null,
  priority: "Low",
  due: "2026-08-20T09:00:00",
  completed: false,
  order: 1,
};

/** The same endpoint after it learned to send recurrence. */
const LIST_DTO_ROW_V2 = {
  ...LIST_DTO_ROW,
  rrule: null,
  listID: "list-a",
};

function wrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };
}

function renderList(rows: unknown[]) {
  getMock.mockResolvedValue({ todos: rows });
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return renderHook(() => useList({ id: "list-a" }), {
    wrapper: wrapper(queryClient),
  });
}

const isRecurringRow = (row: TodoItemType) =>
  Boolean(row.rrule) || row.recurrenceUnknown === true;

describe("rows from the custom-list screen", () => {
  beforeEach(() => {
    getMock.mockReset();
    patchMock.mockReset();
  });

  it("normalises the narrow list payload into a full todo row", async () => {
    const { result } = renderList([LIST_DTO_ROW_V2]);
    await waitFor(() => expect(result.current.listTodos).toHaveLength(1));

    const row = result.current.listTodos[0];
    // `undefined` here is what made `todoSchema.safeParse` reject the row and
    // `patchTodo` return without sending anything.
    expect(row.rrule).toBeNull();
    expect(row.listID).toBe("list-a");
    expect(row.pinned).toBe(false);
    expect(row.createdAt).toBeInstanceOf(Date);
    expect(Number.isNaN(row.createdAt.getTime())).toBe(false);
  });

  it("lets a bulk move actually reach the network", async () => {
    const { result } = renderList([LIST_DTO_ROW_V2]);
    await waitFor(() => expect(result.current.listTodos).toHaveLength(1));
    const row = result.current.listTodos[0];

    const outcome = await runBulkFanOut([row], (item) =>
      patchTodo(
        {
          ...item,
          dateRangeChecksum: item.due.toISOString(),
          rruleChecksum: item.rrule,
        },
        { listID: "list-b", instanceDate: null },
      ),
    );

    // Before the fix this was `{ total: 1, failed: 0 }` with zero PATCH calls:
    // a silent success for a request that was never sent.
    expect(patchMock).toHaveBeenCalledTimes(1);
    expect(outcome).toEqual({ total: 1, failed: 0 });
  });

  it("keeps a repeating task in a list out of delete, priority and move", async () => {
    const { result } = renderList([
      { ...LIST_DTO_ROW_V2, id: "todo-r", rrule: "FREQ=DAILY" },
      LIST_DTO_ROW_V2,
    ]);
    await waitFor(() => expect(result.current.listTodos).toHaveLength(2));

    for (const action of ["delete", "priority", "move"] as const) {
      const effective = effectiveBulkSelection(
        action,
        result.current.listTodos,
        isRecurringRow,
      );
      expect(effective.map((r) => r.id)).toEqual(["todo-1:undefined"]);
    }
  });

  it("treats a row from a server that never states recurrence as repeating", async () => {
    // An older backend, whose ListTodoDto has no `rrule` key at all. Deciding
    // "not recurring" from a missing field is how a bulk delete would have
    // destroyed whole series on this screen.
    const { result } = renderList([LIST_DTO_ROW]);
    await waitFor(() => expect(result.current.listTodos).toHaveLength(1));

    const row = result.current.listTodos[0];
    expect(row.recurrenceUnknown).toBe(true);
    expect(isRecurringRow(row)).toBe(true);
    expect(
      effectiveBulkSelection("delete", [row], isRecurringRow),
    ).toHaveLength(0);
  });
});

describe("a request the fan-out never sent", () => {
  beforeEach(() => patchMock.mockReset());

  it("counts as a failure instead of a silent success", async () => {
    const unpatchable = {
      id: "todo-1:null",
      title: "Water the plants",
      description: null,
      priority: "Low",
      due: new Date("2026-08-20T09:00:00.000Z"),
      completed: false,
      order: 1,
      listID: null,
      instanceDate: null,
      // rrule deliberately absent — the shape todoSchema rejects.
    } as unknown as TodoItemType;

    const outcome = await runBulkFanOut([unpatchable], (row) =>
      patchTodo(
        { ...row, dateRangeChecksum: "x", rruleChecksum: null },
        { listID: "list-b" },
      ),
    );

    expect(patchMock).not.toHaveBeenCalled();
    expect(outcome).toEqual({ total: 1, failed: 1 });
  });
});

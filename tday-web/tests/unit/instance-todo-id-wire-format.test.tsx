// @vitest-environment jsdom

// PATCH/DELETE /api/todo/instance deserialize TodoInstancePatchRequest /
// TodoInstanceDeleteRequest, which both declare `todoId` — no default, no
// @JsonNames alias. `ignoreUnknownKeys = true` means a body that names the todo
// `id` drops it silently, `todoId` is then missing, and Ktor 400s before the
// handler ever runs. Three web hooks were sending `id`; these pin the key name
// for every instance hook so the endpoint can't go dark again.
//
// Deliberately silent on how instanceDate is encoded — that contract is pinned
// separately — so these assertions hold either way.

import type { ReactNode } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";

const patchMock = vi.fn();
const deleteMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  api: {
    PATCH: (...args: unknown[]) => patchMock(...args),
    DELETE: (...args: unknown[]) => deleteMock(...args),
  },
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

// The delete hook stages the request behind an undoable toast; commit it
// straight away so the DELETE body is observable.
vi.mock("@/hooks/use-todo-action-toast", () => ({
  useTodoActionToast: () => ({
    showTodoDeletedToast: (
      _todo: TodoItemType,
      { commit }: { commit: () => void; undo: () => void },
    ) => commit(),
    showTodoCompletedToast: vi.fn(),
  }),
}));

import { useEditTodoInstance } from "@/features/todayTodos/query/update-todo-instance";
import { useEditListTodoInstance } from "@/features/list/query/update-list-todo-instance";
import { useEditCalendarTodoInstance } from "@/features/calendar/query/update-calendar-todo-instance";
import { useDeleteCalendarInstanceTodo } from "@/features/calendar/query/delete-calendar-instance-todo";

// The Today hook's onMutate maps over the cached ["todo"] rows without a
// cold-cache default, so seed the caches it touches — these tests are about the
// request body, not optimistic-update resilience.
function Wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: {
      mutations: { retry: false },
      queries: { retry: false },
    },
  });
  client.setQueryData(["todo"], [buildInstanceTodo()]);
  client.setQueryData(["todoTimeline"], [buildInstanceTodo()]);
  client.setQueryData(["list"], [buildInstanceTodo()]);

  return (
    <MemoryRouter initialEntries={["/en/app/todo"]}>
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    </MemoryRouter>
  );
}

const INSTANCE_DATE = new Date("2026-03-27T15:42:00.000Z");

// A ghost row: `id` carries the instance suffix the canonical id is cut from.
function buildInstanceTodo(): TodoItemType {
  return {
    id: "todo-1:1774626120000",
    title: "Water the plants",
    description: null,
    pinned: false,
    createdAt: new Date("2026-03-01T09:00:00.000Z"),
    order: 1,
    priority: "Low",
    due: new Date("2026-03-27T16:00:00.000Z"),
    rrule: "FREQ=DAILY",
    timeZone: "UTC",
    userID: "user-1",
    completed: false,
    exdates: [],
    instanceDate: INSTANCE_DATE,
    listID: null,
    instances: [],
  };
}

function sentBody(mock: ReturnType<typeof vi.fn>): Record<string, unknown> {
  expect(mock).toHaveBeenCalledTimes(1);
  const call = mock.mock.calls[0][0] as { url: string; body: string };
  expect(call.url).toBe("/api/todo/instance");
  return JSON.parse(call.body) as Record<string, unknown>;
}

describe("todo instance mutations name the todo id `todoId`", () => {
  afterEach(() => {
    patchMock.mockReset();
    deleteMock.mockReset();
  });

  it.each([
    ["today", useEditTodoInstance],
    ["list", useEditListTodoInstance],
  ])("PATCHes the canonical id as todoId from the %s hook", async (_name, hook) => {
    patchMock.mockResolvedValue({ message: "instance updated" });

    const { result } = renderHook(() => hook(undefined), { wrapper: Wrapper });

    act(() => {
      result.current.editTodoInstanceMutateFn(buildInstanceTodo());
    });

    await waitFor(() => {
      expect(result.current.editTodoInstanceStatus).toBe("success");
    });

    const body = sentBody(patchMock);
    expect(body.todoId).toBe("todo-1");
    expect(body).not.toHaveProperty("id");
    expect(body.title).toBe("Water the plants");
  });

  it("PATCHes the canonical id as todoId from the calendar hook", async () => {
    patchMock.mockResolvedValue({ message: "instance updated" });

    const { result } = renderHook(() => useEditCalendarTodoInstance(), {
      wrapper: Wrapper,
    });

    act(() => {
      result.current.editCalendarTodoInstance(buildInstanceTodo());
    });

    await waitFor(() => {
      expect(result.current.editTodoInstanceStatus).toBe("success");
    });

    const body = sentBody(patchMock);
    expect(body.todoId).toBe("todo-1");
    expect(body).not.toHaveProperty("id");
  });

  it("DELETEs the canonical id as todoId from the calendar hook", async () => {
    deleteMock.mockResolvedValue({ message: "instance deleted" });

    const { result } = renderHook(() => useDeleteCalendarInstanceTodo(), {
      wrapper: Wrapper,
    });

    act(() => {
      result.current.deleteInstanceMutate(buildInstanceTodo());
    });

    await waitFor(() => {
      expect(deleteMock).toHaveBeenCalledTimes(1);
    });

    const body = sentBody(deleteMock);
    expect(body.todoId).toBe("todo-1");
    expect(body).not.toHaveProperty("id");
  });
});

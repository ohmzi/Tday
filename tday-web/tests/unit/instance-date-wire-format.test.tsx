// @vitest-environment jsdom

// Regression guard: `instanceDate` goes over the wire as an ISO-8601 string.
// These mutations used to send `.getTime()` (epoch millis). Ktor's JSON is
// lenient, so an unquoted number still decoded into the `String?` field as the
// literal digits — the request only failed later, when the backend's
// `parseDueMinute` could not parse "1774624920000" and the route raised
// BadRequest. That made every recurring-task check-off 400 in Server Mode.

import type { ReactNode } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { CompletedTodoItemType, TodoItemType } from "@/types";

const patchMock = vi.fn(() => Promise.resolve({}));
const deleteMock = vi.fn(() => Promise.resolve({}));

vi.mock("@/lib/api-client", () => ({
  api: {
    PATCH: (...args: unknown[]) => patchMock(...args),
    DELETE: (...args: unknown[]) => deleteMock(...args),
  },
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

// The complete/delete flows are delayed-commit: the request only fires when the
// undo toast closes. Commit immediately so the body is observable.
vi.mock("@/hooks/use-todo-action-toast", () => ({
  useTodoActionToast: () => ({
    showTodoCompletedToast: ({ commit }: { commit: () => void }) => commit(),
    showTodoDeletedToast: (
      _todo: unknown,
      { commit }: { commit: () => void },
    ) => commit(),
  }),
}));

import { useCompleteTodo } from "@/features/todayTodos/query/complete-todo";
import { useCompleteCalendarTodo } from "@/features/calendar/query/complete-calendar-todo";
import { useCompleteCalendarTodoInstance } from "@/features/calendar/query/complete-calendar-todo-instance";
import { useCompleteListTodo } from "@/features/list/query/complete-list-todo";
import { useUnCompleteTodo } from "@/features/completed/query/uncomplete-completedTodo";
import { useDeleteCalendarInstanceTodo } from "@/features/calendar/query/delete-calendar-instance-todo";
import { useEditCalendarTodo } from "@/features/calendar/query/update-calendar-todo";

const INSTANCE_DATE = new Date("2026-03-27T15:42:00.000Z");

function createWrapper(seed?: (client: QueryClient) => void) {
  const client = new QueryClient({
    defaultOptions: {
      mutations: { retry: false },
      queries: { retry: false },
    },
  });
  seed?.(client);
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={["/en/app/todo"]}>
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
      </MemoryRouter>
    );
  };
}

function makeRecurringTodo(
  overrides: Partial<TodoItemType> = {},
): TodoItemType {
  return {
    id: "task-1",
    title: "Water the plants",
    description: null,
    pinned: false,
    createdAt: new Date("2026-03-01T09:00:00.000Z"),
    order: 0,
    priority: "Low",
    due: INSTANCE_DATE,
    rrule: "RRULE:FREQ=DAILY;INTERVAL=1",
    timeZone: "UTC",
    userID: "u1",
    completed: false,
    exdates: [],
    instanceDate: INSTANCE_DATE,
    listID: null,
    ...overrides,
  };
}

/** Reads `instanceDate` out of the single request body a mock captured. */
function sentInstanceDate(mock: typeof patchMock | typeof deleteMock) {
  expect(mock).toHaveBeenCalledTimes(1);
  const call = mock.mock.calls[0][0] as { body: string };
  return (JSON.parse(call.body) as { instanceDate: unknown }).instanceDate;
}

/**
 * The contract the backend enforces: an ISO-8601 string that round-trips to the
 * same instant. A number here is exactly the bug this file guards against.
 */
function expectIsoInstanceDate(value: unknown) {
  expect(typeof value).toBe("string");
  expect(value).toBe(INSTANCE_DATE.toISOString());
  expect(new Date(value as string).getTime()).toBe(INSTANCE_DATE.getTime());
}

describe("instanceDate wire format", () => {
  afterEach(() => {
    patchMock.mockClear();
    deleteMock.mockClear();
  });

  it("PATCH /complete from today's list sends an ISO string", async () => {
    const { result } = renderHook(() => useCompleteTodo(), { wrapper: createWrapper() });

    act(() => result.current.completeMutateFn(makeRecurringTodo()));

    await waitFor(() => expect(patchMock).toHaveBeenCalled());
    expectIsoInstanceDate(sentInstanceDate(patchMock));
  });

  it("PATCH /complete from the calendar sends an ISO string", async () => {
    const { result } = renderHook(() => useCompleteCalendarTodo(), {
      wrapper: createWrapper(),
    });

    act(() =>
      result.current.mutateComplete({ todoItem: makeRecurringTodo() }),
    );

    await waitFor(() => expect(patchMock).toHaveBeenCalled());
    expectIsoInstanceDate(sentInstanceDate(patchMock));
  });

  it("PATCH /complete for a single calendar instance sends an ISO string", async () => {
    const { result } = renderHook(() => useCompleteCalendarTodoInstance(), {
      wrapper: createWrapper(),
    });

    act(() =>
      result.current.mutateComplete({ todoItem: makeRecurringTodo() }),
    );

    await waitFor(() => expect(patchMock).toHaveBeenCalled());
    expectIsoInstanceDate(sentInstanceDate(patchMock));
  });

  it("PATCH /complete from a list sends an ISO string", async () => {
    const { result } = renderHook(() => useCompleteListTodo(), {
      wrapper: createWrapper(),
    });

    act(() => result.current.completeMutateFn(makeRecurringTodo()));

    await waitFor(() => expect(patchMock).toHaveBeenCalled());
    expectIsoInstanceDate(sentInstanceDate(patchMock));
  });

  it("PATCH /uncomplete sends an ISO string", async () => {
    const completed: CompletedTodoItemType = {
      id: "completed-1",
      originalTodoID: "task-1",
      title: "Water the plants",
      createdAt: new Date("2026-03-01T09:00:00.000Z"),
      completedAt: INSTANCE_DATE,
      priority: "Low",
      due: INSTANCE_DATE,
      userID: "u1",
      rrule: "RRULE:FREQ=DAILY;INTERVAL=1",
      instanceDate: INSTANCE_DATE,
    };
    // This hook's onMutate prunes the completed list in place, so the cache has
    // to be populated or it throws before the request is ever built.
    const { result } = renderHook(() => useUnCompleteTodo(), {
      wrapper: createWrapper((client) =>
        client.setQueryData(["completedTodo"], [completed]),
      ),
    });

    act(() => result.current.mutateUnComplete(completed));

    await waitFor(() => expect(patchMock).toHaveBeenCalled());
    expectIsoInstanceDate(sentInstanceDate(patchMock));
  });

  it("DELETE /todo/instance sends an ISO string", async () => {
    const { result } = renderHook(() => useDeleteCalendarInstanceTodo(), {
      wrapper: createWrapper(),
    });

    act(() => result.current.deleteInstanceMutate(makeRecurringTodo()));

    await waitFor(() => expect(deleteMock).toHaveBeenCalled());
    expectIsoInstanceDate(sentInstanceDate(deleteMock));
  });

  it("PATCH /todo from the calendar editor sends an ISO string", async () => {
    const { result } = renderHook(() => useEditCalendarTodo(), {
      wrapper: createWrapper(),
    });

    act(() =>
      result.current.editCalendarTodo({
        ...makeRecurringTodo(),
        dateRangeChecksum: INSTANCE_DATE.toISOString(),
        rruleChecksum: "RRULE:FREQ=DAILY;INTERVAL=1",
      }),
    );

    await waitFor(() => expect(patchMock).toHaveBeenCalled());
    expectIsoInstanceDate(sentInstanceDate(patchMock));
  });

  it("sends null rather than a number when a task does not recur", async () => {
    const { result } = renderHook(() => useCompleteTodo(), { wrapper: createWrapper() });

    act(() =>
      result.current.completeMutateFn(
        makeRecurringTodo({ rrule: null, instanceDate: null }),
      ),
    );

    await waitFor(() => expect(patchMock).toHaveBeenCalled());
    expect(sentInstanceDate(patchMock)).toBeNull();
  });
});

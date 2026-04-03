// @vitest-environment jsdom

import type { ReactNode } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";

const postMock = vi.fn();
const toastMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  api: {
    POST: (...args: unknown[]) => postMock(...args),
  },
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({
    toast: (...args: unknown[]) => toastMock(...args),
  }),
}));

import { useCreateTodo } from "@/features/todayTodos/query/create-todo";
import { useCreateCalendarTodo } from "@/features/calendar/query/create-calendar-todo";

function createWrapper(queryClient?: QueryClient) {
  const client = queryClient ?? new QueryClient({
    defaultOptions: {
      mutations: {
        retry: false,
      },
      queries: {
        retry: false,
      },
    },
  });

  return {
    queryClient: client,
    Wrapper({ children }: { children: ReactNode }) {
      return (
        <MemoryRouter initialEntries={["/en/app/todo"]}>
          <QueryClientProvider client={client}>{children}</QueryClientProvider>
        </MemoryRouter>
      );
    },
  };
}

function buildTodoInput(): TodoItemType {
  return {
    id: "-1",
    title: "Ship web fix",
    description: "verify Today refresh",
    pinned: false,
    createdAt: new Date("2026-04-03T14:00:00.000Z"),
    order: Number.MAX_VALUE,
    priority: "Low",
    dtstart: new Date("2026-04-03T14:00:00.000Z"),
    durationMinutes: 30,
    due: new Date("2026-04-03T14:30:00.000Z"),
    rrule: null,
    timeZone: "UTC",
    userID: "user-1",
    completed: false,
    exdates: [],
    instanceDate: null,
    listID: "list-1",
    instances: [],
  };
}

function buildTodoResponse() {
  return {
    todo: {
      id: "todo-1",
      title: "Ship web fix",
      description: "verify Today refresh",
      pinned: false,
      createdAt: "2026-04-03T14:00:00.000Z",
      order: 7,
      priority: "Low",
      dtstart: "2026-04-03T14:00:00.000Z",
      durationMinutes: 30,
      due: "2026-04-03T14:30:00.000Z",
      rrule: null,
      timeZone: "UTC",
      userID: "user-1",
      completed: false,
      exdates: [],
      instanceDate: null,
      listID: "list-1",
    },
  };
}

describe("todo create mutations", () => {
  afterEach(() => {
    postMock.mockReset();
    toastMock.mockReset();
  });

  it("creates a todo from a cold cache without crashing optimistic updates", async () => {
    postMock.mockResolvedValue(buildTodoResponse());

    const { queryClient, Wrapper } = createWrapper();
    const { result } = renderHook(() => useCreateTodo(), {
      wrapper: Wrapper,
    });

    act(() => {
      result.current.createMutateFn(buildTodoInput());
    });

    await waitFor(() => {
      expect(result.current.createStatus).toBe("success");
    });

    expect(postMock).toHaveBeenCalledTimes(1);
    expect(queryClient.getQueryData<TodoItemType[]>(["todo"])).toEqual([
      expect.objectContaining({
        id: "todo-1:undefined",
        title: "Ship web fix",
        listID: "list-1",
      }),
    ]);
    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])).toEqual([
      expect.objectContaining({
        id: "todo-1:undefined",
        title: "Ship web fix",
      }),
    ]);
    expect(toastMock).toHaveBeenCalledWith(
      expect.objectContaining({
        title: "todo created",
        description: "Tap to open task",
      }),
    );
  });

  it("invalidates the Today timeline after calendar task creation", async () => {
    postMock.mockResolvedValue(buildTodoResponse());

    const queryClient = new QueryClient({
      defaultOptions: {
        mutations: {
          retry: false,
        },
      },
    });
    const invalidateQueriesSpy = vi.spyOn(queryClient, "invalidateQueries");
    const { Wrapper } = createWrapper(queryClient);
    const { result } = renderHook(() => useCreateCalendarTodo(), {
      wrapper: Wrapper,
    });

    act(() => {
      result.current.createCalendarTodo({
        title: "Ship web fix",
        description: "verify Today refresh",
        priority: "Low",
        dtstart: new Date("2026-04-03T14:00:00.000Z"),
        due: new Date("2026-04-03T14:30:00.000Z"),
        rrule: null,
        listID: "list-1",
      });
    });

    await waitFor(() => {
      expect(result.current.createTodoStatus).toBe("success");
    });

    expect(invalidateQueriesSpy).toHaveBeenCalledWith({
      queryKey: ["todoTimeline"],
    });
  });
});

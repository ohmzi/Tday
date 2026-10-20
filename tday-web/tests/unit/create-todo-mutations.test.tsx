// @vitest-environment jsdom

import type { ReactNode } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { CompletedTodoItemType, TodoItemType } from "@/types";

const postMock = vi.fn();
const patchMock = vi.fn();
const toastMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  api: {
    POST: (...args: unknown[]) => postMock(...args),
    PATCH: (...args: unknown[]) => patchMock(...args),
  },
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({
    toast: (...args: unknown[]) => toastMock(...args),
  }),
}));

import { useCreateTodo } from "@/features/todayTodos/query/create-todo";
import { useCreateCalendarTodo } from "@/features/calendar/query/create-calendar-todo";
import { useEditTodoInstance } from "@/features/todayTodos/query/update-todo-instance";
import { useEditTodo } from "@/features/todayTodos/query/update-todo";
import { usePrioritizeTodo } from "@/features/todayTodos/query/prioritize-todo";
import { useUnCompleteTodo } from "@/features/completed/query/uncomplete-completedTodo";

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

function buildGhostTodoInput(): TodoItemType {
  return {
    ...buildTodoInput(),
    // A recurring instance: `<todoId>:<instanceTimestamp>`.
    id: "todo-1:1775227800000",
    rrule: "FREQ=DAILY",
    instanceDate: new Date("2026-04-03T00:00:00.000Z"),
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
    // The unified toast policy dropped the "Task Created" success toast in
    // ea0933b4 — creating a task is silent, only failures toast.
    expect(toastMock).not.toHaveBeenCalled();
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

// These hooks optimistically rewrite the ["todo"] cache in onMutate. An updater
// that assumes `oldTodos` is an array throws when nothing is cached yet, and
// React Query aborts the mutation from inside onMutate — the PATCH never fires
// and the user sees an error toast instead of a saved edit.
describe("todo update mutations on a cold cache", () => {
  afterEach(() => {
    patchMock.mockReset();
    toastMock.mockReset();
  });

  it("edits a single instance from a cold cache without crashing optimistic updates", async () => {
    patchMock.mockResolvedValue(undefined);

    const { queryClient, Wrapper } = createWrapper();
    const { result } = renderHook(() => useEditTodoInstance(undefined), {
      wrapper: Wrapper,
    });

    act(() => {
      result.current.editTodoInstanceMutateFn(buildGhostTodoInput());
    });

    await waitFor(() => {
      expect(result.current.editTodoInstanceStatus).toBe("success");
    });

    expect(patchMock).toHaveBeenCalledTimes(1);
    expect(patchMock).toHaveBeenCalledWith(
      expect.objectContaining({ url: "/api/todo/instance" }),
    );
    expect(queryClient.getQueryData<TodoItemType[]>(["todo"])).toEqual([]);
    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])).toEqual(
      [],
    );
    expect(toastMock).not.toHaveBeenCalled();
  });

  it("edits a todo from a cold cache without crashing optimistic updates", async () => {
    patchMock.mockResolvedValue(undefined);

    const { queryClient, Wrapper } = createWrapper();
    const { result } = renderHook(() => useEditTodo(), { wrapper: Wrapper });

    act(() => {
      result.current.editTodoMutateFn({
        ...buildTodoInput(),
        id: "todo-1",
        dateRangeChecksum: new Date("2026-04-03T14:30:00.000Z").toISOString(),
        rruleChecksum: null,
      });
    });

    await waitFor(() => {
      expect(result.current.editTodoStatus).toBe("success");
    });

    expect(patchMock).toHaveBeenCalledTimes(1);
    expect(patchMock).toHaveBeenCalledWith(
      expect.objectContaining({ url: "/api/todo" }),
    );
    expect(queryClient.getQueryData<TodoItemType[]>(["todo"])).toEqual([]);
    expect(toastMock).not.toHaveBeenCalled();
  });

  it("prioritizes a todo from a cold cache without crashing optimistic updates", async () => {
    patchMock.mockResolvedValue(undefined);

    const { queryClient, Wrapper } = createWrapper();
    const { result } = renderHook(() => usePrioritizeTodo(), {
      wrapper: Wrapper,
    });

    act(() => {
      result.current.prioritizeMutateFn({
        id: "todo-1",
        level: "High",
        isRecurring: false,
      });
    });

    // `prioritizePending` starts false, so assert the PATCH landed too —
    // otherwise this waits on a condition that is already true.
    await waitFor(() => {
      expect(patchMock).toHaveBeenCalledTimes(1);
      expect(result.current.prioritizePending).toBe(false);
    });

    expect(patchMock).toHaveBeenCalledWith(
      expect.objectContaining({ url: "/api/todo/prioritize" }),
    );
    expect(queryClient.getQueryData<TodoItemType[]>(["todo"])).toEqual([]);
    expect(toastMock).not.toHaveBeenCalled();
  });

  it("uncompletes a todo from a cold cache without crashing optimistic updates", async () => {
    patchMock.mockResolvedValue(undefined);

    const { queryClient, Wrapper } = createWrapper();
    const { result } = renderHook(() => useUnCompleteTodo(), {
      wrapper: Wrapper,
    });

    const completedTodo: CompletedTodoItemType = {
      id: "completed-1",
      originalTodoID: "todo-1",
      title: "Ship web fix",
      createdAt: new Date("2026-04-03T14:00:00.000Z"),
      completedAt: new Date("2026-04-03T15:00:00.000Z"),
      priority: "Low",
      due: new Date("2026-04-03T14:30:00.000Z"),
      userID: "user-1",
      rrule: null,
      instanceDate: null,
    };

    act(() => {
      result.current.mutateUnComplete(completedTodo);
    });

    await waitFor(() => {
      expect(patchMock).toHaveBeenCalledTimes(1);
      expect(result.current.isPending).toBe(false);
    });

    expect(patchMock).toHaveBeenCalledWith(
      expect.objectContaining({ url: "/api/todo/uncomplete" }),
    );
    expect(
      queryClient.getQueryData<CompletedTodoItemType[]>(["completedTodo"]),
    ).toEqual([]);
    expect(toastMock).not.toHaveBeenCalled();
  });
});

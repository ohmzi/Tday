// @vitest-environment jsdom

/**
 * Reproduction for "completing a task never updates the list until a page refresh".
 *
 * Completion is delayed-commit: `completeMutateFn` prunes the active-task caches synchronously
 * and only fires the PATCH when the undo toast closes. So the row leaving the list depends
 * entirely on those two `setQueryData` calls landing on the keys the lists actually read
 * (`["todo"]` and `["todoTimeline"]`), and on the mutation's `onSettled` invalidations firing
 * afterwards. These tests pin both halves.
 */

import type { ReactNode } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";

const patchMock = vi.fn();
const toastMock = vi.fn();

// Captures the { commit, undo } pair the completion flow hands the undo toast, so the test can
// drive "toast timed out" and "user pressed undo" without rendering Sonner.
let capturedHandlers: { commit: () => void; undo: () => void } | null = null;

vi.mock("@/lib/api-client", () => ({
  api: {
    PATCH: (...args: unknown[]) => patchMock(...args),
  },
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: (...args: unknown[]) => toastMock(...args) }),
}));

vi.mock("@/hooks/use-todo-action-toast", () => ({
  useTodoActionToast: () => ({
    showTodoCompletedToast: (handlers: { commit: () => void; undo: () => void }) => {
      capturedHandlers = handlers;
    },
    showTodoDeletedToast: vi.fn(),
  }),
}));

import { useCompleteTodo } from "@/features/todayTodos/query/complete-todo";

const TODO: TodoItemType = {
  id: "todo-1",
  title: "Water the plants",
  description: null,
  completed: false,
  priority: "Low",
  due: new Date("2026-08-22T10:00:00.000Z"),
  rrule: null,
  exdates: [],
  instanceDate: null,
  listID: null,
} as unknown as TodoItemType;

const OTHER: TodoItemType = { ...TODO, id: "todo-2", title: "Call the bank" };

function createHarness() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  // Seed both caches exactly as the list screens hold them.
  queryClient.setQueryData<TodoItemType[]>(["todo"], [TODO, OTHER]);
  queryClient.setQueryData<TodoItemType[]>(["todoTimeline"], [TODO, OTHER]);

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={["/en/app/todo"]}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </MemoryRouter>
    );
  }
  return { queryClient, Wrapper };
}

describe("completing a task prunes the caches the lists render from", () => {
  beforeEach(() => {
    patchMock.mockReset();
    patchMock.mockResolvedValue(null);
    toastMock.mockReset();
    capturedHandlers = null;
  });

  it("removes the task from ['todo'] and ['todoTimeline'] immediately", () => {
    const { queryClient, Wrapper } = createHarness();
    const { result } = renderHook(() => useCompleteTodo(), { wrapper: Wrapper });

    act(() => {
      result.current.completeMutateFn(TODO);
    });

    expect(queryClient.getQueryData<TodoItemType[]>(["todo"])?.map((t) => t.id)).toEqual(["todo-2"]);
    expect(
      queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])?.map((t) => t.id),
    ).toEqual(["todo-2"]);
  });

  it("hands the toast a commit that actually sends the PATCH", async () => {
    const { Wrapper } = createHarness();
    const { result } = renderHook(() => useCompleteTodo(), { wrapper: Wrapper });

    act(() => {
      result.current.completeMutateFn(TODO);
    });
    expect(capturedHandlers).not.toBeNull();

    act(() => {
      capturedHandlers!.commit();
    });

    // A passing status alone would not catch an aborted mutation, so assert the call itself.
    await waitFor(() => expect(patchMock).toHaveBeenCalledTimes(1));
    expect(patchMock.mock.calls[0][0]).toMatchObject({ url: "/api/todo/complete" });
  });

  it("undo restores the task by refetching, and never sends the PATCH", async () => {
    const { queryClient, Wrapper } = createHarness();
    const { result } = renderHook(() => useCompleteTodo(), { wrapper: Wrapper });

    act(() => {
      result.current.completeMutateFn(TODO);
    });
    act(() => {
      capturedHandlers!.undo();
    });

    expect(patchMock).not.toHaveBeenCalled();
    // Undo relies on invalidation -> refetch. With no queryFn registered here the cache cannot
    // refill, so the meaningful assertion is that the queries were marked stale for refetch.
    expect(queryClient.getQueryState(["todo"])?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(["todoTimeline"])?.isInvalidated).toBe(true);
  });

  it("works on a cold cache instead of throwing inside the updater", () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    function Wrapper({ children }: { children: ReactNode }) {
      return (
        <MemoryRouter initialEntries={["/en/app/todo"]}>
          <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
        </MemoryRouter>
      );
    }
    const { result } = renderHook(() => useCompleteTodo(), { wrapper: Wrapper });

    expect(() => act(() => result.current.completeMutateFn(TODO))).not.toThrow();
  });
});

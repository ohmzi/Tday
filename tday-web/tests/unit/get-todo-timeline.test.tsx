// @vitest-environment jsdom

import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";

const toastErrorSpy = vi.fn();
const getMock = vi.fn();

vi.mock("sonner", () => ({
  toast: Object.assign(vi.fn(), {
    error: (...args: unknown[]) => toastErrorSpy(...args),
  }),
}));

vi.mock("@/lib/api-client", () => ({
  api: {
    GET: (...args: unknown[]) => getMock(...args),
  },
}));

import { useTodoTimeline } from "@/features/todayTodos/query/get-todo-timeline";

function createWrapper() {
  const queryClient = new QueryClient({
    queryCache: new QueryCache({
      onError: (error) => {
        toastErrorSpy(error.message || "An error occurred");
      },
    }),
    defaultOptions: {
      queries: {
        retryDelay: 0,
      },
    },
  });

  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };
}

describe("useTodoTimeline", () => {
  afterEach(() => {
    toastErrorSpy.mockReset();
    getMock.mockReset();
  });

  it("parses backend datetimes without timezone suffix as UTC", async () => {
    getMock.mockResolvedValue({
      todos: [
        {
          id: "todo-1",
          title: "Late-night task",
          description: null,
          pinned: false,
          createdAt: "2026-03-01T00:00:00",
          order: 1,
          priority: "Medium",
          dtstart: "2026-03-02T23:30:00",
          durationMinutes: 30,
          due: "2026-03-03T00:15:00",
          rrule: null,
          timeZone: "UTC",
          userID: "user-1",
          completed: false,
          exdates: [],
          instanceDate: "2026-03-02T23:30:00",
          listID: null,
        },
      ],
    });

    const { result } = renderHook(() => useTodoTimeline(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.todoLoading).toBe(false);
    });

    expect(result.current.todos).toHaveLength(1);
    expect(result.current.todos[0]?.dtstart.toISOString()).toBe(
      "2026-03-02T23:30:00.000Z",
    );
    expect(result.current.todos[0]?.due.toISOString()).toBe(
      "2026-03-03T00:15:00.000Z",
    );
    expect(result.current.todos[0]?.instanceDate?.toISOString()).toBe(
      "2026-03-02T23:30:00.000Z",
    );
    expect(result.current.todos[0]?.id).toBe("todo-1:1772494200000");
  });
});

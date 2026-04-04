// @vitest-environment jsdom

import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider, QueryCache } from "@tanstack/react-query";
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

import { useTodo } from "@/features/todayTodos/query/get-todo";

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

describe("useTodo", () => {
  afterEach(() => {
    toastErrorSpy.mockReset();
    getMock.mockReset();
  });

  it("formats todo dates from the API response", async () => {
    getMock.mockResolvedValue({
      todos: [
        {
          id: "todo-1",
          title: "Pay rent",
          description: null,
          pinned: false,
          createdAt: "2026-03-01T00:00:00.000Z",
          order: 1,
          priority: "Medium",
          due: "2026-03-02T09:30:00.000Z",
          rrule: null,
          timeZone: "UTC",
          userID: "user-1",
          completed: false,
          exdates: [],
          instanceDate: "2026-03-02T09:00:00.000Z",
          listID: null,
        },
      ],
    });

    const { result } = renderHook(() => useTodo(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.todoLoading).toBe(false);
    });

    expect(result.current.todos).toHaveLength(1);
    expect(result.current.todos[0]?.createdAt).toBeInstanceOf(Date);
    expect(result.current.todos[0]?.due).toBeInstanceOf(Date);
    expect(result.current.todos[0]?.instanceDate).toBeInstanceOf(Date);
    expect(result.current.todos[0]?.id).toBe("todo-1:1772442000000");
  });

  it("surfaces query failures through the global error toast", async () => {
    getMock.mockRejectedValue(new Error("Network down"));

    renderHook(() => useTodo(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(toastErrorSpy).toHaveBeenCalledWith("Network down");
    });
  });
});

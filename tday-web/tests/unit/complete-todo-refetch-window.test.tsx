// @vitest-environment jsdom

/**
 * Completion is delayed-commit: the caches are pruned immediately but the PATCH only fires when
 * the undo toast closes (5s later). For that whole window the server still reports the task as
 * incomplete, so a refetch of ["todo"] / ["todoTimeline"] refills the cache from the server and
 * the row the user just ticked comes back — unless the staged row is claimed somewhere the read
 * path respects, which is what `@/lib/todo/staged-todo-rows` does and what this pins.
 *
 * Refetches in that window are not hypothetical: src/lib/realtime.tsx invalidates ["todo"],
 * ["todoTimeline"], ["overdueTodo"], ["calendarTodo"] and ["list"] on every `todo` realtime event
 * (each completion emits one back to the actor who caused it), and React Query also refetches on
 * window focus and on remount once the 60s staleTime has passed.
 */

import type { ReactNode } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";

const patchMock = vi.fn();
let capturedHandlers: { commit: () => void; undo: () => void } | null = null;

vi.mock("@/lib/api-client", () => ({
  api: { PATCH: (...a: unknown[]) => patchMock(...a) },
}));
vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));
vi.mock("@/hooks/use-todo-action-toast", () => ({
  useTodoActionToast: () => ({
    showTodoCompletedToast: (h: { commit: () => void; undo: () => void }) => {
      capturedHandlers = h;
    },
    showTodoDeletedToast: vi.fn(),
  }),
}));

import { useCompleteTodo } from "@/features/todayTodos/query/complete-todo";

const TODO = {
  id: "todo-1:undefined",
  title: "Water the plants",
  completed: false,
  priority: "Low",
  due: new Date("2026-08-22T10:00:00.000Z"),
  rrule: null,
  instanceDate: null,
} as unknown as TodoItemType;

const OTHER = { ...TODO, id: "todo-2:undefined", title: "Call the bank" };

/** Stands in for the server: still reports the task as incomplete until the PATCH lands. */
function makeServer() {
  let completed = false;
  return {
    complete: () => {
      completed = true;
    },
    fetchTimeline: async () => (completed ? [OTHER] : [TODO, OTHER]),
  };
}

describe("the delayed-commit window", () => {
  beforeEach(() => {
    patchMock.mockReset();
    patchMock.mockResolvedValue(null);
    capturedHandlers = null;
  });

  // Was `it.fails` while the completion flow was memory-only until commit. The staged row is now
  // claimed at the cache boundary (`stageTodoRows`), so a refetch of the pruned keys cannot
  // restore it, and the marker came off with the fix.
  it("should keep the ticked task out of the list even if something refetches first", async () => {
    const server = makeServer();
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

    // An active observer, exactly like the list screens have.
    const { result } = renderHook(
      () => ({
        list: useQuery<TodoItemType[]>({
          queryKey: ["todoTimeline"],
          queryFn: server.fetchTimeline,
        }),
        complete: useCompleteTodo(),
      }),
      { wrapper: Wrapper },
    );

    await waitFor(() => expect(result.current.list.data).toHaveLength(2));

    // Tick the task: the cache is pruned, but no PATCH has been sent yet.
    act(() => {
      result.current.complete.completeMutateFn(TODO);
    });
    expect(queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])).toHaveLength(1);

    // A realtime `todo` event arrives (src/lib/realtime.tsx invalidates these very keys).
    await act(async () => {
      await queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
    });

    // The task the user just ticked stays out of the list: the server was never
    // told, but the cached row is claimed until the commit, so the refetch's
    // result is stripped of it before it lands.
    await waitFor(() =>
      expect(
        queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])?.map((t) => t.id),
      ).toEqual(["todo-2:undefined"]),
    );
  });

  it("settles correctly when nothing refetches before the commit", async () => {
    const server = makeServer();
    patchMock.mockImplementation(async () => {
      server.complete();
      return null;
    });

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

    const { result } = renderHook(
      () => ({
        list: useQuery<TodoItemType[]>({
          queryKey: ["todoTimeline"],
          queryFn: server.fetchTimeline,
        }),
        complete: useCompleteTodo(),
      }),
      { wrapper: Wrapper },
    );
    await waitFor(() => expect(result.current.list.data).toHaveLength(2));

    act(() => {
      result.current.complete.completeMutateFn(TODO);
    });
    act(() => {
      capturedHandlers!.commit();
    });

    await waitFor(() => expect(patchMock).toHaveBeenCalledTimes(1));
    await waitFor(() =>
      expect(
        queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])?.map((t) => t.id),
      ).toEqual(["todo-2:undefined"]),
    );
  });
});

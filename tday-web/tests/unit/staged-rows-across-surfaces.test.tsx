// @vitest-environment jsdom

/**
 * The other surfaces the delayed-commit claim has to cover.
 *
 * `complete-todo-refetch-window.test.tsx` pins the Today/timeline case. This
 * file pins the siblings the same pruning shape left open, because the claim
 * (`@/lib/todo/staged-todo-rows`) is only as good as the set of caches it
 * covers and the refetch it performs when the row turns out to be real:
 *
 * - the calendar cache. `complete-calendar-todo.ts` prunes `["calendarTodo"]`
 *   and defers the PATCH, and `src/lib/realtime.tsx` invalidates that same key
 *   on every `todo` event — including the echo of this completion.
 * - the Anytime caches. `["floater"]` is an array, but `["floaterList", id]` is
 *   `{ list, floaters }`, so it takes a branch of its own or the guard would
 *   skip it as "not shaped like a list".
 * - the UNDO end of the window. Undo restores the caches the mutation itself
 *   pruned, but the claim stripped the row from every row-list cache, so an undo
 *   that only invalidates its own two keys leaves the others one row short until
 *   something unrelated refetches them.
 */

import type { ReactNode } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { FloaterItemType, TodoItemType } from "@/types";

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
import { useCompleteCalendarTodo } from "@/features/calendar/query/complete-calendar-todo";
import { useCompleteFloater } from "@/features/floater/query/complete-floater";

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

const FLOATER = {
  id: "fl-1",
  title: "Pick a paint colour",
  completed: false,
  priority: "Low",
  listID: null,
} as unknown as FloaterItemType;

const OTHER_FLOATER = { ...FLOATER, id: "fl-2", title: "Read the manual" };

const RANGE = {
  start: new Date("2026-08-01T00:00:00.000Z"),
  end: new Date("2026-08-31T00:00:00.000Z"),
};
const RANGE_KEY = ["calendarTodo", RANGE.start.getTime(), RANGE.end.getTime()];

const LIST_ID = "list-a";

/** Stands in for the server: still reports every row as live until told otherwise. */
function makeServer() {
  return {
    todos: async () => [TODO, OTHER],
    floaters: async () => [FLOATER, OTHER_FLOATER],
  };
}

function makeClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function wrapperFor(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={["/en/app/todo"]}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </MemoryRouter>
    );
  };
}

beforeEach(() => {
  patchMock.mockReset();
  patchMock.mockResolvedValue(null);
  capturedHandlers = null;
});

describe("the claim on the calendar screen", () => {
  it("keeps the ticked task out of the calendar cache when a todo event refetches it", async () => {
    const server = makeServer();
    const queryClient = makeClient();

    const { result } = renderHook(
      () => ({
        calendar: useQuery<TodoItemType[]>({
          queryKey: RANGE_KEY,
          queryFn: server.todos,
        }),
        complete: useCompleteCalendarTodo(),
      }),
      { wrapper: wrapperFor(queryClient) },
    );

    await waitFor(() => expect(result.current.calendar.data).toHaveLength(2));

    act(() => {
      result.current.complete.mutateComplete({ todoItem: TODO });
    });
    // Pruned out of the calendar at the tap.
    expect(queryClient.getQueryData<TodoItemType[]>(RANGE_KEY)).toHaveLength(1);

    // The realtime `todo` event this completion emitted comes straight back.
    await act(async () => {
      await queryClient.invalidateQueries({ queryKey: ["calendarTodo"] });
    });

    await waitFor(() =>
      expect(queryClient.getQueryData<TodoItemType[]>(RANGE_KEY)?.map((t) => t.id)).toEqual(
        ["todo-2:undefined"],
      ),
    );
  });
});

describe("the claim on the Anytime feeds", () => {
  it("keeps the ticked floater out of both the feed and the { list, floaters } list cache", async () => {
    const server = makeServer();
    const queryClient = makeClient();

    const { result } = renderHook(
      () => ({
        feed: useQuery<FloaterItemType[]>({
          queryKey: ["floater"],
          queryFn: server.floaters,
        }),
        list: useQuery<{ list: unknown; floaters: FloaterItemType[] }>({
          queryKey: ["floaterList", LIST_ID],
          queryFn: async () => ({
            list: { id: LIST_ID },
            floaters: await server.floaters(),
          }),
        }),
        complete: useCompleteFloater(),
      }),
      { wrapper: wrapperFor(queryClient) },
    );

    await waitFor(() => expect(result.current.feed.data).toHaveLength(2));
    await waitFor(() => expect(result.current.list.data?.floaters).toHaveLength(2));

    act(() => {
      result.current.complete.completeMutateFn(FLOATER);
    });
    expect(queryClient.getQueryData<FloaterItemType[]>(["floater"])).toHaveLength(1);

    await act(async () => {
      await queryClient.invalidateQueries({ queryKey: ["floater"] });
      await queryClient.invalidateQueries({ queryKey: ["floaterList"] });
    });

    await waitFor(() =>
      expect(queryClient.getQueryData<FloaterItemType[]>(["floater"])?.map((f) => f.id)).toEqual([
        "fl-2",
      ]),
    );
    await waitFor(() =>
      expect(
        queryClient
          .getQueryData<{ list: unknown; floaters: FloaterItemType[] }>([
            "floaterList",
            LIST_ID,
          ])
          ?.floaters.map((f) => f.id),
      ).toEqual(["fl-2"]),
    );
    // The container's other field survives the correction.
    expect(
      queryClient.getQueryData<{ list: { id: string } }>(["floaterList", LIST_ID])?.list.id,
    ).toBe(LIST_ID);
  });
});

describe("the undo end of the window", () => {
  it("restores a cache the claim stripped that the mutation itself never pruned", async () => {
    const server = makeServer();
    const queryClient = makeClient();

    const { result } = renderHook(
      () => ({
        timeline: useQuery<TodoItemType[]>({
          queryKey: ["todoTimeline"],
          queryFn: server.todos,
        }),
        list: useQuery<TodoItemType[]>({
          queryKey: ["list", LIST_ID],
          queryFn: server.todos,
        }),
        complete: useCompleteTodo(),
      }),
      { wrapper: wrapperFor(queryClient) },
    );

    await waitFor(() => expect(result.current.timeline.data).toHaveLength(2));
    await waitFor(() => expect(result.current.list.data).toHaveLength(2));

    act(() => {
      result.current.complete.completeMutateFn(TODO);
    });

    // A `todo` event refetches the list screen's cache, which this mutation
    // never pruned — the claim reaches it and holds the row out.
    await act(async () => {
      await queryClient.invalidateQueries({ queryKey: ["list"] });
    });
    await waitFor(() =>
      expect(
        queryClient.getQueryData<TodoItemType[]>(["list", LIST_ID])?.map((t) => t.id),
      ).toEqual(["todo-2:undefined"]),
    );

    // Undo: the server still has the row, so every cache the claim stripped has
    // to be refetched — not just the two the mutation pruned.
    act(() => {
      capturedHandlers!.undo();
    });

    await waitFor(() =>
      expect(
        queryClient.getQueryData<TodoItemType[]>(["list", LIST_ID])?.map((t) => t.id),
      ).toEqual(["todo-1:undefined", "todo-2:undefined"]),
    );
    await waitFor(() =>
      expect(
        queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])?.map((t) => t.id),
      ).toEqual(["todo-1:undefined", "todo-2:undefined"]),
    );
    expect(patchMock).not.toHaveBeenCalled();
  });
});

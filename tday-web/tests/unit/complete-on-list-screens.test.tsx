// @vitest-environment jsdom

/**
 * Ticking a task on a list screen used to leave it sitting in the list until the page was
 * reloaded.
 *
 * The list-detail endpoint returns `ListTodoDto`, which carries no `listID` field, so every row
 * rendered from it has `listID === null`. Completion keyed its cache writes on that value, so it
 * pruned `["list", null]` — a query no screen observes — while the UI kept reading
 * `["list", "<id>"]`. Undo and onSettled invalidated the same phantom key, so nothing refetched
 * either. The sibling delete path was immune because it always used a `["list"]` prefix match.
 *
 * The floater-list screen had the same defect behind an `if (floater.listID)` guard that was never
 * true, plus a shape bug: that cache holds `{ list, floaters }`, not an array.
 */

import type { ReactNode } from "react";
import { act, renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
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

import { useCompleteListTodo } from "@/features/list/query/complete-list-todo";
import { useCompleteFloater } from "@/features/floater/query/complete-floater";

const LIST_ID = "list-abc";

/** listID is null exactly as the real list-detail payload produces it. */
const ROW = {
  id: "todo-1:undefined",
  title: "Water the plants",
  completed: false,
  priority: "Low",
  due: new Date("2026-08-22T10:00:00.000Z"),
  rrule: null,
  instanceDate: null,
  listID: null,
} as unknown as TodoItemType;

const OTHER_ROW = { ...ROW, id: "todo-2:undefined", title: "Call the bank" };

const FLOATER = {
  id: "fl-1",
  title: "Pick a paint colour",
  completed: false,
  priority: "Low",
  listID: null,
} as unknown as FloaterItemType;

const OTHER_FLOATER = { ...FLOATER, id: "fl-2", title: "Read the manual" };

function wrapperFor(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={["/en/app/list/list-abc"]}>
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

describe("completing a task on a scheduled-list screen", () => {
  function harness() {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    // The key the screen actually renders from.
    queryClient.setQueryData<TodoItemType[]>(["list", LIST_ID], [ROW, OTHER_ROW]);
    const { result } = renderHook(() => useCompleteListTodo(), {
      wrapper: wrapperFor(queryClient),
    });
    return { queryClient, result };
  }

  it("removes the row from the list the screen is rendering", () => {
    const { queryClient, result } = harness();

    act(() => {
      result.current.completeMutateFn(ROW);
    });

    expect(
      queryClient.getQueryData<TodoItemType[]>(["list", LIST_ID])?.map((t) => t.id),
    ).toEqual(["todo-2:undefined"]);
  });

  it("never writes to a null-suffixed key", () => {
    const { queryClient, result } = harness();

    act(() => {
      result.current.completeMutateFn(ROW);
    });

    expect(queryClient.getQueryData(["list", null])).toBeUndefined();
  });

  it("undo invalidates the key the screen observes, so the row can come back", () => {
    const { queryClient, result } = harness();

    act(() => {
      result.current.completeMutateFn(ROW);
    });
    act(() => {
      capturedHandlers!.undo();
    });

    expect(queryClient.getQueryState(["list", LIST_ID])?.isInvalidated).toBe(true);
    expect(patchMock).not.toHaveBeenCalled();
  });
});

describe("completing a floater on a floater-list screen", () => {
  function harness() {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    queryClient.setQueryData(["floater"], [FLOATER, OTHER_FLOATER]);
    // This cache holds an object, not an array.
    queryClient.setQueryData(["floaterList", LIST_ID], {
      list: { id: LIST_ID, name: "Decorating" },
      floaters: [FLOATER, OTHER_FLOATER],
    });
    const { result } = renderHook(() => useCompleteFloater(), {
      wrapper: wrapperFor(queryClient),
    });
    return { queryClient, result };
  }

  it("removes the floater from the nested floaters array without clobbering the object", () => {
    const { queryClient, result } = harness();

    act(() => {
      result.current.completeMutateFn(FLOATER);
    });

    const cached = queryClient.getQueryData<{ list: { name: string }; floaters: FloaterItemType[] }>(
      ["floaterList", LIST_ID],
    );
    expect(cached?.floaters.map((f) => f.id)).toEqual(["fl-2"]);
    // The sibling `list` metadata must survive the update.
    expect(cached?.list.name).toBe("Decorating");
  });

  it("still prunes the plain floater feed", () => {
    const { queryClient, result } = harness();

    act(() => {
      result.current.completeMutateFn(FLOATER);
    });

    expect(
      queryClient.getQueryData<FloaterItemType[]>(["floater"])?.map((f) => f.id),
    ).toEqual(["fl-2"]);
  });

  it("undo invalidates the floater-list key the screen observes", () => {
    const { queryClient, result } = harness();

    act(() => {
      result.current.completeMutateFn(FLOATER);
    });
    act(() => {
      capturedHandlers!.undo();
    });

    expect(queryClient.getQueryState(["floaterList", LIST_ID])?.isInvalidated).toBe(true);
  });
});

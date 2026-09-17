// @vitest-environment jsdom

/**
 * Un-completing is the mirror of completing, and it was the mirror in the one place
 * that mattered least: `["completedTodo"]` and `["completedFloater"]` were the two
 * row-list caches `@/lib/todo/staged-todo-rows` deliberately did NOT claim. That is
 * right for a completion — a completed row belongs in them — and exactly wrong for a
 * restore, which takes the row out of one of them and puts it into the active caches.
 *
 * The window is wider here than on the complete path, not narrower. Completion prunes
 * at the tap and holds the row for the five-second undo toast; un-complete prunes at
 * the tap and does not send its PATCH until the row's exit choreography finishes
 * ~840 ms later (`ItemContainer.handleUncomplete`). For that whole stretch the server
 * still reports the task completed, so any read of the completed cache — a
 * collaborator's `completed.changed` event, a sibling mutation's `onSettled`, a focus
 * refetch — writes the row the user just dismissed straight back in. Then the PATCH
 * lands, the echo arrives, and it leaves again.
 *
 * `complete-todo-refetch-window.test.tsx` pins the forward direction. This pins the
 * reverse, on the cache that had no claim at all.
 *
 * The PATCH is deliberately held open rather than resolved immediately. A refetch
 * fired after the request settles is *supposed* to be able to restore the row — that
 * is what `releaseAndRestoreTodoRows` is for — so a test that lets it settle first
 * asserts nothing about the window. Holding the request makes "the window is still
 * open" a fact the test controls instead of a race it hopes to win.
 */

import type { ReactNode } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { CompletedTodoItemType } from "@/types";

const patchMock = vi.fn();
/** Held open by the default PATCH stub so the window stays open under the test's control. */
let releasePatch: (() => void) | null = null;

vi.mock("@/lib/api-client", () => ({
  api: { PATCH: (...a: unknown[]) => patchMock(...a) },
}));
vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));

import { useUnCompleteTodo } from "@/features/completed/query/uncomplete-completedTodo";
import { pruneTodoRowCaches } from "@/lib/todo/staged-todo-rows";

/** The completed cache's own spelling of a row id: `todo.id` plus the instance date. */
const COMPLETED_ROW = {
  id: "todo-1:1730000000000",
  originalTodoID: "todo-1",
  title: "Water the plants",
  priority: "Low",
  rrule: null,
  instanceDate: null,
} as unknown as CompletedTodoItemType;

const OTHER_ROW = {
  ...COMPLETED_ROW,
  id: "todo-2:1730000000000",
  originalTodoID: "todo-2",
  title: "Call the bank",
} as unknown as CompletedTodoItemType;

const completedIds = (queryClient: QueryClient) =>
  queryClient
    .getQueryData<CompletedTodoItemType[]>(["completedTodo"])
    ?.map((row) => row.id);

/**
 * Stands in for the server mid-window: it has not been told yet, so it still reports
 * the restored task as completed — which is what makes a read in this window a
 * resurrection rather than a harmless no-op.
 */
function makeServer() {
  let restored = false;
  return {
    restore: () => {
      restored = true;
    },
    fetchCompleted: async () => (restored ? [OTHER_ROW] : [COMPLETED_ROW, OTHER_ROW]),
  };
}

function wrapperFor(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter initialEntries={["/en/app/completed"]}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </MemoryRouter>
    );
  };
}

/** Mounts the completed list plus the un-complete mutation, with one row already loaded. */
async function mountCompletedScreen(server: ReturnType<typeof makeServer>) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const { result } = renderHook(
    () => ({
      list: useQuery<CompletedTodoItemType[]>({
        queryKey: ["completedTodo"],
        queryFn: server.fetchCompleted,
      }),
      uncomplete: useUnCompleteTodo(),
    }),
    { wrapper: wrapperFor(queryClient) },
  );
  await waitFor(() => expect(result.current.list.data).toHaveLength(2));
  return { queryClient, result };
}

describe("the restore window on the completed list", () => {
  beforeEach(() => {
    patchMock.mockReset();
    releasePatch = null;
    patchMock.mockImplementation(
      () =>
        new Promise((resolve) => {
          releasePatch = () => resolve(null);
        }),
    );
  });

  it("keeps a restored task out of the completed list when something refetches first", async () => {
    const server = makeServer();
    const { queryClient, result } = await mountCompletedScreen(server);

    // Untick the row: it leaves the cache, and the PATCH is still un-answered.
    act(() => {
      result.current.uncomplete.mutateUnComplete(COMPLETED_ROW);
    });
    await waitFor(() => expect(completedIds(queryClient)).toEqual(["todo-2:1730000000000"]));
    expect(patchMock).toHaveBeenCalledTimes(1);

    // A `completed` event arrives while the row's choreography is still playing —
    // exactly what `src/lib/realtime.tsx` does on that family — and the server still
    // has the task completed, so the read answers with the row in it.
    await act(async () => {
      await queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
    });

    // The task the user just restored stays out. Without the claim, the refetch's
    // answer is written in whole and the row comes back struck through — the exact
    // "leaves, comes back, leaves again" the module header describes.
    expect(completedIds(queryClient)).toEqual(["todo-2:1730000000000"]);

    // Let the request finish so the test does not leave a promise dangling.
    await act(async () => {
      releasePatch?.();
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  });

  it("lets the row answer to the server again once the request has settled", async () => {
    const server = makeServer();
    const { queryClient, result } = await mountCompletedScreen(server);

    await act(async () => {
      result.current.uncomplete.mutateUnComplete(COMPLETED_ROW);
    });
    await waitFor(() => expect(patchMock).toHaveBeenCalledTimes(1));
    expect(completedIds(queryClient)).toEqual(["todo-2:1730000000000"]);

    // The request lands. `onSettled` releases the claim and invalidates, and from
    // here the server's answer is the one that counts — the release having happened
    // first is what lets that refetch be filtered by nothing.
    server.restore();
    await act(async () => {
      releasePatch?.();
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    // The claim is a window, not a permanent local override: nothing is left claimed,
    // so a later read is answered normally. (It also cannot be stranded — a row left
    // in the staged set would never come back on any surface.)
    await act(async () => {
      await queryClient.invalidateQueries({ queryKey: ["completedTodo"] });
    });
    await waitFor(() => expect(completedIds(queryClient)).toEqual(["todo-2:1730000000000"]));
  });

  /**
   * The claim is made with the todo id the mutation body carries, while the cache holds
   * the same task spelled `` `${todo.id}:${instanceDateMillis}` `` — and the ACTIVE
   * caches spell theirs with the same template over `undefined` for a task with no
   * instance date. A guard matching raw strings would cover the completed cache not at
   * all, and would quietly stop covering the active caches too, which is the wider
   * regression hiding behind the narrower one.
   *
   * Driven through `pruneTodoRowCaches`, which applies the same match the guard does
   * against a set, so the id-matching rule is asserted directly rather than through a
   * race. The date here is deliberately NOT the one the row is cached with: a rule that
   * happened to work only because the two suffixes coincided would pass the test above
   * and fail this one.
   */
  it("matches a claimed todo id against every spelling of that row", () => {
    const queryClient = new QueryClient();
    const cachedRow = { ...COMPLETED_ROW, id: "todo-1:9999999999999" };
    queryClient.setQueryData(["completedTodo"], [cachedRow, OTHER_ROW]);

    // The id the user's action carries — the bare todo id, no instance-date suffix.
    pruneTodoRowCaches(queryClient, new Set(["todo-1"]));

    expect(
      queryClient
        .getQueryData<CompletedTodoItemType[]>(["completedTodo"])
        ?.map((row) => row.id),
    ).toEqual(["todo-2:1730000000000"]);
  });
});

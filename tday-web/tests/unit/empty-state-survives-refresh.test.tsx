// @vitest-environment jsdom

/**
 * AN EMPTY STATE IS AN ANSWER, NOT AN ABSENCE OF ONE.
 *
 * The reported bug is a native one: on Android and iOS every feed's empty-state gate spelled
 * "we have an answer" as `!isLoading`, and `isLoading` is raised there by `refresh()` alone,
 * over a feed the view model had already hydrated from cache. So the pull that asks the app to
 * RE-CHECK its answer was the very term that withdrew it — the illustration and its copy
 * vanished, the page collapsed upward, and the whole block came back when the refresh returned
 * with nothing new. Both clients now decide it with a three-state `feedAnswer` that has no
 * loading parameter at all.
 *
 * Web was found not to have the bug, and this file is the reason that survives the next
 * refactor. `!isLoading` is safe HERE and nowhere else in the family, because of exactly what
 * TanStack Query v5 puts in it: `isPending && isFetching`, where `status` leaves `pending` the
 * moment `data !== undefined` and never returns. It is therefore "this feed has no answer yet"
 * — the natives' AWAITING_FIRST — and not "a request is in flight".
 *
 * That distinction is a claim about a library, so it is DRIVEN here rather than asserted from
 * memory: every test below renders the real query hook against a controllable server and reads
 * the gate that hangs off it. A hand-typed boolean table would have proved only that the hooks
 * do what their arguments say, which was never in doubt; what had to be proved is that the
 * argument does not move when a refresh runs.
 *
 * Both directions, on every scope, because half of this rule is the other half's worst bug:
 *   - the scene is WITHHELD while the first answer is still in flight (telling someone "no
 *     tasks" before you have looked is worse than the flicker being fixed), and
 *   - the scene SURVIVES a revalidation over an answer already on screen, with no `false`
 *     between the frames — the recorded render log is what rules out a one-frame flash that a
 *     before/after assertion would step straight over.
 * And it still LEAVES the moment a row arrives, which is the thing an unconditional empty state
 * would break.
 *
 * `isFetching` is the term that would undo all of this — true for every revalidation, cached
 * data or not. Its absence from the render path is pinned separately and statically, in
 * `tests/guardrails/web-empty-state-refresh-immunity.test.ts`.
 */

import type { ReactNode } from "react";
import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider, useIsFetching } from "@tanstack/react-query";
import { useEffect } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const getMock = vi.fn();
vi.mock("@/lib/api-client", () => ({
  api: { GET: (args: { url: string }) => getMock(args) },
}));

import { useFloater } from "@/features/floater/query/get-floater";
import { useFloaterEmptyState } from "@/features/floater/lib/useFloaterEmptyState";
import { useList } from "@/features/list/query/get-list-todos";
import { useListEmptyState } from "@/features/list/lib/useListEmptyState";
import { useTodoTimeline } from "@/features/todayTodos/query/get-todo-timeline";
import { useTimelineEmptyState } from "@/features/todayTodos/lib/useTimelineEmptyState";
import type { TimelineItem } from "@/features/todayTodos/component/AllTasksTimelineContainer";
import type { TodoItemType } from "@/types";

/** The one in-flight feed request, handed back so a test can hold it open and then answer it. */
type Pending = { resolve: (value: unknown) => void; settled: Promise<unknown> };

let pending: Pending | null = null;

/**
 * Stands in for the server, with the response withheld until the test says so.
 *
 * The completed-history query is answered immediately and deliberately does not become the
 * `pending` slot: `useTimelineEmptyState` reads it for Today's "day done" count, and a test about
 * a feed's own refresh must not be able to block on, or accidentally answer, a different query.
 */
function installServer() {
  getMock.mockImplementation(({ url }: { url: string }) => {
    if (url === "/api/completedTodo") {
      return Promise.resolve({ completedTodos: [] });
    }
    let resolve!: (value: unknown) => void;
    const settled = new Promise<unknown>((r) => {
      resolve = r;
    });
    pending = { resolve, settled };
    return settled;
  });
}

/** Answers whatever request is currently open, and lets the query settle before returning. */
async function answerWith(response: unknown) {
  const open = pending;
  expect(open, "no feed request was in flight to answer").not.toBeNull();
  pending = null;
  await act(async () => {
    open!.resolve(response);
    await open!.settled;
  });
}

// Far-future and not overdue, so "this scope's own zero" is genuinely about the scope having a
// row rather than about an Earlier bucket quietly holding it — see `useListEmptyState`'s
// search-immune/Earlier-excluding parameter pair.
const ROW = {
  id: "todo-1",
  title: "Water the plants",
  completed: false,
  priority: "Low",
  due: "2099-04-01T10:00:00.000Z",
  createdAt: "2026-01-01T00:00:00.000Z",
  updatedAt: null,
  instanceDate: null,
  rrule: null,
  listID: null,
  pinned: false,
};

const FLOATER_ROW = {
  id: "floater-1",
  title: "Call the bank",
  completed: false,
  priority: "Low",
  createdAt: "2026-01-01T00:00:00.000Z",
  updatedAt: null,
};

/** What every scope's adapter reports: the gate, the flag it must not read, and the skeleton. */
type FeedProbe = { showEmpty: boolean; isFetching: boolean; showSkeleton: boolean };

let seen: boolean[] = [];

/** Records every COMMITTED value of the gate, which is what a one-frame flash would show up in. */
function useRecorded(probe: FeedProbe): FeedProbe {
  useEffect(() => {
    seen.push(probe.showEmpty);
  });
  return probe;
}

function useFloaterProbe(): FeedProbe {
  const { floaters, floaterLoading, isFetching } = useFloater();
  const { showEmpty } = useFloaterEmptyState({
    isLoading: floaterLoading,
    isSearching: false,
    pendingRowCount: floaters.filter((floater) => !floater.completed).length,
  });
  // `showSkeleton` is the same boolean `NativeFloaterTaskHomeDashboard` hands
  // `useSkeletonCrossfade`, read here so the first-load half of the rule is asserted as the
  // hand-over it actually is rather than as an absence.
  return useRecorded({ showEmpty, isFetching, showSkeleton: floaterLoading });
}

function useListProbe(): FeedProbe {
  const { listTodos, listTodosLoading, isFetching } = useList({ id: "list-1" });
  const hasRows = listTodos.length > 0;
  const { showEmpty } = useListEmptyState({
    listTodosLoading,
    isSearching: false,
    hasEarlierItems: false,
    // `ROW` is far-future, so the container's Earlier split would put every row on this side of
    // it; both parameters are fed the same count for the same reason.
    hasNonEarlierListTodos: hasRows,
    hasNonEarlierRawListTodos: hasRows,
    pendingRowCount: listTodos.length,
  });
  return useRecorded({ showEmpty, isFetching, showSkeleton: listTodosLoading });
}

function useTimelineProbe(): FeedProbe {
  const { todos, todoLoading } = useTodoTimeline();
  // `useTodoTimeline` does not even return `isFetching`, which is the point; the client is asked
  // directly so the refresh is still proved to be running while the scene stays up.
  const isFetching = useIsFetching({ queryKey: ["todoTimeline"] }) > 0;
  const scopeFilteredItems: TimelineItem[] = todos.map((todo) => ({
    todo: todo as TodoItemType,
    dayDiff: 3,
    dayKey: "2099-04-01",
    label: "Wed",
  }));
  const { showEmpty } = useTimelineEmptyState({
    scope: "all",
    scopeFilteredItems,
    timeline: true,
    todoLoading,
    isSearching: false,
    earlierExpanded: false,
    earlierHandoff: "idle",
    beginSceneExit: () => {},
    todayHasEarlierItems: false,
    pendingRowCount: todos.length,
  });
  return useRecorded({ showEmpty, isFetching, showSkeleton: todoLoading });
}

const SCOPES: ReadonlyArray<{
  name: string;
  queryKey: readonly unknown[];
  useProbe: () => FeedProbe;
  empty: unknown;
  populated: unknown;
}> = [
  // The screen in the report.
  {
    name: "the Anytime feed",
    queryKey: ["floater"],
    useProbe: useFloaterProbe,
    empty: { floaters: [] },
    populated: { floaters: [FLOATER_ROW] },
  },
  // One custom list — `useListEmptyState`, the gate `ListContainer` sits behind.
  {
    name: "a list",
    queryKey: ["list", "list-1"],
    useProbe: useListProbe,
    empty: { todos: [] },
    populated: { todos: [ROW] },
  },
  // `useTimelineEmptyState` is one gate for Today, All, Priority, Scheduled and Overdue, so this
  // row stands for five screens rather than for "all".
  {
    name: "the scoped timeline",
    queryKey: ["todoTimeline"],
    useProbe: useTimelineProbe,
    empty: { todos: [] },
    populated: { todos: [ROW] },
  },
];

let client: QueryClient;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  getMock.mockReset();
  installServer();
  pending = null;
  seen = [];
  client = new QueryClient({
    // The three feeds ship `retry: 2`; a test that waited out two backoffs would be timing a
    // library, and no test here asks a request to fail.
    defaultOptions: { queries: { retry: false } },
  });
});

afterEach(() => {
  cleanup();
  client.clear();
});

describe.each(SCOPES)("$name's empty state across a refresh", (scope) => {
  it("says nothing until the first answer lands, then keeps saying it through a refresh", async () => {
    const view = renderHook(() => scope.useProbe(), { wrapper });

    // AWAITING_FIRST. Nobody has been told there are no tasks, because nobody knows yet — the
    // skeleton owns the screen instead. Getting this wrong is the worse half of the bug.
    expect(view.result.current.showEmpty).toBe(false);
    expect(view.result.current.showSkeleton).toBe(true);

    // The answer arrives and it is "nothing". NOW the scene is earned.
    await answerWith(scope.empty);
    await waitFor(() => expect(view.result.current.showEmpty).toBe(true));
    expect(view.result.current.showSkeleton).toBe(false);

    // The gesture from the report. Everything below here is one refresh over an answer that is
    // already on screen.
    const beforeRefresh = seen.length;
    act(() => {
      void client.invalidateQueries({ queryKey: [...scope.queryKey] });
    });
    await waitFor(() => expect(view.result.current.isFetching).toBe(true));

    // Mid-flight: screenshot 2, and the assertion that it never happens here. The scene is still
    // up and the skeleton has NOT come back to replace it.
    expect(view.result.current.showEmpty).toBe(true);
    expect(view.result.current.showSkeleton).toBe(false);

    // The refresh returns with nothing new, which is the case the user hit.
    await answerWith(scope.empty);
    await waitFor(() => expect(view.result.current.isFetching).toBe(false));
    expect(view.result.current.showEmpty).toBe(true);

    // And it was up for every committed frame in between. A pair of before/after assertions
    // would step straight over a single-frame withdrawal; this is the part that cannot.
    expect(seen.slice(beforeRefresh)).not.toContain(false);
  });

  it("still goes away the moment a task actually arrives", async () => {
    const view = renderHook(() => scope.useProbe(), { wrapper });

    await answerWith(scope.empty);
    await waitFor(() => expect(view.result.current.showEmpty).toBe(true));

    // The guard on the fix itself: holding the scene through a refresh must not be spelled as
    // holding it unconditionally. A row landing is the one thing that takes it away, and it is a
    // row landing rather than the request ending that does it.
    act(() => {
      void client.invalidateQueries({ queryKey: [...scope.queryKey] });
    });
    await waitFor(() => expect(view.result.current.isFetching).toBe(true));
    await answerWith(scope.populated);

    await waitFor(() => expect(view.result.current.showEmpty).toBe(false));
    expect(view.result.current.showSkeleton).toBe(false);
  });
});

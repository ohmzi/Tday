// @vitest-environment jsdom

/**
 * A batch of completions must play once.
 *
 * `complete-todo-refetch-window.test.tsx` pins the single-row window and
 * `staged-rows-across-surfaces.test.tsx` pins the three single-row hooks. Neither
 * touches `useBulkTodoActions`, which is where the user-visible report comes from:
 * completing a lot of tasks at once makes them come back for a second and then
 * strike out and leave again.
 *
 * The mechanism this file pins, and why it is N-shaped rather than a different
 * bug:
 *
 * - The batch is a bounded fan-out of N single-item requests
 *   (`run-bulk-fan-out.ts`, `BULK_MAX_CONCURRENCY = 4`, `BULK_MAX_SELECTION = 100`),
 *   so a large selection keeps its undo window open for seconds — long enough for
 *   a read the *window* started, when the server still listed every row pending,
 *   to still be in flight when the release lands.
 * - The batch released its claim for the whole set in one call *before* the
 *   refetch it authorises (`releaseTodoRows` then `refreshTodoViews`), so that
 *   read was no longer filtered: it wrote every row back at once. The batch's own
 *   post-release refetch then answered with the completed truth and took them away
 *   again.
 *
 * The fix is not to stop playing anything — the choreography, the prune and the
 * optimistic write are the feature. It is to release the claim only once the read
 * path can no longer answer with the window's truth: an in-flight row-list fetch
 * is cancelled at the settle (`settleTodoRows`) before the ids are handed back,
 * so a row the user staged away cannot be re-inserted by a request that started
 * before the commit.
 *
 * The two assertions below are the two halves of that: the row never comes back
 * (no second play), and the tap's prune reaches every cache the claim covers
 * (so the row's first departure is one departure, not a delayed one).
 */

import type { ReactNode } from "react";
import { act, renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider, useQuery } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";

const patchMock = vi.fn();
const toastMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  api: {
    PATCH: (...args: unknown[]) => patchMock(...args),
    DELETE: vi.fn(),
  },
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({ toast: (...args: unknown[]) => toastMock(...args) }),
}));

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, unknown>) =>
      options && "count" in options ? `${key}:${String(options.count)}` : key,
  }),
}));

vi.mock("@/lib/observability/sentry", () => ({
  addDiagnosticBreadcrumb: vi.fn(),
}));

import { useBulkTodoActions } from "@/hooks/use-bulk-todo-actions";

type CapturedToast = {
  description: string;
  action?: { onClick: () => void };
  onAutoClose?: () => void;
  onDismiss?: () => void;
};

const toastCalls = () =>
  toastMock.mock.calls.map(([options]) => options as CapturedToast);

const undoableToast = () => {
  const found = toastCalls().find((options) => Boolean(options.action));
  if (!found) throw new Error("no undoable toast was shown");
  return found;
};

function buildTodo(index: number): TodoItemType {
  return {
    id: `todo-${index}:null`,
    title: `Task ${index}`,
    description: null,
    pinned: false,
    createdAt: new Date("2026-08-01T09:00:00.000Z"),
    order: index,
    priority: "Low",
    due: new Date("2026-08-20T09:00:00.000Z"),
    rrule: null,
    timeZone: "UTC",
    userID: "user-1",
    completed: false,
    exdates: [],
    instanceDate: null,
    listID: "list-a",
    instances: [],
  };
}

const BATCH = [buildTodo(1), buildTodo(2), buildTodo(3), buildTodo(4), buildTodo(5)];
const SURVIVOR = buildTodo(9);
const BATCH_IDS = new Set(BATCH.map((row) => row.id));

const TIMELINE_KEY = ["todoTimeline"] as const;
const CALENDAR_KEY = ["calendarTodo", 0, 1] as const;
const FLOATER_LIST_KEY = ["floaterList", "list-a"] as const;

/**
 * The stand-in server, and the whole point of the file.
 *
 * Call 1 fails, so the cache entry is cold — `state.data` is undefined. That is
 * the state the race needs, and it is not exotic: a first load that errored, a
 * screen whose cache was cleared, or a query React Query has reverted after a
 * cancellation all leave an active reader with no data and an empty
 * `revertState`, and an active reader with no data is exactly the case where a
 * second fetch does not supersede the one already in flight — it *joins* it and
 * inherits its answer.
 *
 * Call 2 is handed out but never resolved: it is the read the *window* started,
 * and the server answered it with every row still pending because the batch had
 * told it nothing yet. The test resolves it by hand, after the commit, to stand
 * in for the network landing late.
 * Anything after that is a read taken once the commit has been told, so the
 * completed rows are gone.
 */
let heldResolver: ((rows: TodoItemType[]) => void) | null = null;
let timelineCalls = 0;

function timelineQueryFn(): Promise<TodoItemType[]> {
  timelineCalls += 1;
  if (timelineCalls === 1) {
    return Promise.reject(new Error("cold cache"));
  }
  if (timelineCalls === 2) {
    return new Promise<TodoItemType[]>((resolve) => {
      heldResolver = resolve;
    });
  }
  return Promise.resolve([SURVIVOR]);
}

function coldQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function wrapperFor(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  };
}

/** Every value `["todoTimeline"]` ever held, as row ids, from the tracker on. */
function trackTimeline(queryClient: QueryClient) {
  const seen: string[][] = [];
  queryClient.getQueryCache().subscribe((event) => {
    if (event.type !== "updated" && event.type !== "added") return;
    if (event.query.queryKey[0] !== "todoTimeline") return;
    const data = event.query.state.data as TodoItemType[] | undefined;
    seen.push((data ?? []).map((row) => row.id));
  });
  return seen;
}

beforeEach(() => {
  patchMock.mockReset();
  toastMock.mockReset();
  patchMock.mockResolvedValue(undefined);
  heldResolver = null;
  timelineCalls = 0;
});

describe("bulk complete plays once", () => {
  it("never re-inserts a staged row when a read the window started lands after the commit", async () => {
    const queryClient = coldQueryClient();

    const { result } = renderHook(
      () => ({
        timeline: useQuery<TodoItemType[]>({
          queryKey: TIMELINE_KEY,
          queryFn: timelineQueryFn,
        }),
        actions: useBulkTodoActions(),
      }),
      { wrapper: wrapperFor(queryClient) },
    );

    // A cold, active reader: no data, an observer attached, `revertState` empty.
    await waitFor(() => expect(result.current.timeline.isError).toBe(true));

    await act(async () => {
      result.current.actions.completeSelected(BATCH);
    });

    const seen = trackTimeline(queryClient);

    // A read starts inside the undo window — a realtime echo, a focus refetch, a
    // sibling's `onSettled`. The server still lists every row as pending.
    await act(async () => {
      void queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      await Promise.resolve();
    });
    expect(heldResolver).not.toBeNull();

    // The window closes: the batch is sent.
    await act(async () => {
      undoableToast().onAutoClose?.();
      await Promise.resolve();
    });
    await waitFor(() => expect(patchMock).toHaveBeenCalledTimes(BATCH.length));

    // The read the window started lands now — after the commit. It carries the
    // pre-commit truth and must not be allowed to answer for these rows.
    await act(async () => {
      heldResolver?.([...BATCH, SURVIVOR]);
      await Promise.resolve();
      await Promise.resolve();
    });

    // A later read, taken after the server was told, still has to be able to
    // settle the view.
    await act(async () => {
      void queryClient.invalidateQueries({ queryKey: ["todoTimeline"] });
      await Promise.resolve();
    });

    await waitFor(() =>
      expect(
        queryClient.getQueryData<TodoItemType[]>(TIMELINE_KEY)?.map((row) => row.id),
      ).toEqual([SURVIVOR.id]),
    );

    // The whole point: from the tap to the end, no row the batch staged away is
    // ever in the live cache again. One departure, not "leave, come back, cross
    // out, leave".
    const readmitted = seen.filter((ids) =>
      ids.some((id) => BATCH_IDS.has(id)),
    );
    expect(readmitted).toEqual([]);
  });

  it("takes the batch out of every cache the claim covers, not only the timeline pair", async () => {
    const queryClient = coldQueryClient();
    queryClient.setQueryData<TodoItemType[]>(TIMELINE_KEY, [...BATCH, SURVIVOR]);
    queryClient.setQueryData<TodoItemType[]>(CALENDAR_KEY, [...BATCH, SURVIVOR]);
    queryClient.setQueryData<{ list: unknown; floaters: TodoItemType[] }>(
      FLOATER_LIST_KEY,
      { list: { id: "list-a" }, floaters: [...BATCH, SURVIVOR] },
    );

    const { result } = renderHook(() => useBulkTodoActions(), {
      wrapper: wrapperFor(queryClient),
    });

    await act(async () => {
      result.current.completeSelected(BATCH);
    });

    // A root the claim defends but the prune never wrote keeps the row until
    // something happens to write it, which is a departure the user did not ask
    // for and cannot be undone by the toast they are looking at.
    expect(
      queryClient.getQueryData<TodoItemType[]>(CALENDAR_KEY)?.map((row) => row.id),
    ).toEqual([SURVIVOR.id]);
    expect(
      queryClient
        .getQueryData<{ list: unknown; floaters: TodoItemType[] }>(FLOATER_LIST_KEY)
        ?.floaters.map((row) => row.id),
    ).toEqual([SURVIVOR.id]);
    // The array roots keep their other members too.
    expect(
      queryClient.getQueryData<TodoItemType[]>(TIMELINE_KEY)?.map((row) => row.id),
    ).toEqual([SURVIVOR.id]);
  });
});

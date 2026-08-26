// @vitest-environment jsdom

import type { ReactNode } from "react";
import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { TodoItemType } from "@/types";

const patchMock = vi.fn();
const deleteMock = vi.fn();
const toastMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  api: {
    PATCH: (...args: unknown[]) => patchMock(...args),
    DELETE: (...args: unknown[]) => deleteMock(...args),
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
  variant?: string;
  action?: { label: string; onClick: () => void };
  onAutoClose?: () => void;
  onDismiss?: () => void;
};

const toastCalls = () =>
  toastMock.mock.calls.map(([options]) => options as CapturedToast);

/** The undoable batch toast is the one carrying an Undo action. */
const undoableToast = () => {
  const found = toastCalls().find((options) => Boolean(options.action));
  if (!found) throw new Error("no undoable toast was shown");
  return found;
};

const errorToasts = () =>
  toastCalls().filter((options) => options.variant === "destructive");

function buildTodo(overrides: Partial<TodoItemType> = {}): TodoItemType {
  return {
    id: "todo-1:null",
    title: "Ship the web surface",
    description: null,
    pinned: false,
    createdAt: new Date("2026-08-01T09:00:00.000Z"),
    order: 1,
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
    ...overrides,
  };
}

function buildBatch(count: number) {
  return Array.from({ length: count }, (_, index) =>
    buildTodo({ id: `todo-${index}:null`, title: `Task ${index}` }),
  );
}

function renderActions(
  queryClient: QueryClient,
  options: { scopeListId?: string | null } = {},
) {
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  }
  return renderHook(() => useBulkTodoActions(options), { wrapper: Wrapper });
}

function coldQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

describe("bulk todo actions", () => {
  beforeEach(() => {
    patchMock.mockReset();
    deleteMock.mockReset();
    toastMock.mockReset();
    patchMock.mockResolvedValue(undefined);
    deleteMock.mockResolvedValue(undefined);
  });

  it("fires one request per row once the complete toast settles", async () => {
    const queryClient = coldQueryClient();
    const rows = buildBatch(5);
    const { result } = renderActions(queryClient);

    result.current.completeSelected(rows);

    // Delayed commit: staged only, nothing sent.
    expect(patchMock).not.toHaveBeenCalled();
    expect(undoableToast().description).toBe("tasksCompleted:5");

    undoableToast().onAutoClose?.();

    // A passing status is not proof: assert the transport actually ran N times,
    // which is what an aborted optimistic updater would silently skip.
    await waitFor(() => expect(patchMock).toHaveBeenCalledTimes(5));
    expect(
      patchMock.mock.calls.every(([call]) => call.url === "/api/todo/complete"),
    ).toBe(true);
  });

  it("sends the occurrence's instanceDate for a recurring complete", async () => {
    const queryClient = coldQueryClient();
    const instanceDate = new Date("2026-08-20T09:00:00.000Z");
    const rows = [
      buildTodo({ id: "todo-r:123", rrule: "FREQ=DAILY", instanceDate }),
      buildTodo({ id: "todo-p:null" }),
    ];
    const { result } = renderActions(queryClient);

    result.current.completeSelected(rows);
    undoableToast().onAutoClose?.();

    await waitFor(() => expect(patchMock).toHaveBeenCalledTimes(2));
    const bodies = patchMock.mock.calls.map(([call]) => JSON.parse(call.body));
    const recurring = bodies.find((body) => body.id === "todo-r");
    const plain = bodies.find((body) => body.id === "todo-p");

    // Without it the backend writes a history row, marks nothing complete, and
    // leaves the task on screen.
    expect(recurring.instanceDate).toBe(instanceDate.toISOString());
    expect(plain.instanceDate).toBeNull();
  });

  it("sends nothing for a delete until the toast settles, and nothing at all on undo", async () => {
    const queryClient = coldQueryClient();
    const rows = buildBatch(3);
    const { result } = renderActions(queryClient);

    result.current.deleteSelected(rows);
    expect(deleteMock).not.toHaveBeenCalled();

    const toast = undoableToast();
    expect(toast.description).toBe("tasksDeleted:3");
    toast.action?.onClick();
    toast.onAutoClose?.();
    toast.onDismiss?.();

    await Promise.resolve();
    expect(deleteMock).not.toHaveBeenCalled();
  });

  it("deletes every row once, by canonical id, after the undo window", async () => {
    const queryClient = coldQueryClient();
    const rows = buildBatch(4);
    const { result } = renderActions(queryClient);

    result.current.deleteSelected(rows);
    undoableToast().onAutoClose?.();

    await waitFor(() => expect(deleteMock).toHaveBeenCalledTimes(4));
    const ids = deleteMock.mock.calls
      .map(([call]) => JSON.parse(call.body).id)
      .sort();
    expect(ids).toEqual(["todo-0", "todo-1", "todo-2", "todo-3"]);
  });

  it("shows exactly one error toast for a partly failed batch", async () => {
    const queryClient = coldQueryClient();
    const rows = buildBatch(4);
    deleteMock.mockImplementation(async (call: { body: string }) => {
      const { id } = JSON.parse(call.body);
      if (id === "todo-1" || id === "todo-2") throw new Error("boom");
    });
    const { result } = renderActions(queryClient);

    result.current.deleteSelected(rows);
    undoableToast().onAutoClose?.();

    await waitFor(() => expect(errorToasts()).toHaveLength(1));
    // Never one toast per item, and never a claim of success.
    expect(errorToasts()[0].description).toBe("bulkDeleteFailed:2");
  });

  it("prioritizes through the dedicated route without an instance date", async () => {
    const queryClient = coldQueryClient();
    const rows = buildBatch(3);
    const { result } = renderActions(queryClient);

    result.current.setPriorityForSelected(rows, "High");

    await waitFor(() => expect(patchMock).toHaveBeenCalledTimes(3));
    const calls = patchMock.mock.calls.map(([call]) => call);
    expect(calls.every((call) => call.url === "/api/todo/prioritize")).toBe(true);
    const body = JSON.parse(calls[0].body);
    expect(body.priority).toBe("High");
    expect(body.instanceDate).toBeNull();
    // An edit succeeds silently — no success toast, per the unified toast policy.
    expect(toastCalls()).toHaveLength(0);
  });

  it("moves without signalling a date or recurrence change", async () => {
    const queryClient = coldQueryClient();
    const rows = [buildTodo({ id: "todo-9:null", rrule: null })];
    const { result } = renderActions(queryClient);

    result.current.moveSelectedToList(rows, "list-b");

    await waitFor(() => expect(patchMock).toHaveBeenCalledTimes(1));
    const call = patchMock.mock.calls[0][0];
    expect(call.url).toBe("/api/todo");
    const body = JSON.parse(call.body);
    expect(body.listID).toBe("list-b");
    // patchTodo derives these from the checksums it is handed; getting them
    // wrong makes the backend reprocess the due date and the rrule.
    expect(body.dateChanged).toBe(false);
    expect(body.rruleChanged).toBe(false);
    expect(toastCalls()).toHaveLength(0);
  });

  it("sends \"\" and not null when moving to No list", async () => {
    // TodoRoutes does `body.listID?.let { fields["listID"] = ... }`, so a null
    // listID never lands in `fields` and TodoService.update leaves the list
    // alone. The row would clear optimistically and the next refetch would put
    // the old list back. Blank is the value the backend maps to null.
    const queryClient = coldQueryClient();
    const rows = [buildTodo({ id: "todo-9:null", rrule: null })];
    const { result } = renderActions(queryClient);

    result.current.moveSelectedToList(rows, null);

    await waitFor(() => expect(patchMock).toHaveBeenCalledTimes(1));
    const body = JSON.parse(patchMock.mock.calls[0][0].body);
    expect(body.listID).toBe("");
    expect(body.listID).not.toBeNull();
  });

  it("survives a cold cache on every action", async () => {
    // The hazard this pins: an updater annotated `(old: TodoItemType[] = [])`
    // type-checks, then throws on a cache react-query has never filled — inside
    // onMutate that aborts the mutation and looks exactly like a network error.
    const rows = buildBatch(2);

    for (const run of [
      (actions: ReturnType<typeof useBulkTodoActions>) =>
        actions.completeSelected(rows),
      (actions: ReturnType<typeof useBulkTodoActions>) =>
        actions.deleteSelected(rows),
      (actions: ReturnType<typeof useBulkTodoActions>) =>
        actions.setPriorityForSelected(rows, "Medium"),
      (actions: ReturnType<typeof useBulkTodoActions>) =>
        actions.moveSelectedToList(rows, null),
    ]) {
      patchMock.mockClear();
      deleteMock.mockClear();
      toastMock.mockClear();
      const queryClient = coldQueryClient();
      const { result } = renderActions(queryClient);

      expect(() => run(result.current)).not.toThrow();
      // Nothing was written into caches the screen never filled.
      expect(queryClient.getQueryData(["todo"])).toBeUndefined();
      expect(queryClient.getQueryData(["todoTimeline"])).toBeUndefined();

      const staged = toastCalls().find((options) => Boolean(options.action));
      staged?.onAutoClose?.();
      await waitFor(() =>
        expect(patchMock.mock.calls.length + deleteMock.mock.calls.length).toBe(2),
      );
      expect(errorToasts()).toHaveLength(0);
    }
  });

  it("prunes a warm cache without touching the rows it was not given", () => {
    const queryClient = coldQueryClient();
    const rows = buildBatch(2);
    const survivor = buildTodo({ id: "todo-keep:null" });
    queryClient.setQueryData(["todoTimeline"], [...rows, survivor]);
    queryClient.setQueryData(["list", "list-a"], [...rows, survivor]);
    const { result } = renderActions(queryClient, { scopeListId: "list-a" });

    result.current.deleteSelected(rows);

    expect(
      queryClient.getQueryData<TodoItemType[]>(["todoTimeline"])?.map((t) => t.id),
    ).toEqual(["todo-keep:null"]);
    // Prefix match, because list rows carry no listID to key ["list", id] from.
    expect(
      queryClient.getQueryData<TodoItemType[]>(["list", "list-a"])?.map((t) => t.id),
    ).toEqual(["todo-keep:null"]);
  });

  it("leaves the listMetaData map alone when pruning by ['list'] prefix", () => {
    const queryClient = coldQueryClient();
    const rows = buildBatch(1);
    // A map, not an array: if the prefix filter ever matched this key, the
    // updater would call .filter on an object and throw mid-action.
    queryClient.setQueryData(["listMetaData"], { "list-a": { name: "Work" } });
    const { result } = renderActions(queryClient);

    expect(() => result.current.deleteSelected(rows)).not.toThrow();
    expect(queryClient.getQueryData(["listMetaData"])).toEqual({
      "list-a": { name: "Work" },
    });
  });

  it("takes moved rows off the list screen they were scoped to", () => {
    const queryClient = coldQueryClient();
    // Rows from /api/list/:id carry listID === null — ListTodoDto has no such
    // field — so only the screen's own id knows where they came from.
    const rows = [
      buildTodo({ id: "todo-0:null", listID: null }),
      buildTodo({ id: "todo-1:null", listID: null }),
    ];
    queryClient.setQueryData(["list", "list-a"], rows);
    const { result } = renderActions(queryClient, { scopeListId: "list-a" });

    result.current.moveSelectedToList(rows, "list-b");

    expect(queryClient.getQueryData<TodoItemType[]>(["list", "list-a"])).toEqual([]);
  });
});

// @vitest-environment jsdom

import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";

const patchMock = vi.fn();
const postMock = vi.fn();

vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));
vi.mock("@/lib/api-client", () => ({
  api: {
    PATCH: (...args: unknown[]) => patchMock(...args),
    POST: (...args: unknown[]) => postMock(...args),
  },
}));

import { useEditFloater } from "@/features/floater/query/update-floater";
import { usePromoteFloater } from "@/features/floater/query/promote-floater";
import type { FloaterListCache } from "@/features/floaterList/query/get-floater-list";
import type { FloaterItemType } from "@/types";

// `["floaterList", id]` holds a `FloaterListCache` (`{ list, floaters }`), not a
// bare array. These tests seed that shape and check the floater writers keep it.

function makeFloater(overrides: Partial<FloaterItemType> = {}): FloaterItemType {
  return {
    id: "floater-1",
    title: "Buy stamps",
    description: null,
    pinned: false,
    createdAt: new Date("2026-10-01T00:00:00Z"),
    updatedAt: null,
    order: 1,
    priority: "Low",
    userID: "user-1",
    completed: false,
    listID: null,
    ...overrides,
  };
}

function listCache(listID: string, name: string, floaters: FloaterItemType[]): FloaterListCache {
  return { list: { id: listID, name }, floaters };
}

// onMutate awaits cancelQueries before its optimistic writes, so the writes land a
// few ticks after `mutate`/`mutateFn` returns.
async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function createClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

function wrapperFor(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

function floaterIDs(cache: FloaterListCache | undefined) {
  return cache?.floaters.map((floater) => floater.id);
}

describe("floater writers against the floaterList cache shape", () => {
  afterEach(() => {
    patchMock.mockReset();
    postMock.mockReset();
  });

  it("editing a floater into a cached list appends it to .floaters", async () => {
    patchMock.mockResolvedValue({ message: "floater updated" });
    const queryClient = createClient();
    const target = listCache("list-2", "Errands", [makeFloater({ id: "other", listID: "list-2" })]);
    queryClient.setQueryData(["floater"], [makeFloater()]);
    queryClient.setQueryData(["floaterList", "list-2"], target);

    const { result } = renderHook(() => useEditFloater(), {
      wrapper: wrapperFor(queryClient),
    });

    result.current.editTodoMutateFn(makeFloater({ title: "Buy more stamps", listID: "list-2" }));
    await flush();
    // onMutate runs before mutationFn. If the optimistic write threw, the PATCH
    // would never be sent, so this also shows the write completed.
    expect(patchMock).toHaveBeenCalledTimes(1);

    const cached = queryClient.getQueryData<FloaterListCache>(["floaterList", "list-2"]);
    expect(cached?.list).toEqual(target.list);
    expect(floaterIDs(cached)).toEqual(["other", "floater-1"]);
    expect(cached?.floaters[1]?.title).toBe("Buy more stamps");
  });

  it("edits an existing row inside the cached floaterList object", async () => {
    patchMock.mockResolvedValue({ message: "floater updated" });
    const queryClient = createClient();
    queryClient.setQueryData(["floater"], [makeFloater({ listID: "list-2" })]);
    queryClient.setQueryData(
      ["floaterList", "list-2"],
      listCache("list-2", "Errands", [makeFloater({ listID: "list-2" })]),
    );

    const { result } = renderHook(() => useEditFloater(), {
      wrapper: wrapperFor(queryClient),
    });

    result.current.editTodoMutateFn(makeFloater({ title: "Renamed", listID: "list-2" }));
    await flush();

    const cached = queryClient.getQueryData<FloaterListCache>(["floaterList", "list-2"]);
    expect(cached?.floaters).toHaveLength(1);
    expect(cached?.floaters[0]?.title).toBe("Renamed");
  });

  it("leaves unloaded root and floaterList caches unloaded instead of writing []", async () => {
    patchMock.mockResolvedValue({ message: "floater updated" });
    const queryClient = createClient();

    const { result } = renderHook(() => useEditFloater(), {
      wrapper: wrapperFor(queryClient),
    });

    result.current.editTodoMutateFn(makeFloater({ listID: "list-9" }));
    await flush();

    expect(patchMock).toHaveBeenCalledTimes(1);
    expect(queryClient.getQueryData(["floater"])).toBeUndefined();
    expect(queryClient.getQueryData(["floaterList", "list-9"])).toBeUndefined();
  });

  it("moving a floater from list A to list B removes it from A and adds it to B", async () => {
    patchMock.mockResolvedValue({ message: "floater updated" });
    const queryClient = createClient();
    queryClient.setQueryData(["floater"], [makeFloater({ listID: "list-a" })]);
    queryClient.setQueryData(
      ["floaterList", "list-a"],
      listCache("list-a", "Alpha", [
        makeFloater({ listID: "list-a" }),
        makeFloater({ id: "keep-a", listID: "list-a" }),
      ]),
    );
    queryClient.setQueryData(
      ["floaterList", "list-b"],
      listCache("list-b", "Beta", [makeFloater({ id: "other", listID: "list-b" })]),
    );

    const { result } = renderHook(() => useEditFloater(), {
      wrapper: wrapperFor(queryClient),
    });

    result.current.editTodoMutateFn(makeFloater({ listID: "list-b" }));
    await flush();

    const from = queryClient.getQueryData<FloaterListCache>(["floaterList", "list-a"]);
    const to = queryClient.getQueryData<FloaterListCache>(["floaterList", "list-b"]);
    expect(floaterIDs(from)).toEqual(["keep-a"]);
    expect(floaterIDs(to)).toEqual(["other", "floater-1"]);
    expect(to?.floaters[1]?.listID).toBe("list-b");
  });

  it("restores both list caches and the root when the move's PATCH fails", async () => {
    patchMock.mockRejectedValue(new Error("boom"));
    const queryClient = createClient();
    const rootSeed = [makeFloater({ listID: "list-a" })];
    const listASeed = listCache("list-a", "Alpha", [
      makeFloater({ listID: "list-a" }),
      makeFloater({ id: "keep-a", listID: "list-a" }),
    ]);
    const listBSeed = listCache("list-b", "Beta", [makeFloater({ id: "other", listID: "list-b" })]);
    queryClient.setQueryData(["floater"], rootSeed);
    queryClient.setQueryData(["floaterList", "list-a"], listASeed);
    queryClient.setQueryData(["floaterList", "list-b"], listBSeed);

    const { result } = renderHook(() => useEditFloater(), {
      wrapper: wrapperFor(queryClient),
    });

    result.current.editTodoMutateFn(makeFloater({ listID: "list-b" }));
    await waitFor(() => expect(result.current.editTodoStatus).toBe("error"));

    expect(queryClient.getQueryData(["floater"])).toEqual(rootSeed);
    expect(queryClient.getQueryData(["floaterList", "list-a"])).toEqual(listASeed);
    expect(queryClient.getQueryData(["floaterList", "list-b"])).toEqual(listBSeed);
  });

  it("promoting a floater prunes it from the cached floaterList object", async () => {
    postMock.mockResolvedValue({ message: "promoted" });
    const queryClient = createClient();
    queryClient.setQueryData(["floater"], [makeFloater({ listID: "list-2" })]);
    queryClient.setQueryData(
      ["floaterList", "list-2"],
      listCache("list-2", "Errands", [
        makeFloater({ listID: "list-2" }),
        makeFloater({ id: "keep", listID: "list-2" }),
      ]),
    );
    const { result } = renderHook(() => usePromoteFloater(), {
      wrapper: wrapperFor(queryClient),
    });

    result.current.promoteMutateFn({
      floater: makeFloater({ listID: "list-2" }),
      due: new Date("2026-11-01T00:00:00Z"),
    });
    await flush();

    const cached = queryClient.getQueryData<FloaterListCache>(["floaterList", "list-2"]);
    expect(floaterIDs(cached)).toEqual(["keep"]);
    expect(cached?.list).toEqual({ id: "list-2", name: "Errands" });
  });

  it("restores the {list, floaters} object when the promote request fails", async () => {
    postMock.mockRejectedValue(new Error("boom"));
    const queryClient = createClient();
    const rootSeed = [makeFloater({ listID: "list-2" })];
    const listSeed = listCache("list-2", "Errands", [
      makeFloater({ listID: "list-2" }),
      makeFloater({ id: "keep", listID: "list-2" }),
    ]);
    queryClient.setQueryData(["floater"], rootSeed);
    queryClient.setQueryData(["floaterList", "list-2"], listSeed);
    const { result } = renderHook(() => usePromoteFloater(), {
      wrapper: wrapperFor(queryClient),
    });

    result.current.promoteMutateFn({
      floater: makeFloater({ listID: "list-2" }),
      due: new Date("2026-11-01T00:00:00Z"),
    });
    // postMock runs only after onMutate has pruned, so waiting for the restore
    // proves the prune happened and was then rolled back.
    await waitFor(() =>
      expect(queryClient.getQueryData(["floaterList", "list-2"])).toEqual(listSeed),
    );
    expect(postMock).toHaveBeenCalledTimes(1);
    expect(queryClient.getQueryData(["floater"])).toEqual(rootSeed);
  });
});

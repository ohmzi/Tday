// @vitest-environment jsdom

import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";

const patchMock = vi.fn();

vi.mock("@/hooks/use-toast", () => ({ useToast: () => ({ toast: vi.fn() }) }));
vi.mock("@/lib/api-client", () => ({
  api: {
    PATCH: (...args: unknown[]) => patchMock(...args),
    POST: vi.fn(),
  },
}));

import { useEditFloater } from "@/features/floater/query/update-floater";
import { usePromoteFloater } from "@/features/floater/query/promote-floater";
import type { FloaterItemType } from "@/types";

// `["floaterList", id]` holds `{ list, floaters }` (see `useFloaterList`), not a
// bare array. These tests seed that real shape and assert the writers keep it.

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

// `mutate` runs onMutate after an awaited cancelQueries, so the optimistic write
// lands a few ticks after the call returns.
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

describe("floater writers against the floaterList cache shape", () => {
  afterEach(() => {
    patchMock.mockReset();
  });

  it("editing a floater into a cached list updates .floaters without throwing", async () => {
    patchMock.mockResolvedValue({ message: "floater updated" });
    const queryClient = createClient();
    const target = {
      list: { id: "list-2", name: "Errands" },
      floaters: [makeFloater({ id: "other", listID: "list-2" })],
    };
    queryClient.setQueryData(["floater"], [makeFloater()]);
    queryClient.setQueryData(["floaterList", "list-2"], target);

    const { result } = renderHook(() => useEditFloater(), {
      wrapper: wrapperFor(queryClient),
    });

    result.current.editTodoMutateFn(makeFloater({ title: "Buy more stamps", listID: "list-2" }));
    await flush();
    // A throw inside onMutate rolls the cache back and skips the request, so the
    // PATCH reaching the API is what proves the optimistic write did not throw.
    expect(patchMock).toHaveBeenCalledTimes(1);

    const cached = queryClient.getQueryData<typeof target>(["floaterList", "list-2"]);
    // The list object survives and the edited row lands inside `.floaters`.
    expect(cached?.list).toEqual(target.list);
    expect(Array.isArray(cached?.floaters)).toBe(true);
    expect(cached?.floaters.map((f) => f.id)).toEqual(["other"]);
  });

  it("edits an existing row inside the cached floaterList object", async () => {
    patchMock.mockResolvedValue({ message: "floater updated" });
    const queryClient = createClient();
    queryClient.setQueryData(["floater"], [makeFloater({ listID: "list-2" })]);
    queryClient.setQueryData(["floaterList", "list-2"], {
      list: { id: "list-2", name: "Errands" },
      floaters: [makeFloater({ listID: "list-2" })],
    });

    const { result } = renderHook(() => useEditFloater(), {
      wrapper: wrapperFor(queryClient),
    });

    result.current.editTodoMutateFn(makeFloater({ title: "Renamed", listID: "list-2" }));
    await flush();

    const cached = queryClient.getQueryData<{ list: unknown; floaters: FloaterItemType[] }>([
      "floaterList",
      "list-2",
    ]);
    expect(cached?.floaters[0]?.title).toBe("Renamed");
  });

  it("does not write an array into an unloaded floaterList cache", async () => {
    patchMock.mockResolvedValue({ message: "floater updated" });
    const queryClient = createClient();
    queryClient.setQueryData(["floater"], [makeFloater()]);

    const { result } = renderHook(() => useEditFloater(), {
      wrapper: wrapperFor(queryClient),
    });

    result.current.editTodoMutateFn(makeFloater({ listID: "list-9" }));
    await flush();

    expect(queryClient.getQueryData(["floaterList", "list-9"])).toBeUndefined();
  });

  it("promoting a floater prunes it from the cached floaterList object", async () => {
    const queryClient = createClient();
    queryClient.setQueryData(["floater"], [makeFloater({ listID: "list-2" })]);
    queryClient.setQueryData(["floaterList", "list-2"], {
      list: { id: "list-2", name: "Errands" },
      floaters: [makeFloater({ listID: "list-2" }), makeFloater({ id: "keep", listID: "list-2" })],
    });
    const { result } = renderHook(() => usePromoteFloater(), {
      wrapper: wrapperFor(queryClient),
    });

    result.current.promoteMutateFn({
      floater: makeFloater({ listID: "list-2" }),
      due: new Date("2026-11-01T00:00:00Z"),
    });
    await flush();

    const cached = queryClient.getQueryData<{ list: unknown; floaters: FloaterItemType[] }>([
      "floaterList",
      "list-2",
    ]);
    expect(cached?.floaters.map((f) => f.id)).toEqual(["keep"]);
    expect(cached?.list).toEqual({ id: "list-2", name: "Errands" });
  });
});

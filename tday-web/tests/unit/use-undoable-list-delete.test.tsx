// @vitest-environment jsdom

import type { ReactNode } from "react";
import { act, renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

const deleteMock = vi.fn();
const toastMock = vi.fn();
const pushMock = vi.fn();
const setActiveMenuMock = vi.fn();
let pathnameMock = "/app/tday";

vi.mock("@/lib/api-client", () => ({
  api: {
    DELETE: (...args: unknown[]) => deleteMock(...args),
  },
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({
    toast: (...args: unknown[]) => toastMock(...args),
  }),
}));

vi.mock("@/lib/navigation", () => ({
  usePathname: () => pathnameMock,
  useRouter: () => ({ push: pushMock }),
}));

vi.mock("@/providers/MenuProvider", () => ({
  useMenu: () => ({ setActiveMenu: setActiveMenuMock }),
}));

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string, options?: { count?: number }) =>
      options?.count !== undefined ? `${key}:${options.count}` : key,
  }),
}));

import { useUndoableListDelete } from "@/hooks/use-undoable-list-delete";

type CapturedToast = {
  description: string;
  variant?: string;
  action?: { label: string; onClick: () => void };
  onAutoClose?: () => void;
  onDismiss?: () => void;
};

function undoToast(): CapturedToast {
  const captured = toastMock.mock.calls
    .map((call) => call[0] as CapturedToast)
    .find((options) => options.action);
  if (!captured) throw new Error("no undoable toast was shown");
  return captured;
}

function createClient() {
  return new QueryClient({
    defaultOptions: {
      mutations: { retry: false },
      queries: { retry: false },
    },
  });
}

function renderListDelete(queryClient: QueryClient) {
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  }
  return renderHook(() => useUndoableListDelete(), { wrapper: Wrapper });
}

const listMeta = (name: string) => ({ name, todoCount: 0 });

describe("useUndoableListDelete", () => {
  beforeEach(() => {
    deleteMock.mockReset();
    deleteMock.mockResolvedValue({});
    toastMock.mockClear();
    pushMock.mockClear();
    setActiveMenuMock.mockClear();
    pathnameMock = "/app/tday";
  });

  it("stages by pruning caches without sending a DELETE", () => {
    const queryClient = createClient();
    queryClient.setQueryData(["listMetaData"], {
      "list-1": listMeta("Groceries"),
      "list-2": listMeta("Errands"),
    });
    queryClient.setQueryData(["list", "list-1"], [{ id: "todo-1" }]);

    const { result } = renderListDelete(queryClient);
    act(() => result.current(["list-1"]));

    expect(deleteMock).not.toHaveBeenCalled();
    expect(queryClient.getQueryData(["listMetaData"])).toEqual({
      "list-2": listMeta("Errands"),
    });
    expect(queryClient.getQueryData(["list", "list-1"])).toBeUndefined();
    expect(undoToast().description).toBe("listDeleted");
  });

  it("commits a single delete exactly once when the toast closes", async () => {
    const queryClient = createClient();
    const { result } = renderListDelete(queryClient);
    act(() => result.current(["list-1"]));

    const toast = undoToast();
    await act(async () => {
      toast.onAutoClose?.();
      toast.onDismiss?.();
    });

    expect(deleteMock).toHaveBeenCalledTimes(1);
    expect(deleteMock).toHaveBeenCalledWith(
      expect.objectContaining({
        url: "/api/list",
        body: JSON.stringify({ id: "list-1", ids: ["list-1"] }),
      }),
    );
  });

  it("commits a bulk delete with plural copy and the bulk request shape", async () => {
    const queryClient = createClient();
    const { result } = renderListDelete(queryClient);
    act(() => result.current(["list-1", "list-2"]));

    const toast = undoToast();
    expect(toast.description).toBe("listsDeleted:2");

    await act(async () => {
      toast.onAutoClose?.();
    });

    expect(deleteMock).toHaveBeenCalledTimes(1);
    expect(deleteMock).toHaveBeenCalledWith(
      expect.objectContaining({
        url: "/api/list",
        body: JSON.stringify({ ids: ["list-1", "list-2"] }),
      }),
    );
  });

  it("undo refetches instead of ever sending the DELETE", async () => {
    const queryClient = createClient();
    queryClient.setQueryData(["listMetaData"], { "list-1": listMeta("Groceries") });
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");

    const { result } = renderListDelete(queryClient);
    act(() => result.current(["list-1"]));

    const toast = undoToast();
    await act(async () => {
      toast.action?.onClick();
      toast.onDismiss?.();
      toast.onAutoClose?.();
    });

    expect(deleteMock).not.toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["listMetaData"] });
  });

  it("leaves the deleted list's page at stage time", () => {
    pathnameMock = "/app/list/list-1";
    const queryClient = createClient();
    const { result } = renderListDelete(queryClient);

    act(() => result.current(["list-1", "list-2"]));

    expect(setActiveMenuMock).toHaveBeenCalledWith({ name: "Todo" });
    expect(pushMock).toHaveBeenCalledWith("/app/tday");
  });

  it("does not navigate when only a prefix-similar list is deleted", () => {
    pathnameMock = "/app/list/list-12";
    const queryClient = createClient();
    const { result } = renderListDelete(queryClient);

    act(() => result.current(["list-1"]));

    expect(setActiveMenuMock).not.toHaveBeenCalled();
    expect(pushMock).not.toHaveBeenCalled();
  });

  it("surfaces a destructive toast and refetches when the commit fails", async () => {
    deleteMock.mockRejectedValue(new Error("boom"));
    const queryClient = createClient();
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");

    const { result } = renderListDelete(queryClient);
    act(() => result.current(["list-1"]));

    const toast = undoToast();
    await act(async () => {
      toast.onAutoClose?.();
    });

    expect(toastMock).toHaveBeenCalledWith({
      description: "boom",
      variant: "destructive",
    });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["listMetaData"] });
  });
});

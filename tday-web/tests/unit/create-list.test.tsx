// @vitest-environment jsdom

import type { ReactNode } from "react";
import { act, renderHook } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";

const postMock = vi.fn();
const toastMock = vi.fn();

vi.mock("@/lib/api-client", () => ({
  api: {
    POST: (...args: unknown[]) => postMock(...args),
  },
}));

vi.mock("@/hooks/use-toast", () => ({
  useToast: () => ({
    toast: (...args: unknown[]) => toastMock(...args),
  }),
}));

import { useCreateList } from "@/components/Sidebar/List/query/create-list";

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      mutations: {
        retry: false,
      },
      queries: {
        retry: false,
      },
    },
  });

  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  };
}

describe("useCreateList", () => {
  it("sends the selected icon key when creating a list", async () => {
    postMock.mockResolvedValue({
      list: {
        id: "list-1",
        name: "Errands",
        color: "TEAL",
        iconKey: "cart",
        todoCount: 0,
      },
    });

    const { result } = renderHook(() => useCreateList(), {
      wrapper: createWrapper(),
    });

    await act(async () => {
      await result.current.createMutateAsync({
        name: "Errands",
        color: "TEAL",
        iconKey: "cart",
      });
    });

    expect(postMock).toHaveBeenCalledWith(
      expect.objectContaining({
        url: "/api/list",
        body: JSON.stringify({
          name: "Errands",
          color: "TEAL",
          iconKey: "cart",
        }),
      }),
    );
  });
});

// @vitest-environment jsdom

import type { ReactNode } from "react";
import { describe, expect, it } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook } from "@testing-library/react";
import { useUserTimezone } from "@/features/user/query/get-timezone";

function createWrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        {children}
      </QueryClientProvider>
    );
  };
}

describe("useUserTimezone", () => {
  it("falls back to the browser timezone when the cache is empty", () => {
    const queryClient = new QueryClient();

    const { result } = renderHook(() => useUserTimezone(), {
      wrapper: createWrapper(queryClient),
    });

    expect(result.current.timeZone).toBe(
      Intl.DateTimeFormat().resolvedOptions().timeZone,
    );
  });

  it("returns the cached timezone when one is available", () => {
    const queryClient = new QueryClient();
    queryClient.setQueryData(["userTimezone"], {
      timeZone: "America/Los_Angeles",
    });

    const { result } = renderHook(() => useUserTimezone(), {
      wrapper: createWrapper(queryClient),
    });

    expect(result.current.timeZone).toBe("America/Los_Angeles");
  });
});

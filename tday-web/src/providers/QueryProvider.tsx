import { QueryClient, QueryClientProvider, QueryCache } from "@tanstack/react-query";
import { toast } from "sonner";
import type { ReactNode } from "react";
import { ApiError } from "@/lib/api-client";

let browserQueryClient: QueryClient | undefined;

function getQueryClient(): QueryClient {
  if (!browserQueryClient) {
    browserQueryClient = new QueryClient({
      queryCache: new QueryCache({
        onError: (error) => {
          // 401s are owned by the session-expiry flow (AuthProvider shows a
          // dedicated toast and redirects to login) — don't double-toast here.
          if (error instanceof ApiError && error.status === 401) return;
          toast.error(error.message || "An error occurred");
        },
      }),
      defaultOptions: {
        queries: {
          staleTime: 60_000,
        },
      },
    });
  }
  return browserQueryClient;
}

export default function QueryProvider({ children }: { children: ReactNode }) {
  const queryClient = getQueryClient();
  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

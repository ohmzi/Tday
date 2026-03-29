import { QueryClient, QueryClientProvider, QueryCache } from "@tanstack/react-query";
import { toast } from "sonner";
import type { ReactNode } from "react";

let browserQueryClient: QueryClient | undefined;

function getQueryClient(): QueryClient {
  if (!browserQueryClient) {
    browserQueryClient = new QueryClient({
      queryCache: new QueryCache({
        onError: (error) => {
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

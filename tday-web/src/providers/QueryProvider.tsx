import {
  QueryCache,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { toast } from "sonner";
import type { ReactNode } from "react";
import { ApiError } from "@/lib/api-client";

let browserQueryClient: QueryClient | undefined;

// One deduped toast id so a burst of failed requests collapses into a single calm notice
// (a backend-down and an unreachable state are mutually exclusive — they replace each other).
const SERVER_STATUS_TOAST_ID = "server-status";

/**
 * Global query (read/sync) error handler. Distinguishes the two "can't sync" cases so the
 * user sees the right thing instead of a raw "Bad Gateway"/"Failed to fetch":
 *   - a 5xx (backend container or database down) → a "server error" message;
 *   - a network throw (request never reached the server) → a "can't reach the server" message,
 *     unless the browser itself is offline (ConnectivityGate owns that toast — skip to avoid doubling).
 * 401 stays owned by the session-expiry flow, and real 4xx client/validation errors keep their
 * specific message. Mutations keep their own per-feature onError toasts (a global mutation handler
 * here would double-toast alongside them).
 */
function reportQueryError(error: unknown): void {
  if (error instanceof ApiError && error.status === 401) return;
  if (error instanceof ApiError && error.status >= 500) {
    toast.error(
      "Server error — the backend or database may be down. Try again shortly.",
      { id: SERVER_STATUS_TOAST_ID },
    );
    return;
  }
  if (!(error instanceof ApiError)) {
    if (typeof navigator !== "undefined" && navigator.onLine === false) return;
    toast.error(
      "Can't reach the server — check that it's running and try again.",
      { id: SERVER_STATUS_TOAST_ID },
    );
    return;
  }
  const message = error instanceof Error ? error.message : "";
  toast.error(message || "An error occurred");
}

function getQueryClient(): QueryClient {
  if (!browserQueryClient) {
    browserQueryClient = new QueryClient({
      queryCache: new QueryCache({
        onError: (error) => reportQueryError(error),
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

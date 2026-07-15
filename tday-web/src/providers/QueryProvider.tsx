import {
  QueryCache,
  QueryClient,
  QueryClientProvider,
} from "@tanstack/react-query";
import { toast } from "sonner";
import type { ReactNode } from "react";
import { ApiError } from "@/lib/api-client";

let browserQueryClient: QueryClient | undefined;

// One deduped toast id so a burst of failed requests collapses into a single calm
// notice, matching ConnectivityGate's offline-toast pattern.
const SERVER_UNREACHABLE_TOAST_ID = "server-unreachable";

/**
 * True when the server is unreachable or unhealthy — the request never reached it
 * (a raw fetch throw is a TypeError, not an ApiError), or it answered with a 5xx: the
 * backend container is down/restarting (502/503/504) or the database is down (500).
 * Either way sync can't happen — the same condition the native apps show their offline
 * notice for, so web should say the same calm thing instead of a raw "Bad Gateway".
 */
function isServerUnreachable(error: unknown): boolean {
  if (!(error instanceof ApiError)) return true;
  return error.status >= 500;
}

/**
 * Global query (read/sync) error handler. Keeps 401 owned by the session-expiry flow,
 * shows one deduped "can't reach the server" toast for outages instead of a raw
 * "Bad Gateway"/"Failed to fetch", and preserves specific messages for real 4xx
 * client/validation errors. Web has no offline mutation queue, so the copy says we'll keep
 * retrying (React Query refetches on reconnect) rather than falsely promising unsaved
 * changes will sync. Mutations keep their own per-feature onError toasts (a global
 * mutation handler here would double-toast alongside them).
 */
function reportQueryError(error: unknown): void {
  if (error instanceof ApiError && error.status === 401) return;
  if (isServerUnreachable(error)) {
    toast.error(
      "Can't reach the server — it may be down or restarting. We'll keep retrying when it's back.",
      { id: SERVER_UNREACHABLE_TOAST_ID },
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

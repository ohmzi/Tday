import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useIsLocalMode } from "@/hooks/useAppMode";
import { useAuth } from "@/providers/AuthProvider";

/** Response of `GET`/`PATCH /api/admin/telemetry`: this server's own Sentry reporting. */
export type ServerTelemetryResponse = {
  /** Whether the server has a `SENTRY_DSN` at all. Without one there is nothing to switch. */
  dsnConfigured: boolean;
  enabled: boolean;
  /** ISO-8601 UTC time of the last change, or null if it was never changed. */
  updatedAt: string | null;
};

export const SERVER_TELEMETRY_QUERY_KEY = ["serverTelemetry"] as const;

/**
 * The server's error-report switch, for the one person who may flip it — or null when it is not on
 * offer: the viewer is not an admin, the workspace is Local Mode (no server to report for, and the
 * route would be answered from browser storage), the request has not come back, or the server has
 * no DSN. The Settings row exists exactly when this is non-null.
 */
export function useServerTelemetry(): ServerTelemetryResponse | null {
  const { user } = useAuth();
  const isLocalMode = useIsLocalMode();
  const mayConfigure = user?.role === "ADMIN" && !isLocalMode;
  const { data } = useQuery<ServerTelemetryResponse>({
    queryKey: SERVER_TELEMETRY_QUERY_KEY,
    enabled: mayConfigure,
    staleTime: 60 * 1000,
    retry: 1,
    queryFn: () => api.GET({ url: "/api/admin/telemetry" }),
  });
  // A disabled query still hands back what an earlier admin session cached, so the same check
  // that gates the request has to gate the answer too.
  return mayConfigure && data?.dsnConfigured ? data : null;
}

import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useIsLocalMode } from "@/hooks/useAppMode";
import { useAuth } from "@/providers/AuthProvider";

/**
 * Response of `GET`/`PATCH /api/admin/telemetry`: the instance-wide error-report answer an admin
 * gives, which covers this server and every browser using the web app.
 */
export type ServerTelemetryResponse = {
  /** Whether the server has a `SENTRY_DSN` at all. Without one there is nothing to switch. */
  dsnConfigured: boolean;
  enabled: boolean;
  /** ISO-8601 UTC time of the last change, or null if it was never changed. */
  updatedAt: string | null;
};

export const SERVER_TELEMETRY_QUERY_KEY = ["serverTelemetry"] as const;

/**
 * The admin's error-report switch — the one control for it — or null when the viewer may not
 * configure it: not an admin, Local Mode (no server to report for, and the route would be answered
 * from browser storage), the request has not come back, or the server has no DSN of its own. The
 * switch row exists when this is non-null; everyone else reads the state instead
 * (`useInstanceTelemetry`).
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

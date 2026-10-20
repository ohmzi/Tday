import { useQuery } from "@tanstack/react-query";
import { useIsLocalMode } from "@/hooks/useAppMode";
import { api } from "@/lib/api-client";
import { INSTANCE_TELEMETRY_URL } from "@/lib/privacy/instanceTelemetry";

/**
 * Response of the public `GET /api/instance/telemetry`: the instance-wide answer an admin gives
 * for error reports, without the DSN state. It carries no personal data, so a browser that has no
 * session may read it.
 */
export type InstanceTelemetryResponse = {
  enabled: boolean;
  /** ISO-8601 UTC time of the last change, or null if it was never answered. */
  updatedAt: string | null;
};

export const INSTANCE_TELEMETRY_QUERY_KEY = ["instanceTelemetry"] as const;

/**
 * The current state of the admin's error-report answer, for someone who may see it but not change
 * it. `enabled` is the caller's own reason to show it at all — this is the state a non-admin reads,
 * never a control.
 */
export function useInstanceTelemetry(enabled: boolean): InstanceTelemetryResponse | null {
  const isLocalMode = useIsLocalMode();
  const offered = enabled && !isLocalMode;
  const { data } = useQuery<InstanceTelemetryResponse>({
    queryKey: INSTANCE_TELEMETRY_QUERY_KEY,
    enabled: offered,
    staleTime: 60 * 1000,
    retry: 1,
    queryFn: () => api.GET({ url: INSTANCE_TELEMETRY_URL }),
  });
  return offered && data ? data : null;
}

import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useIsLocalMode } from "@/hooks/useAppMode";

/**
 * The backend's own version, for the release screen's Server row.
 *
 * `/api/mobile/probe` is the unauthenticated endpoint both natives read their
 * `backendVersion` from (`MobileProbeRoutes.kt`, whose `appVersion` falls back
 * to the backend's own version), and the same one the Settings About card
 * already asks. No session, no new route.
 *
 * `null` means "no answer yet, or no answer at all", and the row prints the
 * natives' "Unavailable" for it. Their third state — "Not connected", for a
 * build with no server configured — has no web equivalent: in Server Mode a
 * tab is always talking to a server, and in Local Mode the row is not drawn.
 */
export function useServerVersion(): string | null {
  const isLocalMode = useIsLocalMode();

  const { data } = useQuery({
    queryKey: ["serverVersion"],
    // Local Mode has no server to ask, and the row that would show the answer
    // is hidden there anyway.
    enabled: !isLocalMode,
    staleTime: 15 * 60 * 1000,
    retry: 1,
    queryFn: async () => {
      const res = await api.GET({ url: "/api/mobile/probe" });
      const version = res?.appVersion;
      return typeof version === "string" && version ? version : null;
    },
  });

  return data ?? null;
}

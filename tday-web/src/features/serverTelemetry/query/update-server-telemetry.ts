import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import {
  SERVER_TELEMETRY_QUERY_KEY,
  type ServerTelemetryResponse,
} from "./get-server-telemetry";

/** Turns the server's own error reports on or off. Admin only; the server refuses anyone else. */
export function useUpdateServerTelemetry() {
  const queryClient = useQueryClient();

  return useMutation<ServerTelemetryResponse, Error, boolean>({
    mutationFn: (enabled) =>
      api.PATCH({
        url: "/api/admin/telemetry",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ enabled }),
      }),
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: SERVER_TELEMETRY_QUERY_KEY });
    },
  });
}

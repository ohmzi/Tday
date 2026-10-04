import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { answerFrom, applyInstanceTelemetry } from "@/lib/privacy/instanceTelemetry";
import {
  SERVER_TELEMETRY_QUERY_KEY,
  type ServerTelemetryResponse,
} from "./get-server-telemetry";

/**
 * Turns error reports on or off for this server and the web app — one answer, one switch. Admin
 * only; the server refuses anyone else.
 *
 * The answer is the same one the browser's SDK gate reads, so a successful write is applied to it
 * here: switching reports on starts the SDK and switching them off stops it, with no reload.
 */
export function useUpdateServerTelemetry() {
  const queryClient = useQueryClient();

  return useMutation<ServerTelemetryResponse, Error, boolean>({
    mutationFn: (enabled) =>
      api.PATCH({
        url: "/api/admin/telemetry",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ enabled }),
      }),
    onSuccess: (result) => {
      applyInstanceTelemetry(answerFrom(result.enabled, result.updatedAt));
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: SERVER_TELEMETRY_QUERY_KEY });
    },
  });
}

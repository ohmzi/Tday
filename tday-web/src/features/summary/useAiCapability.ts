import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";

export type AiCapability = {
  aiSummaryConfigured: boolean;
  aiSummaryHealthy: boolean;
};

async function fetchAiCapability(): Promise<AiCapability> {
  const data = await api.GET({ url: "/api/app-settings" });
  return {
    aiSummaryConfigured: Boolean(data?.aiSummaryConfigured),
    aiSummaryHealthy: Boolean(data?.aiSummaryHealthy),
  };
}

/**
 * AI-summary capability advertised by the backend. `aiSummaryConfigured=false`
 * means AI was never configured — the source label is hidden and the summary
 * is always produced by the local (logic) path. Cached for a few minutes since
 * capability rarely changes within a session.
 */
export function useAiCapability() {
  return useQuery({
    queryKey: ["aiCapability"],
    queryFn: fetchAiCapability,
    staleTime: 5 * 60 * 1000,
  });
}

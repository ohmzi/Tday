import { useMutation } from "@tanstack/react-query";
import i18n from "@/i18n";
import { api } from "@/lib/api-client";
import { useUserTimezone } from "@/features/user/query/get-timezone";

export type SummaryMode =
  | "today"
  | "scheduled"
  | "all"
  | "priority"
  | "overdue"
  | "list"
  | "floater"
  | "week";

export type SummarySource = "ai" | "logic";

export type SummaryResult = {
  summary: string | null;
  source: SummarySource;
};

type SummaryResponse = {
  summary: string | null;
  source: SummarySource;
  mode: SummaryMode;
  taskCount: number;
  generatedAt: string;
  fallbackReason?: string | null;
  reason?: string | null;
};

/**
 * Requests a task summary from the backend. The web app is online-only — the
 * summary is always computed server-side (never client-side); a network/server
 * failure surfaces as a thrown error the caller can toast on.
 */
export function useSummary() {
  const userTimeZone = useUserTimezone();

  return useMutation<SummaryResult, Error, { mode: SummaryMode; listId?: string }>({
    mutationFn: async ({ mode, listId }) => {
      const data: SummaryResponse = await api.POST({
        url: "/api/todo/summary",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          mode,
          ...(listId ? { listId } : {}),
          timeZone: userTimeZone.timeZone,
          locale: i18n.language,
        }),
      });
      return { summary: data.summary, source: data.source };
    },
  });
}

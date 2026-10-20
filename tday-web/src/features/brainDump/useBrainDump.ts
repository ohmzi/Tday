import { useMutation } from "@tanstack/react-query";
import i18n from "@/i18n";
import { api } from "@/lib/api-client";
import { useUserTimezone } from "@/features/user/query/get-timezone";

export type BrainDumpCandidate = {
  title: string;
  dueEpochMs: number | null;
  rrule: string | null;
  priority: string | null;
};

/**
 * Sends a free-text blob to the backend, which splits it into candidate tasks
 * (dates via Natty, recurrence/priority via the shared grammar). The model never
 * invents timestamps — the split + grammar are deterministic.
 */
export function useBrainDump() {
  const userTimeZone = useUserTimezone();

  return useMutation<BrainDumpCandidate[], Error, { text: string }>({
    mutationFn: async ({ text }) => {
      const data: { candidates?: BrainDumpCandidate[] } = await api.POST({
        url: "/api/todo/brain-dump",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          text,
          timeZone: userTimeZone.timeZone,
          locale: i18n.language,
        }),
      });
      return data.candidates ?? [];
    },
  });
}

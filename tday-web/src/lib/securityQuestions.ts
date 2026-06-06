import { api } from "@/lib/api-client";

export type SecurityQuestion = { id: number; text: string };

export type SecurityAnswerInput = { questionId: number; answer: string };

/** The full catalogue of security questions (for signup / set-questions pickers). */
export async function fetchAllSecurityQuestions(): Promise<SecurityQuestion[]> {
  const body = (await api.GET({ url: "/api/auth/security-questions/all" })) as
    | { questions?: SecurityQuestion[] }
    | null;
  return body?.questions ?? [];
}

/**
 * The two questions to challenge for a username during password reset. The
 * server always returns two questions (a stable decoy pair for unknown
 * accounts), so this never reveals whether the account exists.
 */
export async function fetchQuestionsForUsername(username: string): Promise<SecurityQuestion[]> {
  const body = (await api.GET({
    url: `/api/auth/security-questions?username=${encodeURIComponent(username)}`,
  })) as { questions?: SecurityQuestion[] } | null;
  return body?.questions ?? [];
}

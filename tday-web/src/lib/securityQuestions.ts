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

export type SecurityAnswerResult = { questionId: number; correct: boolean };

/**
 * The account's stored security questions (2–3) for the reset wizard. Throws an
 * ApiError with code "user_not_found" (HTTP 404) when no such account exists, so the
 * wizard can validate the username up front before showing the questions.
 */
export async function fetchQuestionsForUsername(username: string): Promise<SecurityQuestion[]> {
  const body = (await api.GET({
    url: `/api/auth/security-questions?username=${encodeURIComponent(username)}`,
  })) as { questions?: SecurityQuestion[] } | null;
  return body?.questions ?? [];
}

/**
 * Verifies security answers WITHOUT resetting the password. Returns whether the pair is
 * valid and a per-answer breakdown so the wizard can swap the failed question. Throws an
 * ApiError with code "reset_locked" (HTTP 403) once the account is locked out.
 */
export async function verifySecurityAnswers(
  username: string,
  answers: SecurityAnswerInput[],
): Promise<{ valid: boolean; results: SecurityAnswerResult[] }> {
  const body = (await api.POST({
    url: "/api/auth/verify-security-answers",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, answers }),
  })) as { valid?: boolean; results?: SecurityAnswerResult[] } | null;
  return { valid: Boolean(body?.valid), results: body?.results ?? [] };
}

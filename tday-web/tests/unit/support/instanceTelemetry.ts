import { vi } from "vitest";
import { answerFrom, applyInstanceTelemetry } from "@/lib/privacy/instanceTelemetry";

/** The moment the admin is treated as having answered, for tests that need a timestamp guard. */
export const INSTANCE_ANSWERED_AT = "2026-10-01T12:00:00.000Z";

/**
 * Puts the instance-wide answer where a test wants it, without the server round trip
 * `initSentryIfConsented` would make. This is the same call the admin's Settings switch makes.
 */
export function setInstanceAnswerForTests(
  enabled: boolean,
  updatedAt: string | null = INSTANCE_ANSWERED_AT,
): void {
  applyInstanceTelemetry(answerFrom(enabled, enabled ? updatedAt : null));
}

/** Answers the boot read with `payload`, and hands back the spy so a test can inspect the call. */
export function stubInstanceTelemetryFetch(payload: unknown) {
  const fetchMock = vi.fn(() =>
    Promise.resolve({ ok: true, json: () => Promise.resolve(payload) }),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

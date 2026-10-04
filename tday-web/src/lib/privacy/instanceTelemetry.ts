/**
 * Whether this browser may send crash and problem reports, which is an instance-wide answer an
 * admin gives once on the server rather than a per-browser preference.
 *
 * The browser asks the server's public `GET /api/instance/telemetry` before the SDK may start and
 * keeps the answer in memory for the page load. It is deliberately not persisted: a stale answer
 * from an older build (the old `tday.telemetry.consent` / `tday.telemetry.consentAt` keys) is
 * never read, so nothing can report on the strength of a choice somebody made for themselves when
 * the model was per-user.
 *
 * The default is off, and every failure keeps it off: no answer, an unreadable answer, or a wrong
 * shape all leave the SDK uninitialised. That is the same fail-closed rule the endpoint applies on
 * the server side.
 *
 * The read is a plain `fetch` rather than the shared API client on purpose. Local Mode answers
 * `/api/*` from browser storage, and the admin's answer belongs to the server, not to the
 * workspace this browser happens to have open — a browser in Local Mode still has to obey the
 * answer it read when the page loaded.
 *
 * The answer is re-read when a page comes back to the foreground (`watchInstanceTelemetry`), so an
 * admin changing it in one browser reaches already-open pages in another without a reload.
 */

import { isLocalMode } from "@/lib/local/appMode";

/** One answer for the whole instance: whether reports may be sent, and since when. */
export type InstanceTelemetryAnswer = {
  enabled: boolean;
  /** The admin's last write, in epoch milliseconds, or null while it was never answered. */
  updatedAtMs: number | null;
};

export const INSTANCE_TELEMETRY_URL = "/api/instance/telemetry";

type Listener = () => void;

const OFF: InstanceTelemetryAnswer = { enabled: false, updatedAtMs: null };

const listeners = new Set<Listener>();

let answer: InstanceTelemetryAnswer = OFF;
let inFlight: Promise<void> | null = null;

/**
 * The endpoint's `{enabled, updatedAt}` as an answer, or null when it is not one. `updatedAt` is
 * ISO-8601; an answer without a usable timestamp keeps `updatedAtMs` null, which makes the
 * timestamp guard a no-op rather than dropping every event.
 */
export function parseInstanceTelemetry(payload: unknown): InstanceTelemetryAnswer | null {
  if (payload === null || typeof payload !== "object") return null;
  const enabled = (payload as { enabled?: unknown }).enabled;
  if (typeof enabled !== "boolean") return null;
  return answerFrom(enabled, (payload as { updatedAt?: unknown }).updatedAt);
}

/** The admin's answer in the shape the store holds it, from the API's ISO timestamp. */
export function answerFrom(enabled: boolean, updatedAt: unknown): InstanceTelemetryAnswer {
  const parsedAt = typeof updatedAt === "string" ? Date.parse(updatedAt) : Number.NaN;
  return { enabled, updatedAtMs: Number.isFinite(parsedAt) ? parsedAt : null };
}

/** Records the answer the server gave (or the admin just wrote) and wakes the gates. */
export function applyInstanceTelemetry(next: InstanceTelemetryAnswer): void {
  if (next.enabled === answer.enabled && next.updatedAtMs === answer.updatedAtMs) return;
  answer = next;
  for (const listener of listeners) listener();
}

export function getInstanceTelemetry(): InstanceTelemetryAnswer {
  return answer;
}

/** The only question the Sentry gates ask. Anything but a yes from the server is a no. */
export function isTelemetryGranted(): boolean {
  return answer.enabled;
}

/**
 * When the admin's answer last changed, or null while reports are off. A report stamped earlier
 * than this is dropped, so nothing from before the answer can ride in on a later yes.
 */
export function getTelemetryConsentAt(): number | null {
  return answer.enabled ? answer.updatedAtMs : null;
}

/** Calls `listener` after every change to the answer; returns the function that unsubscribes it. */
export function subscribeToTelemetryConsent(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/**
 * Reads the instance answer once. Concurrent callers share one request. A failure changes nothing,
 * which leaves the answer at its off default.
 */
export function refreshInstanceTelemetry(): Promise<void> {
  inFlight ??= readInstanceTelemetry().finally(() => {
    inFlight = null;
  });
  return inFlight;
}

let stopWatching: (() => void) | null = null;

/**
 * Re-reads the answer when a page that was already open comes back to the foreground. That is how
 * the admin turning reports on or off in one browser reaches a page open in another, without a
 * reload and without a polling interval: a page sitting in front of somebody already has the answer
 * it read when it loaded, and the return to view is the moment the answer could have changed.
 *
 * It cannot start anything on its own. It only asks `refreshInstanceTelemetry`, which applies an
 * answer and nothing else, so a read that fails or never comes back leaves the SDK where it was.
 *
 * Local Mode is a no-op: the answer belongs to the server, and a local-only workspace is not
 * talking to one. Installed once per page; the returned function removes it.
 */
export function watchInstanceTelemetry(): () => void {
  if (stopWatching) return stopWatching;
  if (typeof document === "undefined" || typeof window === "undefined") return () => {};

  const recheck = () => {
    // Only the return to view counts, and a hidden page never asks.
    if (document.visibilityState === "hidden" || isLocalMode()) return;
    void refreshInstanceTelemetry();
  };
  document.addEventListener("visibilitychange", recheck);
  window.addEventListener("focus", recheck);

  const unwatch = () => {
    document.removeEventListener("visibilitychange", recheck);
    window.removeEventListener("focus", recheck);
    stopWatching = null;
  };
  stopWatching = unwatch;
  return unwatch;
}

async function readInstanceTelemetry(): Promise<void> {
  try {
    const response = await fetch(INSTANCE_TELEMETRY_URL, {
      method: "GET",
      headers: { Accept: "application/json" },
      credentials: "same-origin",
      cache: "no-store",
    });
    if (!response.ok) return;
    const parsed = parseInstanceTelemetry(await response.json());
    if (parsed) applyInstanceTelemetry(parsed);
  } catch {
    // No answer, no reports.
  }
}

/** The browser build's Sentry DSN. Empty when the build carries none (forks, local builds). */
export function clientSentryDsn(): string {
  return (import.meta.env.VITE_SENTRY_DSN ?? "").trim();
}

/**
 * Without a DSN there is nowhere to send to, so there is nothing to ask about: no wizard step, no
 * Settings row, and the SDK never starts.
 */
export function isCrashReportingConfigured(): boolean {
  return clientSentryDsn() !== "";
}

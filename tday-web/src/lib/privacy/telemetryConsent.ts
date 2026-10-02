/**
 * Whether this browser may send crash and problem reports to the maintainer's Sentry.
 *
 * Three states, one rule: `unanswered` behaves exactly like `denied`. Nothing is initialised,
 * patched or queued until somebody says yes, so a person who never meets the consent card — or
 * dismisses it — has never sent anything. The answer is per browser, like the sound and vibration
 * switches, and not per account: it is about the machine, and the same account can be signed in
 * on a desk and a phone with a different answer on each.
 *
 * It mirrors `lib/local/appMode.ts` — a copy of the stored value in memory, a subscribe function
 * and a `useSyncExternalStore` hook — because the two readers that matter cannot await storage:
 * the Sentry transport asks on every envelope, and `main.tsx` asks before React exists. The one
 * difference is the default. A preference like `feedbackPreferences` falls back to "on" when
 * storage cannot answer; consent falls back to "not given".
 *
 * Both keys are listed in `AuthProvider`'s `PRESERVED_STORAGE_KEYS`: a sign-out or an expired
 * session is not a change of mind, and wiping the answer would put the card back on screen.
 */
export type TelemetryConsent = "unanswered" | "granted" | "denied";

export const TELEMETRY_CONSENT_STORAGE_KEY = "tday.telemetry.consent";
/** Epoch milliseconds of the last grant. Present only while consent is `granted`. */
export const TELEMETRY_CONSENT_AT_STORAGE_KEY = "tday.telemetry.consentAt";

type Listener = () => void;

const listeners = new Set<Listener>();

function readStoredConsent(): TelemetryConsent {
  if (typeof window === "undefined") return "unanswered";
  try {
    const stored = window.localStorage.getItem(TELEMETRY_CONSENT_STORAGE_KEY);
    return stored === "granted" || stored === "denied" ? stored : "unanswered";
  } catch {
    // Storage blocked (private mode / embedded webview) — behave as "not answered".
    return "unanswered";
  }
}

function readStoredConsentAt(): number | null {
  if (typeof window === "undefined") return null;
  try {
    const stored = Number(window.localStorage.getItem(TELEMETRY_CONSENT_AT_STORAGE_KEY));
    return Number.isFinite(stored) && stored > 0 ? stored : null;
  } catch {
    return null;
  }
}

let currentConsent = readStoredConsent();
let currentConsentAt = currentConsent === "granted" ? readStoredConsentAt() : null;

function notify(): void {
  for (const listener of listeners) listener();
}

// The browser fires this in every other document when one tab writes. Without it a tab that was
// left open would keep sending after the answer was withdrawn somewhere else, because the copy in
// memory is what the transport reads. `key === null` is a clear of all site data.
function syncFromStorage(event: StorageEvent): void {
  if (
    event.key !== null &&
    event.key !== TELEMETRY_CONSENT_STORAGE_KEY &&
    event.key !== TELEMETRY_CONSENT_AT_STORAGE_KEY
  ) {
    return;
  }
  const consent = readStoredConsent();
  const consentAt = consent === "granted" ? readStoredConsentAt() : null;
  if (consent === currentConsent && consentAt === currentConsentAt) return;
  currentConsent = consent;
  currentConsentAt = consentAt;
  notify();
}

if (typeof window !== "undefined") {
  window.addEventListener("storage", syncFromStorage);
}

export function getTelemetryConsent(): TelemetryConsent {
  return currentConsent;
}

/** The only question the Sentry gates ask. `unanswered` and `denied` both answer no. */
export function isTelemetryGranted(): boolean {
  return currentConsent === "granted";
}

/** When consent was last granted, or null while it is not granted. */
export function getTelemetryConsentAt(): number | null {
  return currentConsentAt;
}

export function setTelemetryConsent(granted: boolean): void {
  const next: TelemetryConsent = granted ? "granted" : "denied";
  if (currentConsent === next) return;
  currentConsent = next;
  currentConsentAt = granted ? Date.now() : null;
  try {
    window.localStorage.setItem(TELEMETRY_CONSENT_STORAGE_KEY, next);
    if (currentConsentAt === null) {
      window.localStorage.removeItem(TELEMETRY_CONSENT_AT_STORAGE_KEY);
    } else {
      window.localStorage.setItem(TELEMETRY_CONSENT_AT_STORAGE_KEY, String(currentConsentAt));
    }
  } catch {
    // Ignore storage write failures; the in-memory answer still governs this session.
  }
  notify();
}

/** Calls `listener` after every change to the answer; returns the function that unsubscribes it. */
export function subscribeToTelemetryConsent(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** The browser build's Sentry DSN. Empty when the build carries none (forks, local builds). */
export function clientSentryDsn(): string {
  return (import.meta.env.VITE_SENTRY_DSN ?? "").trim();
}

/**
 * Without a DSN there is nowhere to send to, so there is nothing to ask about: no card, no
 * Settings row, and the SDK never starts.
 */
export function isCrashReportingConfigured(): boolean {
  return clientSentryDsn() !== "";
}

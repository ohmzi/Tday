/**
 * Which workspace the browser is using — the web mirror of the native apps'
 * `AppDataMode` (see `ServerConfigRepository` on Android / iOS).
 *
 * - `server`: the normal signed-in workspace, backed by this site's backend.
 * - `local`:  a no-login workspace kept entirely in this browser's storage.
 *
 * `null` means the visitor has not picked yet, so the onboarding wizard opens on
 * its Mode step. Clearing site data (what browsers call "cookies") resets this
 * back to `null` and takes the local workspace with it — that is by design.
 */
export type AppMode = "server" | "local";

export const APP_MODE_STORAGE_KEY = "tday.appMode";

type Listener = () => void;

const listeners = new Set<Listener>();

function readStoredMode(): AppMode | null {
  if (typeof window === "undefined") return null;
  try {
    const stored = window.localStorage.getItem(APP_MODE_STORAGE_KEY);
    return stored === "local" || stored === "server" ? stored : null;
  } catch {
    // Storage blocked (private mode / embedded webview) — behave as "not chosen".
    return null;
  }
}

// Mirrored in memory so `isLocalMode()` stays a cheap synchronous check on the
// hot path (every api-client call reads it).
let currentMode: AppMode | null = readStoredMode();

function notify(): void {
  for (const listener of listeners) listener();
}

export function getAppMode(): AppMode | null {
  return currentMode;
}

export function isLocalMode(): boolean {
  return currentMode === "local";
}

export function setAppMode(mode: AppMode | null): void {
  if (currentMode === mode) return;
  currentMode = mode;
  try {
    if (mode === null) {
      window.localStorage.removeItem(APP_MODE_STORAGE_KEY);
    } else {
      window.localStorage.setItem(APP_MODE_STORAGE_KEY, mode);
    }
  } catch {
    // Ignore storage write failures; the in-memory mode still drives this session.
  }
  notify();
}

/** Re-reads the flag from storage — used by tests and cross-tab `storage` events. */
export function refreshAppMode(): void {
  const next = readStoredMode();
  if (next === currentMode) return;
  currentMode = next;
  notify();
}

export function subscribeToAppMode(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

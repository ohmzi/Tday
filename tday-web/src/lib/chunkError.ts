/**
 * Stale-chunk recovery.
 *
 * After a deploy, a client running an older bundle may try to load a hashed
 * route chunk that no longer exists. Depending on the browser/engine this
 * surfaces as either a failed dynamic import, or — when React.lazy resolves a
 * mismatched module — a "_result.default" / "reading 'default'" TypeError
 * (notably in WebKit: `undefined is not an object (evaluating 'o._result.default')`).
 *
 * Both mean the same thing: reload once to fetch the fresh index + current
 * chunks. The sessionStorage guard prevents a reload loop if the failure turns
 * out to be a genuine bug rather than a version mismatch.
 */

const RELOAD_KEY = "tday:stale-chunk-reload";

const STALE_CHUNK_TOKENS = [
  "dynamically imported module",
  "error loading dynamically imported module",
  "failed to fetch dynamically imported module",
  "loading chunk",
  "loading css chunk",
  "importing a module script failed",
  "module script failed",
  // React.lazy resolved a stale/mismatched module (engine-specific phrasings):
  "_result.default",
  "reading 'default'",
];

export function isStaleChunkError(error: unknown): boolean {
  const message =
    error instanceof Error
      ? error.message
      : typeof error === "string"
        ? error
        : "";
  if (!message) return false;
  const lower = message.toLowerCase();
  return STALE_CHUNK_TOKENS.some((token) => lower.includes(token));
}

/**
 * Reloads the page once per session to recover from a stale chunk.
 * @returns true if a reload was triggered, false if we've already reloaded once.
 */
export function reloadOnceForStaleChunk(): boolean {
  try {
    if (sessionStorage.getItem(RELOAD_KEY)) return false;
    sessionStorage.setItem(RELOAD_KEY, "1");
  } catch {
    // sessionStorage unavailable (private mode quirks) — reload anyway.
  }
  window.location.reload();
  return true;
}

/** Clears the guard once the app has loaded successfully, so a later deploy in
 * the same session can recover too. */
export function clearStaleChunkReloadFlag(): void {
  try {
    sessionStorage.removeItem(RELOAD_KEY);
  } catch {
    // ignore
  }
}

/**
 * Separate one-shot guard for the proactive version-gate reload (see
 * useVersionGate). Kept distinct from RELOAD_KEY so the reactive chunk-error
 * net and the proactive version check don't consume each other's budget.
 */
const VERSION_RELOAD_KEY = "tday:version-reload";

export function versionReloadAlreadyTried(): boolean {
  try {
    return sessionStorage.getItem(VERSION_RELOAD_KEY) != null;
  } catch {
    return false;
  }
}

export function markVersionReloadTried(): void {
  try {
    sessionStorage.setItem(VERSION_RELOAD_KEY, "1");
  } catch {
    // ignore
  }
}

export function clearVersionReloadFlag(): void {
  try {
    sessionStorage.removeItem(VERSION_RELOAD_KEY);
  } catch {
    // ignore
  }
}

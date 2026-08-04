import { clearStaleChunkReloadFlag, clearVersionReloadFlag } from "./chunkError";

/**
 * Last-resort recovery for a client stuck on a half-updated build.
 *
 * Several deploys in quick succession re-hash every chunk each time, which can
 * leave a cached client (iOS PWA especially) holding a precache that references
 * chunk hashes the server no longer has — it crashes on *some* screens while the
 * rest of the app looks fine. The automatic nets (`vite:preloadError` in
 * `main.tsx` and the `/version.json` poll in `useVersionGate`) only fire when the
 * app still boots far enough to run them, so this is the manual escape hatch
 * that replaces "delete the home-screen PWA and clear site data".
 *
 * Deliberately scoped to Cache Storage + the Service Worker: localStorage and
 * IndexedDB are left alone, so the session, language pick and Local Mode tasks
 * all survive the reset.
 */
export async function resetAppData(): Promise<void> {
  try {
    const registration = await navigator.serviceWorker?.getRegistration("/");
    // Best-effort — lets the SW drop anything it opened itself. The page-side
    // wipe below is what we actually await: postMessage gives no completion
    // signal, so relying on it alone would race the reload.
    registration?.active?.postMessage({ type: "CLEAR_CACHES" });
    await registration?.unregister();
  } catch {
    // No SW (dev build, private mode, insecure origin) — the wipe below still applies.
  }

  try {
    if (typeof caches !== "undefined") {
      const keys = await caches.keys();
      await Promise.all(keys.map((key) => caches.delete(key)));
    }
  } catch {
    // Cache Storage unavailable — the unregister + reload is still worth doing.
  }

  // The fresh boot must not inherit a spent reload budget, or the reactive
  // stale-chunk net would decline to reload if it hits a bad chunk again.
  clearStaleChunkReloadFlag();
  clearVersionReloadFlag();

  window.location.reload();
}

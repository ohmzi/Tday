import { useEffect, useRef } from "react";
import {
  versionReloadAlreadyTried,
  markVersionReloadTried,
} from "@/lib/chunkError";

/**
 * Proactively detects that the backend has shipped a new build (by polling
 * /version.json and comparing its buildId to the one baked into this bundle)
 * and reloads into the fresh build before a stale dynamic-import chunk can
 * crash the app.
 *
 * Hybrid UX: silently reload when the tab was backgrounded/idle and the user
 * isn't typing; otherwise hand off to `onPromptUpdate` to show a toast.
 */
const VERSION_URL = "/version.json";
const CHECK_INTERVAL_MS = 15 * 60 * 1000;
const CURRENT_BUILD_ID = __BUILD_ID__;

async function fetchDeployedBuildId(): Promise<string | null> {
  try {
    const res = await fetch(VERSION_URL, { cache: "no-store" });
    if (!res.ok) return null;
    const data = (await res.json()) as { buildId?: unknown };
    return typeof data.buildId === "string" ? data.buildId : null;
  } catch {
    return null; // offline / deploy-in-progress — try again next tick
  }
}

/** Reload once into the latest build, driving the waiting SW if there is one. */
async function reloadToLatest(): Promise<void> {
  if (versionReloadAlreadyTried()) return;
  markVersionReloadTried();
  try {
    const reg = await navigator.serviceWorker?.getRegistration("/");
    await reg?.update();
    const waiting = reg?.waiting;
    if (waiting) {
      navigator.serviceWorker.addEventListener(
        "controllerchange",
        () => window.location.reload(),
        { once: true },
      );
      waiting.postMessage({ type: "SKIP_WAITING" });
      return;
    }
  } catch {
    // fall through to a plain reload (NetworkFirst fetches fresh index.html)
  }
  window.location.reload();
}

function isEditingActive(): boolean {
  const el = document.activeElement as HTMLElement | null;
  if (!el) return false;
  const tag = el.tagName;
  return (
    tag === "INPUT" ||
    tag === "TEXTAREA" ||
    tag === "SELECT" ||
    el.isContentEditable
  );
}

interface UseVersionGateOptions {
  /** Called when an update should be offered (not auto-applied). The argument
   * triggers the reload when the user accepts. */
  onPromptUpdate: (reload: () => void) => void;
}

export function useVersionGate({ onPromptUpdate }: UseVersionGateOptions): void {
  const handledRef = useRef<string | null>(null);
  const wasHiddenRef = useRef(false);

  useEffect(() => {
    // version.json + the service worker only exist in production builds.
    if (!import.meta.env.PROD) return;
    let cancelled = false;

    const check = async (eligibleForAuto: boolean) => {
      if (!navigator.onLine) return;
      const deployedId = await fetchDeployedBuildId();
      if (cancelled || !deployedId || deployedId === CURRENT_BUILD_ID) return;
      if (handledRef.current === deployedId) return; // already handled this build
      handledRef.current = deployedId;

      if (eligibleForAuto && !isEditingActive()) {
        void reloadToLatest();
      } else {
        onPromptUpdate(() => void reloadToLatest());
      }
    };

    const onVisibility = () => {
      if (document.visibilityState === "hidden") {
        wasHiddenRef.current = true;
        return;
      }
      const wasHidden = wasHiddenRef.current;
      wasHiddenRef.current = false;
      // Returning to a previously-backgrounded tab → eligible for silent reload.
      void check(wasHidden);
    };

    void check(false);
    const interval = window.setInterval(() => void check(false), CHECK_INTERVAL_MS);
    document.addEventListener("visibilitychange", onVisibility);

    return () => {
      cancelled = true;
      window.clearInterval(interval);
      document.removeEventListener("visibilitychange", onVisibility);
    };
  }, [onPromptUpdate]);
}

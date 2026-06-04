import { useCallback, useEffect, useState } from "react";

interface BeforeInstallPromptEvent extends Event {
  readonly platforms: string[];
  prompt(): Promise<{ outcome: "accepted" | "dismissed" }>;
}

declare global {
  interface WindowEventMap {
    beforeinstallprompt: BeforeInstallPromptEvent;
  }
}

type InstallState = {
  /** The deferred prompt event (Chrome/Edge/Samsung). Null on iOS or after install. */
  deferredPrompt: BeforeInstallPromptEvent | null;
  /** True when the app is running in standalone / installed mode. */
  isInstalled: boolean;
  /** True on iOS Safari where `beforeinstallprompt` doesn't fire. */
  isIosSafari: boolean;
  /** Whether the user has dismissed the install banner this session. */
  dismissed: boolean;
};

const DISMISSED_KEY = "tday.install-banner-dismissed";

function getIsInstalled(): boolean {
  if (typeof window === "undefined") return false;
  // PWA standalone
  if (window.matchMedia("(display-mode: standalone)").matches) return true;
  // iOS standalone
  if ((navigator as unknown as { standalone?: boolean }).standalone) return true;
  return false;
}

function getIsIosSafari(): boolean {
  if (typeof navigator === "undefined") return false;
  const ua = navigator.userAgent;
  const isIos = /iPad|iPhone|iPod/.test(ua) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
  const isSafari = /Safari/.test(ua) && !/CriOS|FxiOS|OPiOS|EdgiOS/.test(ua);
  return isIos && isSafari;
}

export function useInstallPrompt() {
  const [state, setState] = useState<InstallState>({
    deferredPrompt: null,
    isInstalled: getIsInstalled(),
    isIosSafari: getIsIosSafari(),
    dismissed: typeof sessionStorage !== "undefined" && sessionStorage.getItem(DISMISSED_KEY) === "1",
  });

  useEffect(() => {
    const onPrompt = (e: BeforeInstallPromptEvent) => {
      e.preventDefault();
      setState((s) => ({ ...s, deferredPrompt: e }));
    };

    const onInstalled = () => {
      setState((s) => ({ ...s, isInstalled: true, deferredPrompt: null }));
    };

    window.addEventListener("beforeinstallprompt", onPrompt);
    window.addEventListener("appinstalled", onInstalled);

    // Also listen for display-mode changes (e.g. user installs from browser menu)
    const mq = window.matchMedia("(display-mode: standalone)");
    const onDisplayChange = (e: MediaQueryListEvent) => {
      if (e.matches) setState((s) => ({ ...s, isInstalled: true, deferredPrompt: null }));
    };
    mq.addEventListener("change", onDisplayChange);

    return () => {
      window.removeEventListener("beforeinstallprompt", onPrompt);
      window.removeEventListener("appinstalled", onInstalled);
      mq.removeEventListener("change", onDisplayChange);
    };
  }, []);

  const promptInstall = useCallback(async () => {
    if (!state.deferredPrompt) return;
    const result = await state.deferredPrompt.prompt();
    if (result.outcome === "accepted") {
      setState((s) => ({ ...s, isInstalled: true, deferredPrompt: null }));
    }
  }, [state.deferredPrompt]);

  const dismiss = useCallback(() => {
    sessionStorage.setItem(DISMISSED_KEY, "1");
    setState((s) => ({ ...s, dismissed: true }));
  }, []);

  const showBanner = !state.isInstalled && !state.dismissed && (state.deferredPrompt !== null || state.isIosSafari);

  return {
    ...state,
    promptInstall,
    dismiss,
    showBanner,
  };
}

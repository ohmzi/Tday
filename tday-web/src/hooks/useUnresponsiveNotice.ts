import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

/**
 * How long the main thread may be blocked before T'Day offers a way out. A block this long is long
 * enough to feel like the app has died, and short enough that a slow render does not trigger it.
 */
const BLOCKED_NOTICE_MS = 2500;

/** How often the thread checks in. A tick that arrives late *is* the block being measured. */
const TICK_MS = 1000;

/** Sonner's identity for the notice, so a second stall replaces the first notice instead of stacking. */
const UNRESPONSIVE_NOTICE_ID = "tday-unresponsive";

/**
 * Offers a reload when the main thread has been blocked long enough that the app looks dead.
 *
 * Nothing can be drawn while the thread is blocked — no spinner, no button, not even this notice —
 * so the escape is offered the moment the thread runs again: a notice naming how long it was stuck,
 * with the reload beside it. A block that never ends is the browser's own "page unresponsive" prompt
 * to handle, and a render failure is the error boundary's, which has its own reload.
 *
 * Background tabs throttle their timers, and a throttled tab is not a stuck app, so gaps seen while
 * the tab is hidden are ignored and the clock restarts when it comes back.
 */
export function useUnresponsiveNotice(): void {
  const { t } = useTranslation("app");

  useEffect(() => {
    let lastTick = performance.now();
    const timer = window.setInterval(() => {
      const now = performance.now();
      const gap = now - lastTick;
      lastTick = now;
      if (document.hidden || gap < BLOCKED_NOTICE_MS) return;
      toast(t("unresponsiveTitle"), {
        // One notice, however many times the thread stalls: the same id replaces it rather than
        // stacking a second copy behind the first.
        id: UNRESPONSIVE_NOTICE_ID,
        description: t("unresponsiveBody", { seconds: Math.max(1, Math.round(gap / 1000)) }),
        action: { label: t("unresponsiveReload"), onClick: () => window.location.reload() },
        duration: Number.POSITIVE_INFINITY,
      });
    }, TICK_MS);

    const resetClock = () => {
      lastTick = performance.now();
    };
    document.addEventListener("visibilitychange", resetClock);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", resetClock);
    };
  }, [t]);
}

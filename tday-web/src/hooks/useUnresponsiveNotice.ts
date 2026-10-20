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
 * A late tick is only evidence of a blocked thread when the page was there to be blocked. A
 * backgrounded tab, a minimised or occluded window, a machine that slept — each stops the timers
 * for a while and looks identical to a stall from the inside (issue: "the webapp was just in
 * background, nothing broke, so why is it showing messages like this"). So a gap is only measured
 * between two ticks that were both taken while the page was visible AND focused, and every event
 * that could open such a gap on purpose — the tab hiding or returning, the window losing or gaining
 * focus, a page coming back from the bfcache — restarts the clock rather than letting the gap land.
 */
export function useUnresponsiveNotice(): void {
  const { t } = useTranslation("app");

  useEffect(() => {
    let lastTick = performance.now();
    const timer = window.setInterval(() => {
      const now = performance.now();
      const gap = now - lastTick;
      lastTick = now;
      // Both halves of "the user is actually looking at this page". A page that
      // is not visible was throttled by the browser; a page that is visible but
      // unfocused is behind another window and its timers are not a promise
      // about this app's health either.
      if (document.hidden || !document.hasFocus()) return;
      if (gap < BLOCKED_NOTICE_MS) return;
      toast(t("unresponsiveTitle"), {
        // One notice, however many times the thread stalls: the same id replaces it rather than
        // stacking a second copy behind the first.
        id: UNRESPONSIVE_NOTICE_ID,
        description: t("unresponsiveBody", { seconds: Math.max(1, Math.round(gap / 1000)) }),
        action: { label: t("unresponsiveReload"), onClick: () => window.location.reload() },
        duration: Number.POSITIVE_INFINITY,
      });
    }, TICK_MS);

    // Any of these can sit inside a gap that is not a stall, so each one is
    // treated as the start of a fresh watch rather than as a slow tick.
    const resetClock = () => {
      lastTick = performance.now();
    };
    document.addEventListener("visibilitychange", resetClock);
    window.addEventListener("focus", resetClock);
    window.addEventListener("blur", resetClock);
    window.addEventListener("pageshow", resetClock);
    window.addEventListener("pagehide", resetClock);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", resetClock);
      window.removeEventListener("focus", resetClock);
      window.removeEventListener("blur", resetClock);
      window.removeEventListener("pageshow", resetClock);
      window.removeEventListener("pagehide", resetClock);
    };
  }, [t]);
}

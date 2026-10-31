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
 * backgrounded tab, a minimised or occluded window, a machine that slept — each stops or throttles
 * the timers for a while and looks identical to a stall from the inside. Two rules keep those out:
 *
 * 1. A tick only counts as "watching" when the page is visible and focused. Ticks taken while it is
 *    not are recorded but never reported.
 * 2. Any event that can open a gap on purpose (the tab hiding or returning, focus moving, a
 *    freeze/resume, a page coming back from the bfcache) marks the watch as interrupted. The first
 *    watching tick after that is skipped, whatever state the page is in when it fires. Background
 *    timers fire late, so that first tick can land before `visibilitychange` is dispatched; the
 *    interruption flag does not depend on the event arriving first, and it is only cleared by a
 *    tick that was itself watching.
 *
 * A jump in the wall clock that the monotonic clock did not share (the machine slept, or the clock
 * was changed) is also not a stall.
 */
export function useUnresponsiveNotice(): void {
  const { t } = useTranslation("app");

  useEffect(() => {
    let lastTick = performance.now();
    let lastWallTick = Date.now();
    let interrupted = false;

    const timer = window.setInterval(() => {
      const now = performance.now();
      const wall = Date.now();
      const gap = now - lastTick;
      const wallGap = wall - lastWallTick;
      lastTick = now;
      lastWallTick = wall;

      // Both halves of "the user is actually looking at this page". A page that
      // is not visible was throttled by the browser; a page that is visible but
      // unfocused is behind another window and its timers are not a promise
      // about this app's health either. Neither tick may clear the interruption,
      // so the first watching tick after it is still skipped.
      if (document.hidden || !document.hasFocus()) return;
      const wasInterrupted = interrupted;
      interrupted = false;
      if (wasInterrupted) return;

      if (gap < BLOCKED_NOTICE_MS) return;
      // A block advances both clocks together. A jump in only the wall clock is the machine sleeping
      // or the clock changing, not the thread being stuck.
      if (Math.abs(wallGap - gap) >= BLOCKED_NOTICE_MS) return;

      toast(t("unresponsiveTitle"), {
        // One notice, however many times the thread stalls: the same id replaces it rather than
        // stacking a second copy behind the first.
        id: UNRESPONSIVE_NOTICE_ID,
        description: t("unresponsiveBody", { seconds: Math.max(1, Math.round(gap / 1000)) }),
        action: { label: t("unresponsiveReload"), onClick: () => window.location.reload() },
        duration: Number.POSITIVE_INFINITY,
      });
    }, TICK_MS);

    // Any of these can sit inside a gap that is not a stall, so each one
    // interrupts the watch. The next watching tick is then skipped.
    const interrupt = () => {
      interrupted = true;
    };
    document.addEventListener("visibilitychange", interrupt);
    document.addEventListener("freeze", interrupt);
    document.addEventListener("resume", interrupt);
    window.addEventListener("focus", interrupt);
    window.addEventListener("blur", interrupt);
    window.addEventListener("pageshow", interrupt);
    window.addEventListener("pagehide", interrupt);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", interrupt);
      document.removeEventListener("freeze", interrupt);
      document.removeEventListener("resume", interrupt);
      window.removeEventListener("focus", interrupt);
      window.removeEventListener("blur", interrupt);
      window.removeEventListener("pageshow", interrupt);
      window.removeEventListener("pagehide", interrupt);
    };
  }, [t]);
}

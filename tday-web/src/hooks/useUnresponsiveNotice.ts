import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";

/**
 * How long the main thread may be blocked before T'Day offers a way out. A block this long is long
 * enough to feel like the app has died, and short enough that a slow render does not trigger it.
 */
const BLOCKED_NOTICE_MS = 2500;

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
 * The signal is the browser's own `longtask` entry: a task that ran on the main thread for longer
 * than the threshold. Timer-gap detection was tried and rejected. A background tab, a throttled
 * timer, or a machine that slept all stop the timers for a while, and from inside the page they
 * look exactly like a stall. A long task is only recorded for work that actually ran.
 *
 * A long task is reported only when all of these hold:
 * - the page was visible and focused when the entry was delivered;
 * - the task started after the last interruption (hide, blur, freeze, pagehide, and similar),
 *   so a task that began before the user left the page does not count.
 *
 * Where `longtask` is not supported (Safari and Firefox), no notice is shown at all. This is
 * deliberate: a missed notice is better than a false one. There is no timer-gap fallback.
 */
export function useUnresponsiveNotice(): void {
  const { t } = useTranslation("app");

  useEffect(() => {
    if (typeof PerformanceObserver === "undefined") return;
    if (!PerformanceObserver.supportedEntryTypes?.includes("longtask")) return;

    // performance.now() of the last hide, blur, freeze, or similar event. A task that started
    // before it belongs to a stretch the user was not looking at.
    let lastInterruptAt = performance.now();
    const interrupt = () => {
      lastInterruptAt = performance.now();
    };

    const observer = new PerformanceObserver((list) => {
      for (const entry of list.getEntries()) {
        if (entry.duration < BLOCKED_NOTICE_MS) continue;
        // Both halves of "the user is actually looking at this page". A hidden or
        // unfocused page can still run a long task, but it is not one the user saw.
        if (document.hidden || !document.hasFocus()) continue;
        if (entry.startTime <= lastInterruptAt) continue;

        toast(t("unresponsiveTitle"), {
          // One notice, however many times the thread stalls: the same id replaces it rather than
          // stacking a second copy behind the first.
          id: UNRESPONSIVE_NOTICE_ID,
          description: t("unresponsiveBody", {
            seconds: Math.max(1, Math.round(entry.duration / 1000)),
          }),
          action: { label: t("unresponsiveReload"), onClick: () => window.location.reload() },
          duration: Number.POSITIVE_INFINITY,
        });
      }
    });
    observer.observe({ type: "longtask", buffered: false });

    document.addEventListener("visibilitychange", interrupt);
    document.addEventListener("freeze", interrupt);
    document.addEventListener("resume", interrupt);
    window.addEventListener("focus", interrupt);
    window.addEventListener("blur", interrupt);
    window.addEventListener("pageshow", interrupt);
    window.addEventListener("pagehide", interrupt);
    return () => {
      observer.disconnect();
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

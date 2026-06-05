import { useEffect } from "react";
import { usePathname } from "@/lib/navigation";

// Safari (desktop + iOS) does not implement requestIdleCallback. Fall back to a
// timeout so route prefetching degrades gracefully instead of throwing a
// ReferenceError that crashes the whole app shell.
const requestIdle: (callback: IdleRequestCallback, options?: IdleRequestOptions) => number =
  typeof window !== "undefined" && typeof window.requestIdleCallback === "function"
    ? window.requestIdleCallback.bind(window)
    : (callback) =>
        window.setTimeout(
          () => callback({ didTimeout: false, timeRemaining: () => 0 }),
          1,
        );

const cancelIdle: (handle: number) => void =
  typeof window !== "undefined" && typeof window.cancelIdleCallback === "function"
    ? window.cancelIdleCallback.bind(window)
    : (handle) => window.clearTimeout(handle);

/**
 * Prefetches adjacent route chunks so navigation between primary screens
 * feels instant. Runs once per mount after a short idle delay.
 *
 * Today  → prefetches Floater, FloaterListPage
 * Floater → prefetches Today (TodayPage, TodayTasksPage)
 */
export function usePrefetchRoutes() {
  const pathname = usePathname();

  useEffect(() => {
    const id = requestIdle(
      () => {
        if (pathname.includes("/app/tday") || pathname.includes("/app/today")) {
          import("@/pages/FloaterPage");
          import("@/pages/FloaterListPage");
        } else if (pathname.includes("/app/floater")) {
          import("@/pages/TodayPage");
          import("@/pages/TodayTasksPage");
        }
      },
      { timeout: 2000 },
    );

    return () => cancelIdle(id);
  }, [pathname]);
}

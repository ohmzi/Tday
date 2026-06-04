import { useEffect } from "react";
import { usePathname } from "@/lib/navigation";

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
    const id = requestIdleCallback(
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

    return () => cancelIdleCallback(id);
  }, [pathname]);
}

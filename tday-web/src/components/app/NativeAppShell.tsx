import { useState } from "react";
import type { ReactNode } from "react";
import { usePathname } from "@/lib/navigation";
import MoreNavigationSheet from "./MoreNavigationSheet";
import RootDock from "./RootDock";
import TaskFloatingActionButton from "./TaskFloatingActionButton";
import InstallPromptBanner from "./InstallPromptBanner";
import { useNativeRouteCounts } from "./nativeRouteConfig";
import { usePrefetchRoutes } from "@/hooks/usePrefetchRoutes";

export default function NativeAppShell({
  children,
}: {
  children: ReactNode;
}) {
  const [moreOpen, setMoreOpen] = useState(false);
  const counts = useNativeRouteCounts();
  const pathname = usePathname();
  usePrefetchRoutes();
  const showTaskFab =
    !pathname.includes("/app/settings") &&
    !pathname.includes("/app/admin");

  return (
    <div className="relative flex h-screen min-h-screen overflow-hidden bg-background text-foreground">
      <div className="relative z-0 flex min-w-0 flex-1 flex-col overflow-hidden">
        {children}
      </div>
      <RootDock onOpenMore={() => setMoreOpen(true)} moreOpen={moreOpen} />
      {showTaskFab && <TaskFloatingActionButton />}
      <InstallPromptBanner />
      <MoreNavigationSheet open={moreOpen} onOpenChange={setMoreOpen} counts={counts} />
    </div>
  );
}

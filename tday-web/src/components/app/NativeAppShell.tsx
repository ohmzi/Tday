import { useState } from "react";
import type { ReactNode } from "react";
import { usePathname } from "@/lib/navigation";
import MoreNavigationSheet from "./MoreNavigationSheet";
import RootDock from "./RootDock";
import TaskFloatingActionButton from "./TaskFloatingActionButton";
import InstallPromptBanner from "./InstallPromptBanner";
import ForcePasswordChangeGate from "./ForcePasswordChangeGate";
import SetSecurityQuestionsGate from "./SetSecurityQuestionsGate";
import { useNativeRouteCounts } from "./nativeRouteConfig";
import { usePrefetchRoutes } from "@/hooks/usePrefetchRoutes";
import { useDuckPresence } from "@/hooks/useDuckPresence";
import { useBulkSelectionActive } from "@/lib/bulk/bulk-selection-signal";

export default function NativeAppShell({
  children,
}: {
  children: ReactNode;
}) {
  const [moreOpen, setMoreOpen] = useState(false);
  const counts = useNativeRouteCounts();
  const pathname = usePathname();
  usePrefetchRoutes();
  // A task list in selection mode puts its own action bar in this slot; the two
  // share the same fixed bottom metrics and must never come to REST on the
  // screen together. They do now overlap while the swap plays — the bar rises as
  // the chrome below ducks out — which is the hand-over the bar's own doc comment
  // says it was missing, not the collision this note was written about.
  const bulkSelecting = useBulkSelectionActive();
  const showTaskFab =
    !bulkSelecting &&
    !pathname.includes("/app/settings") &&
    !pathname.includes("/app/guide") &&
    !pathname.includes("/app/admin");

  // The two conditionals below used to be the whole story, which meant the dock
  // and the button were on the screen and then simply were not, in the paint the
  // thing taking their slot arrived in. They duck now — down and out through the
  // bottom edge, and back up the same way — so each one keeps its own predicate
  // and gains the travel. The dock and the button are asked separately because
  // they leave for different reasons: the More sheet takes the button's slot and
  // not the dock's.
  const dock = useDuckPresence(!bulkSelecting);
  const taskFab = useDuckPresence(showTaskFab && !moreOpen);

  return (
    <div className="relative flex h-screen min-h-screen overflow-hidden bg-background text-foreground">
      <div className="relative z-0 flex min-w-0 flex-1 flex-col overflow-hidden">
        {children}
      </div>
      {dock.mounted && (
        <RootDock
          onOpenMore={() => setMoreOpen(true)}
          moreOpen={moreOpen}
          duckClassName={dock.className}
          duckInteractive={dock.interactive}
        />
      )}
      {taskFab.mounted && (
        <TaskFloatingActionButton
          duckClassName={taskFab.className}
          duckInteractive={taskFab.interactive}
        />
      )}
      <InstallPromptBanner />
      <ForcePasswordChangeGate />
      <SetSecurityQuestionsGate />
      <MoreNavigationSheet open={moreOpen} onOpenChange={setMoreOpen} counts={counts} />
    </div>
  );
}

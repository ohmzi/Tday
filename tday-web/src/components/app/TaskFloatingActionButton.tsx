import { Plus } from "lucide-react";
import { usePathname } from "@/lib/navigation";
import { useCalendarCreateAction } from "@/features/calendar/context/CalendarCreateActionContext";
import { useCreateTask } from "@/providers/CreateTaskProvider";
import { useCreateFloaterTask } from "@/providers/CreateFloaterProvider";
import {
  nativeAppContentClassName,
  nativeAppHorizontalPaddingClassName,
} from "@/components/app/nativeAppLayout";
import { cn } from "@/lib/utils";
import { hapticButtonTap } from "@/lib/haptics";
import {
  activeListIdFromPath,
  activeFloaterListIdFromPath,
  listColorAccentColors,
  nativeScreenAccentColors,
} from "@/components/app/nativeScreenTheme";
import {
  isNativeRouteActive,
  nativeRoutes,
} from "@/components/app/nativeRouteConfig";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";

export default function TaskFloatingActionButton({
  className,
}: {
  className?: string;
}) {
  const pathname = usePathname();
  const calendarCreate = useCalendarCreateAction();
  const { openCreateTask } = useCreateTask();
  const { openCreateFloater } = useCreateFloaterTask();
  const { listMetaData } = useListMetaData();
  const activeRoute = nativeRoutes.find((route) => isNativeRouteActive(pathname, route));
  const useCalendarCreate = activeRoute?.id === "calendar" && calendarCreate != null;
  const activeListId = activeListIdFromPath(pathname);
  const activeFloaterListId = activeFloaterListIdFromPath(pathname);
  const useFloaterCreate = pathname.includes("/app/floater");
  const activeListColor = activeListId ? listMetaData[activeListId]?.color : undefined;
  const buttonColor =
    (useFloaterCreate ? nativeScreenAccentColors.floater : undefined) ??
    (activeListColor ? listColorAccentColors[activeListColor] : undefined) ??
    (activeRoute ? nativeScreenAccentColors[activeRoute.id] : undefined);

  return (
    <div
      className={cn(
        "pointer-events-none fixed inset-x-0 bottom-[calc(18px+env(safe-area-inset-bottom))] z-40",
        nativeAppHorizontalPaddingClassName,
      )}
    >
      <div className={cn(nativeAppContentClassName, "flex items-center justify-end")}>
        <button
          type="button"
          aria-label="Add task"
          onClick={
            useCalendarCreate
              ? () => { hapticButtonTap(); calendarCreate(); }
              : useFloaterCreate
                ? () => {
                    hapticButtonTap();
                    openCreateFloater(
                      activeFloaterListId
                        ? { listID: activeFloaterListId }
                        : undefined,
                    );
                  }
              : () => {
                  hapticButtonTap();
                  openCreateTask(
                    activeListId ? { listID: activeListId } : undefined,
                  );
                }
          }
          className={cn(
            "pointer-events-auto",
            // Icon-only circle on mobile (matching native); icon + label on desktop.
            "flex h-14 w-14 items-center justify-center gap-2 rounded-full px-0 sm:w-auto sm:px-5",
            "border border-white/60 bg-accent text-white",
            "shadow-[0_18px_34px_-18px_hsl(var(--shadow)/0.65)]",
            "transition-transform duration-200 hover:-translate-y-0.5 active:translate-y-0.5 active:scale-95",
            className,
          )}
          style={buttonColor ? { backgroundColor: buttonColor } : undefined}
        >
          <Plus className="h-8 w-8 stroke-[2.8]" />
          <span className="hidden text-base font-black tracking-tight sm:inline">
            Add Task
          </span>
        </button>
      </div>
    </div>
  );
}

import { Plus } from "lucide-react";
import { Link, usePathname } from "@/lib/navigation";
import { useCalendarCreateAction } from "@/features/calendar/context/CalendarCreateActionContext";
import {
  nativeAppContentClassName,
  nativeAppHorizontalPaddingClassName,
} from "@/components/app/nativeAppLayout";
import { cn } from "@/lib/utils";
import {
  activeListIdFromPath,
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
  const { listMetaData } = useListMetaData();
  const activeRoute = nativeRoutes.find((route) => isNativeRouteActive(pathname, route));
  const useCalendarCreate = activeRoute?.id === "calendar" && calendarCreate != null;
  const activeListId = activeListIdFromPath(pathname);
  const activeListColor = activeListId ? listMetaData[activeListId]?.color : undefined;
  const buttonColor =
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
        {useCalendarCreate ? (
          <button
            type="button"
            aria-label="Add task"
            onClick={calendarCreate}
            className={cn(
              "pointer-events-auto",
              "flex h-14 items-center justify-center gap-2 rounded-full px-5",
              "border border-white/60 bg-accent text-white",
              "shadow-[0_18px_34px_-18px_hsl(var(--shadow)/0.65)]",
              "transition-transform duration-200 hover:-translate-y-0.5 active:translate-y-0.5 active:scale-95",
              className,
            )}
            style={buttonColor ? { backgroundColor: buttonColor } : undefined}
          >
            <Plus className="h-5 w-5 stroke-[2.6]" />
            <span className="text-base font-black tracking-tight">Add Task</span>
          </button>
        ) : (
          <Link
            href="/app/add-task"
            aria-label="Add task"
            className={cn(
              "pointer-events-auto",
              "flex h-14 items-center justify-center gap-2 rounded-full px-5",
              "border border-white/60 bg-accent text-white",
              "shadow-[0_18px_34px_-18px_hsl(var(--shadow)/0.65)]",
              "transition-transform duration-200 hover:-translate-y-0.5 active:translate-y-0.5 active:scale-95",
              className,
            )}
            style={buttonColor ? { backgroundColor: buttonColor } : undefined}
          >
            <Plus className="h-5 w-5 stroke-[2.6]" />
            <span className="text-base font-black tracking-tight">Add Task</span>
          </Link>
        )}
      </div>
    </div>
  );
}

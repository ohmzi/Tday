import { Plus } from "lucide-react";
import { Link, usePathname } from "@/lib/navigation";
import { cn } from "@/lib/utils";
import {
  isNativeRouteActive,
  nativeRoutes,
  type NativeRouteId,
} from "@/components/app/nativeRouteConfig";
import { useListMetaData } from "@/components/Sidebar/List/query/get-list-meta";
import type { ListColor } from "@/types";

const createButtonColors: Partial<Record<NativeRouteId, string>> = {
  today: "#6EA8E1",
  scheduled: "#D98F4B",
  priority: "#C97880",
  overdue: "#E06F66",
  all: "#68717A",
  completed: "#719F84",
  calendar: "#9A89D2",
  settings: "#68717A",
};

const listButtonColors: Record<ListColor, string> = {
  RED: "hsl(var(--accent-red))",
  ORANGE: "hsl(var(--accent-orange))",
  YELLOW: "hsl(var(--accent-yellow))",
  LIME: "hsl(var(--accent-lime))",
  BLUE: "hsl(var(--accent-blue))",
  PURPLE: "hsl(var(--accent-purple))",
  PINK: "hsl(var(--accent-pink))",
  TEAL: "hsl(var(--accent-teal))",
  CORAL: "hsl(var(--accent-coral))",
  GOLD: "hsl(var(--accent-gold))",
  DEEP_BLUE: "hsl(var(--accent-deep-blue))",
  ROSE: "hsl(var(--accent-rose))",
  LIGHT_RED: "hsl(var(--accent-light-red))",
  BRICK: "hsl(var(--accent-brick))",
  SLATE: "hsl(var(--accent-slate))",
};

function activeListIdFromPath(pathname: string) {
  const marker = "/app/list/";
  const markerIndex = pathname.indexOf(marker);
  if (markerIndex === -1) return null;
  return pathname.slice(markerIndex + marker.length).split("/")[0] || null;
}

export default function TaskFloatingActionButton({
  className,
}: {
  className?: string;
}) {
  const pathname = usePathname();
  const { listMetaData } = useListMetaData();
  const activeRoute = nativeRoutes.find((route) => isNativeRouteActive(pathname, route));
  const activeListId = activeListIdFromPath(pathname);
  const activeListColor = activeListId ? listMetaData[activeListId]?.color : undefined;
  const buttonColor =
    (activeListColor ? listButtonColors[activeListColor] : undefined) ??
    (activeRoute ? createButtonColors[activeRoute.id] : undefined);

  return (
    <Link
      href="/app/add-task"
      aria-label="Add task"
      className={cn(
        "fixed bottom-[calc(18px+env(safe-area-inset-bottom))] right-5 z-40",
        "flex h-14 items-center justify-center gap-2 rounded-full px-5",
        "border border-white/60 bg-accent text-white",
        "shadow-[0_18px_34px_-18px_hsl(var(--shadow)/0.65)]",
        "transition-transform duration-200 hover:-translate-y-0.5 active:translate-y-0.5 active:scale-95",
        "md:right-8",
        className,
      )}
      style={buttonColor ? { backgroundColor: buttonColor } : undefined}
    >
      <Plus className="h-5 w-5 stroke-[2.6]" />
      <span className="text-base font-black tracking-tight">Add Task</span>
    </Link>
  );
}

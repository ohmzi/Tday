import type { ListColor } from "@/types";
import type { NativeRouteId } from "@/components/app/nativeRouteConfig";
import { isNativeRouteActive, nativeRoutes } from "@/components/app/nativeRouteConfig";

export const nativeScreenAccentColors: Record<NativeRouteId, string> = {
  today: "#6EA8E1",
  scheduled: "#D98F4B",
  priority: "#C97880",
  overdue: "#E06F66",
  all: "#68717A",
  completed: "#719F84",
  calendar: "#9A89D2",
  settings: "#68717A",
};

export const listColorAccentColors: Record<ListColor, string> = {
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

export function activeListIdFromPath(pathname: string) {
  const marker = "/app/list/";
  const markerIndex = pathname.indexOf(marker);
  if (markerIndex === -1) return null;
  return pathname.slice(markerIndex + marker.length).split("/")[0] || null;
}

export function resolveNativeScreenAccent(pathname: string, listColor?: ListColor | null) {
  if (pathname.includes("/app/list/") && listColor) {
    return {
      color: listColorAccentColors[listColor] ?? nativeScreenAccentColors.all,
      routeId: null as NativeRouteId | null,
    };
  }

  if (pathname.includes("/app/admin")) {
    return { color: nativeScreenAccentColors.settings, routeId: "settings" as const };
  }

  const activeRoute = nativeRoutes.find((route) => isNativeRouteActive(pathname, route));
  if (activeRoute) {
    return {
      color: nativeScreenAccentColors[activeRoute.id],
      routeId: activeRoute.id,
    };
  }

  return { color: nativeScreenAccentColors.all, routeId: null };
}

export const timelineScopeAccentColors = {
  today: nativeScreenAccentColors.today,
  overdue: nativeScreenAccentColors.overdue,
  scheduled: nativeScreenAccentColors.scheduled,
  priority: nativeScreenAccentColors.priority,
  all: nativeScreenAccentColors.all,
} as const;

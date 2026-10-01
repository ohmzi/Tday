import type { ListColor } from "@/types";
import type { NativeRouteId } from "@/components/app/nativeRouteConfig";

export const nativeScreenAccentColors: Record<NativeRouteId, string> = {
  today: "#6EA8E1",
  scheduled: "#D98F4B",
  priority: "#C97880",
  overdue: "#E06F66",
  all: "#68717A",
  completed: "#719F84",
  floater: "#4D8F83",
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

export function activeFloaterListIdFromPath(pathname: string) {
  const marker = "/app/floater-list/";
  const markerIndex = pathname.indexOf(marker);
  if (markerIndex === -1) return null;
  return pathname.slice(markerIndex + marker.length).split("/")[0] || null;
}

export const timelineScopeAccentColors = {
  today: nativeScreenAccentColors.today,
  overdue: nativeScreenAccentColors.overdue,
  scheduled: nativeScreenAccentColors.scheduled,
  priority: nativeScreenAccentColors.priority,
  all: nativeScreenAccentColors.all,
} as const;

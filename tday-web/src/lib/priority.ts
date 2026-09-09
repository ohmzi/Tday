// Priority flag mapping shared by the task card and calendar row, mirroring the native
// apps: medium/important → orange "Important", high/urgent → red "Urgent", everything else
// (low / normal / none) shows no flag. Keep in sync with `isPriorityTodo` in
// components/app/nativeRouteConfig.tsx and the native `tdayPriorityColor`.

export type PriorityFlag = {
  /** Tailwind classes for the lucide <Flag> icon (fill + stroke). */
  className: string;
  label: "Important" | "Urgent";
};

export function getPriorityFlag(
  priority: string | null | undefined,
): PriorityFlag | null {
  const normalized = (priority || "").trim().toLowerCase();
  if (normalized === "high" || normalized === "urgent") {
    return { className: "fill-red text-red", label: "Urgent" };
  }
  if (normalized === "medium" || normalized === "important") {
    return { className: "fill-orange text-orange", label: "Important" };
  }
  return null;
}

/**
 * Tailwind text/fill classes for the flag icon inside a priority PICKER
 * itself (trigger + option list) — every tier always shows some color there,
 * unlike {@link getPriorityFlag} above, which decides whether a badge shows
 * at all elsewhere in the app. Kept as one map so the picker components don't
 * each hand-roll their own nested ternary over the same four tiers.
 */
export type PriorityFlagClasses = { text: string; fill: string };

const PRIORITY_FLAG_CLASSES: Record<
  "Lowest" | "Low" | "Medium" | "High",
  PriorityFlagClasses
> = {
  Lowest: { text: "text-muted-foreground", fill: "fill-muted-foreground" },
  Low: { text: "text-lime", fill: "fill-lime" },
  Medium: { text: "text-orange", fill: "fill-orange" },
  High: { text: "text-red", fill: "fill-red" },
};

export function priorityFlagClasses(
  priority: string | null | undefined,
): PriorityFlagClasses {
  return (
    PRIORITY_FLAG_CLASSES[priority as keyof typeof PRIORITY_FLAG_CLASSES] ??
    PRIORITY_FLAG_CLASSES.Low
  );
}

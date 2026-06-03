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

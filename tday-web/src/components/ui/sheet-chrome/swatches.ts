// Swatch color classes for the native-style centered selector overlays.
// Mirrors the iOS CreateTaskSheet swatch palette (priority + repeat dots).

export function prioritySwatchClass(priority: "Low" | "Medium" | "High"): string {
  return priority === "Low"
    ? "bg-lime"
    : priority === "Medium"
      ? "bg-orange"
      : "bg-red";
}

export type RepeatSwatchKind =
  | "Daily"
  | "Weekly"
  | "Monthly"
  | "Yearly"
  | "Weekday"
  | "Custom"
  | null;

export function repeatSwatchClass(kind: RepeatSwatchKind): string {
  switch (kind) {
    case "Daily":
      return "bg-accent-lime";
    case "Weekly":
      return "bg-accent-blue";
    case "Weekday":
      return "bg-accent-purple";
    case "Monthly":
      return "bg-accent-gold";
    case "Yearly":
      return "bg-accent-red";
    case "Custom":
      return "bg-accent-teal";
    default:
      return "bg-muted-foreground/50";
  }
}

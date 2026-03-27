import type { ListColor } from "@/types";
import { listColorMap } from "@/lib/listColorMap";
export const calendarEventPropStyles = (
  priority: "High" | "Medium" | "Low",
  listColor: ListColor | undefined,
) => {
  let eventBgColor: string;

  if (!listColor) {
    switch (priority) {
      case "Low":
        eventBgColor = "hsl(var(--calendar-lime))";
        break;
      case "Medium":
        eventBgColor = "hsl(var(--calendar-orange))";
        break;
      case "High":
        eventBgColor = "hsl(var(--calendar-red))";
        break;
    }
  } else {
    const match = listColorMap.find(({ value }) => value === listColor);
    eventBgColor =
      `hsl(var(--${match?.tailwind.replace("bg-", "")})/0.7)` ||
      "hsl(var(--accent-blue)/0.7)";
  }

  return {
    style: {
      backgroundColor: eventBgColor,
      border: "0px solid rgba(255,255,255,0.12)",
      outline: "none",
      display: "flex",
      justifyContent: "start",
    },
  };
};

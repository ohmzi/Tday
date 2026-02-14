import { ToolbarProps, View } from "react-big-calendar";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { TodoItemType } from "@/types";
import { useTranslations } from "next-intl";
import { cn } from "@/lib/utils";

const viewOptions: View[] = ["month", "week", "day"];

export function CalendarToolbar({
  label,
  onNavigate,
  onView,
  view,
}: ToolbarProps<TodoItemType, object>) {
  const appDict = useTranslations("app");

  return (
    <div className="flex flex-col gap-3 px-1 pb-3 sm:px-2 sm:pb-4">
      {/* Row 1 – Navigation: ‹ › title Today */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1 sm:gap-2">
          <button
            className="flex h-8 w-8 items-center justify-center rounded-lg border border-border/60 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground sm:h-9 sm:w-9"
            onClick={() => onNavigate("PREV")}
            aria-label="Previous"
          >
            <ChevronLeft className="h-4 w-4 sm:h-5 sm:w-5" />
          </button>
          <button
            className="flex h-8 w-8 items-center justify-center rounded-lg border border-border/60 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground sm:h-9 sm:w-9"
            onClick={() => onNavigate("NEXT")}
            aria-label="Next"
          >
            <ChevronRight className="h-4 w-4 sm:h-5 sm:w-5" />
          </button>

          {/* Mobile: abbreviated label */}
          <h2 className="pl-1 text-base font-semibold tracking-tight sm:hidden">
            {label.split(" ")[0].slice(0, 3) + " " + label.split(" ")[1]?.slice(2)}
          </h2>
          {/* Desktop: full label */}
          <h2 className="hidden pl-2 text-lg font-semibold tracking-tight sm:block">
            {label}
          </h2>
        </div>

        <button
          onClick={() => onNavigate("TODAY")}
          className="rounded-lg border border-border/60 px-3 py-1.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-foreground sm:px-4 sm:py-2"
        >
          {appDict("today")}
        </button>
      </div>

      {/* Row 2 – Segmented view toggle */}
      <div className="flex w-full rounded-xl border border-border/60 bg-muted/40 p-1">
        {viewOptions.map((v) => (
          <button
            key={v}
            onClick={() => onView(v)}
            className={cn(
              "flex-1 rounded-lg px-3 py-1.5 text-xs font-medium capitalize transition-all duration-200 sm:text-sm",
              view === v
                ? "bg-background text-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground",
            )}
          >
            {appDict(v.toLowerCase())}
          </button>
        ))}
      </div>
    </div>
  );
}

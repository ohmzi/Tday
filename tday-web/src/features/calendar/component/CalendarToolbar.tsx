import { ToolbarProps, View } from "react-big-calendar";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { TodoItemType } from "@/types";
import { useTranslation } from "react-i18next";
import { cn } from "@/lib/utils";

const viewOptions: View[] = ["month", "week", "day"];

export function CalendarToolbar({
  label,
  onNavigate,
  onView,
  view,
}: ToolbarProps<TodoItemType, object>) {
  const { t: appDict } = useTranslation("app");

  return (
    <div className="flex flex-col gap-3 px-1 pb-3 sm:px-2 sm:pb-4">
      {/* Row 1 – Navigation: ‹ › title Today */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1 sm:gap-2">
          <button
            className="flex h-10 w-10 items-center justify-center rounded-full border border-white/70 bg-card/90 text-muted-foreground shadow-sm transition-colors hover:bg-card hover:text-foreground dark:border-white/10 sm:h-11 sm:w-11"
            onClick={() => onNavigate("PREV")}
            aria-label="Previous"
          >
            <ChevronLeft className="h-4 w-4 sm:h-5 sm:w-5" />
          </button>
          <button
            className="flex h-10 w-10 items-center justify-center rounded-full border border-white/70 bg-card/90 text-muted-foreground shadow-sm transition-colors hover:bg-card hover:text-foreground dark:border-white/10 sm:h-11 sm:w-11"
            onClick={() => onNavigate("NEXT")}
            aria-label="Next"
          >
            <ChevronRight className="h-4 w-4 sm:h-5 sm:w-5" />
          </button>

          {/* Mobile: abbreviated label */}
          <h2 className="pl-1 text-lg font-black tracking-normal sm:hidden">
            {label.split(" ")[0].slice(0, 3) + " " + label.split(" ")[1]?.slice(2)}
          </h2>
          {/* Desktop: full label */}
          <h2 className="hidden pl-2 text-2xl font-black tracking-normal sm:block">
            {label}
          </h2>
        </div>

        <button
          onClick={() => onNavigate("TODAY")}
          className="rounded-full border border-white/70 bg-card/90 px-4 py-2 text-sm font-black text-muted-foreground shadow-sm transition-colors hover:bg-accent hover:text-accent-foreground dark:border-white/10 sm:px-5"
        >
          {appDict("today")}
        </button>
      </div>

      {/* Row 2 – Segmented view toggle */}
      <div className="flex w-full rounded-[22px] border border-white/70 bg-muted/80 p-1.5 shadow-[0_12px_30px_-28px_hsl(var(--shadow)/0.5)] dark:border-white/10">
        {viewOptions.map((v) => (
          <button
            key={v}
            onClick={() => onView(v)}
            className={cn(
              "flex-1 rounded-[17px] px-3 py-2 text-xs font-black capitalize transition-all duration-200 sm:text-sm",
              view === v
                ? "bg-card text-foreground shadow-sm"
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

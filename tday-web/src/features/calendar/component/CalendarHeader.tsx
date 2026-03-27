import { HeaderProps } from "react-big-calendar";
import { cn } from "@/lib/utils";
import { format, isToday } from "date-fns";

/** Month view — day name only (no date number) */
export default function CalendarHeader({ date }: HeaderProps) {
  const today = isToday(date);

  return (
    <div
      className={cn(
        "flex items-center justify-center py-2 h-full",
        today && "text-accent font-semibold",
      )}
    >
      <span className="hidden lg:inline text-sm font-medium text-foreground">
        {format(date, "EEEE")}
      </span>
      <span className="lg:hidden text-xs sm:text-sm font-medium text-foreground">
        {format(date, "EEE")}
      </span>
    </div>
  );
}

/** Week / Day view — day name + circled date number */
export function TimeViewHeader({ date }: HeaderProps) {
  const today = isToday(date);

  return (
    <div
      className={cn(
        "flex flex-col items-center justify-center gap-0.5 py-1 h-full",
        today && "text-accent",
      )}
    >
      <span className="hidden lg:inline text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {format(date, "EEEE")}
      </span>
      <span className="lg:hidden text-[0.65rem] sm:text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {format(date, "EEE")}
      </span>

      <span
        className={cn(
          "text-sm font-semibold sm:text-base",
          today
            ? "flex h-7 w-7 items-center justify-center rounded-full bg-accent text-accent-foreground sm:h-8 sm:w-8"
            : "text-foreground",
        )}
      >
        {format(date, "d")}
      </span>
    </div>
  );
}

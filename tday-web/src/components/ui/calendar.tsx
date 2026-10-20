import * as React from "react";
import { ChevronDown, ChevronLeft, ChevronRight, ChevronUp } from "lucide-react";
import { DayPicker } from "react-day-picker";

import { cn } from "@/lib/utils";
import { buttonVariants } from "@/components/ui/button";

type CalendarProps = React.ComponentProps<typeof DayPicker>;

const CHEVRONS = {
  up: ChevronUp,
  down: ChevronDown,
  left: ChevronLeft,
  right: ChevronRight,
} as const;

// react-day-picker 10 puts every state class (`selected`, `today`, `range_*`, ...) on the day's
// `<td>`, while the visible disc has always been the `<button>` inside it. The `[&>button]:`
// variants below carry the old button-level styling over to the new structure.
function Calendar({
  className,
  classNames,
  showOutsideDays = true,
  ...props
}: CalendarProps) {
  return (
    <DayPicker
      showOutsideDays={showOutsideDays}
      className={cn("p-3", className)}
      classNames={{
        months:
          "relative w-fit flex flex-col gap-4 sm:flex-row text-sm",
        month: "space-y-4 text-foreground",
        month_caption: "flex justify-center pt-1 items-center",
        caption_label: "text-sm font-medium text-foreground",
        nav: "absolute inset-x-0 top-0 flex items-center justify-between px-1",
        button_previous: cn(
          buttonVariants({ variant: "outline" }),
          "h-7 w-7 bg-transparent p-0 opacity-50 hover:opacity-100 text-foreground",
        ),
        button_next: cn(
          buttonVariants({ variant: "outline" }),
          "h-7 w-7 bg-transparent p-0 opacity-50 hover:opacity-100 text-foreground",
        ),
        month_grid: "w-full border-collapse space-y-1",
        weekdays: "flex",
        weekday: "text-muted-foreground rounded-md w-full font-normal text-xs",
        week: "flex w-full mt-2",
        day: "h-8 w-8 text-center text-sm p-0 relative focus-within:relative focus-within:z-20",
        day_button: cn(
          buttonVariants({ variant: "ghost" }),
          "h-8 w-8 p-0 font-normal text-xs text-foreground",
        ),
        range_start:
          "[&>button]:!bg-calendar--lime [&>button]:font-bold! [&>button]:text-sm! [&>button]:text-foreground relative z-10 [&>button]:rounded-full!",
        range_end:
          "[&>button]:!bg-calendar--lime [&>button]:font-bold! [&>button]:text-sm! [&>button]:text-foreground relative z-10",
        selected:
          "[&>button]:bg-primary [&>button]:rounded-full! [&>button]:text-primary-foreground [&>button]:hover:bg-primary [&>button]:hover:text-primary-foreground [&>button]:focus:bg-primary [&>button]:focus:text-primary-foreground",
        today: "[&>button]:bg-accent [&>button]:text-accent-foreground",
        outside:
          "text-muted-foreground [&>button]:text-muted-foreground",
        disabled: "text-muted-foreground opacity-50",
        range_middle:
          "[&>button]:rounded-full! [&>button]:bg-popover-border! [&>button]:text-white",
        hidden: "invisible",
        ...classNames,
      }}
      components={{
        Chevron: ({ className, orientation }) => {
          const Icon = CHEVRONS[orientation ?? "right"];
          return <Icon className={cn("h-4 w-4 pointer-events-none", className)} />;
        },
      }}
      {...props}
    />
  );
}
Calendar.displayName = "Calendar";

export { Calendar };

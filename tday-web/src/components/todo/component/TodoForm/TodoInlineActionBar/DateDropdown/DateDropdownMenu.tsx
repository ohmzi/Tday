import { addDays, endOfDay, startOfDay, isSameDay } from "date-fns";
import { Calendar } from "@/components/ui/calendar";
import React from "react";
import { format, nextMonday, differenceInDays } from "date-fns";
import { getDateFnsLocale } from "@/lib/date/dateFnsLocale";
import LineSeparator from "@/components/ui/lineSeparator";
import { Sun } from "lucide-react";
import { IterationCcw as Tomorrow } from "lucide-react";
import { Calendar as CalenderIcon } from "lucide-react";
import { useTodoForm } from "@/providers/TodoFormProvider";
import DurationPicker from "./DurationPicker";
import { useTranslation } from "react-i18next";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import { Button } from "@/components/ui/button";
import { getDisplayDate } from "@/lib/date/displayDate";
import { useLocale } from "@/lib/navigation";
import clsx from "clsx";
import { useUserTimezone } from "@/features/user/query/get-timezone";

const DateDropdownMenu = () => {
  const locale = useLocale()
  const userTZ = useUserTimezone()
  const { t: appDict } = useTranslation("app");
  const { dateRange, setDateRange } = useTodoForm();
  const nextWeek = startOfDay(nextMonday(dateRange?.from || new Date()));
  const tomorrow = startOfDay(addDays(dateRange?.from || new Date(), 1));
  const [isOpen, setIsOpen] = React.useState(false);
  const dfLocale = getDateFnsLocale(locale);

  // Color the trigger by how the selected date relates to *now* using date math,
  // so it stays correct regardless of the active language (previously this
  // compared the localized label against the English strings "Today"/"Tomorrow").
  const today = new Date();
  const triggerColorClass = dateRange.from && isSameDay(dateRange.from, today)
    ? "text-lime"
    : dateRange.from && isSameDay(dateRange.from, addDays(today, 1))
      ? "text-orange"
      : "text-red";

  const itemClass =
    "flex justify-between items-center p-1.5 px-2 rounded w-[96.5%] hover:bg-popover-accent cursor-pointer m-auto text-sm";

  return (
    <Popover open={isOpen} onOpenChange={setIsOpen}>
      <PopoverTrigger asChild>
        <Button
          variant={"outline"}
          className={clsx(
            "cursor-pointer text-xs sm:text-sm font-medium w-fit h-fit p-2! text-muted-foreground bg-inherit",
            triggerColorClass,
          )}
        >
          <CalenderIcon className="w-4 h-4" />
          <span className="text-sm font-medium">
            {getDisplayDate(dateRange.from, true, locale, userTZ?.timeZone)}
          </span>
        </Button>
      </PopoverTrigger>

      <PopoverContent
        className="shadow-2xl! flex flex-col px-0 gap-1 py-1.5 w-62.5 font-extralight border-popover-accent overflow-scroll scrollbar-none"
        align="start"
      >
        {/* --- OPTION: TODAY --- */}
        <button
          className={itemClass}
          onClick={() => {
            setDateRange((prev) => ({
              from: startOfDay(new Date()),
              to:
                prev.to && prev.from
                  ? new Date(
                    endOfDay(
                      addDays(
                        new Date(),
                        differenceInDays(prev.to, prev.from),
                      ),
                    ),
                  )
                  : endOfDay(new Date()),
            }));
            setIsOpen(false);
          }}
        >
          <div className="flex gap-2 items-center">
            <Sun strokeWidth={1.7} className="w-4 h-4" />
            {appDict("today")}
          </div>
          <p className="text-xs text-muted-foreground">
            {format(new Date(), "EEE", { locale: dfLocale })}
          </p>
        </button>

        {/* --- OPTION: TOMORROW --- */}
        <button
          className={itemClass}
          onClick={() => {
            setDateRange((prev) => ({
              from: tomorrow,
              to:
                prev.to && prev.from
                  ? endOfDay(
                    addDays(tomorrow, differenceInDays(prev.to, prev.from)),
                  )
                  : endOfDay(tomorrow),
            }));
            setIsOpen(false);
          }}
        >
          <div className="flex gap-2 items-center">
            <Tomorrow strokeWidth={1.7} className="w-4 h-4" />
            {appDict("tomorrow")}
          </div>
          <p className="text-xs text-muted-foreground">
            {format(tomorrow, "EEE", { locale: dfLocale })}
          </p>
        </button>

        {/* --- OPTION: NEXT WEEK --- */}
        <button
          className={itemClass}
          onClick={() => {
            setDateRange((prev) => ({
              from: nextWeek,
              to:
                prev.to && prev.from
                  ? endOfDay(
                    new Date(
                      addDays(nextWeek, differenceInDays(prev.to, prev.from)),
                    ),
                  )
                  : endOfDay(nextWeek),
            }));
            setIsOpen(false);
          }}
        >
          <div className="flex gap-2 items-center">
            <CalenderIcon strokeWidth={1.7} className="w-4 h-4" />
            {appDict("nextWeek")}
          </div>
          <div className="text-xs text-muted-foreground">{format(nextWeek, "EEE", { locale: dfLocale })}</div>
        </button>

        {/* --- DURATION --- */}
        <DurationPicker className={itemClass} />

        <LineSeparator className="w-full border-popover-border my-1 mb-4" />

        {/* --- CALENDAR --- */}
        <div className="w-full p-0">
          <Calendar
            mode="range"
            defaultMonth={new Date()}
            selected={dateRange}
            onSelect={(newDateRange) => {
              setDateRange(() => {
                const from = startOfDay(newDateRange?.from || new Date());
                const to = endOfDay(newDateRange?.to || from);
                return { from, to };
              });
            }}
            numberOfMonths={1}
          />
        </div>
      </PopoverContent>
    </Popover>
  );
};

export default DateDropdownMenu;

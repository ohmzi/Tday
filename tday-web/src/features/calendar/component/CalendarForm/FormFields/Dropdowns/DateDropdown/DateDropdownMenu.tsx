import { addDays, endOfDay, startOfDay } from "date-fns";
import { Calendar } from "@/components/ui/calendar";
import React, { useMemo, useState, useEffect } from "react";
import { nextMonday } from "date-fns";
import LineSeparator from "@/components/ui/lineSeparator";
import { IterationCcw, Sun } from "lucide-react";
import { Calendar as CalendarIcon } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  DropdownMenuSub,
  DropdownMenuSubTrigger,
  DropdownMenuSubContent,
  DropdownMenuPortal
} from "@/components/ui/dropdown-menu";
import { useTranslation } from "react-i18next";
import { getDisplayDate } from "@/lib/date/displayDate";
import { isSameDay } from "date-fns";
import { useUserTimezone } from "@/features/user/query/get-timezone";
import { useLocale } from "@/lib/navigation";
import type { DateRange } from "react-day-picker";

import { Clock } from "lucide-react";
import { format, parse, isValid } from "date-fns";
import { Input } from "@/components/ui/input";

type DropdownDateRange = { from: Date; to: Date };

type DateDropdownMenuProps = {
  dateRange: DropdownDateRange;
  setDateRange: React.Dispatch<React.SetStateAction<DropdownDateRange>>;
};

const DateDropdownMenu = ({
  dateRange,
  setDateRange,
}: DateDropdownMenuProps) => {
  const locale = useLocale();
  const userTZ = useUserTimezone();
  const { t: appDict } = useTranslation("app");
  const nextWeek = startOfDay(nextMonday(dateRange?.from || new Date()));
  const tomorrow = startOfDay(addDays(dateRange?.from || new Date(), 1));
  const [isOpen, setIsOpen] = React.useState(false);

  const [calendarRange, setCalendarRange] = useState<DateRange | undefined>({
    from: dateRange.from,
    to: dateRange.to,
  });

  useEffect(() => {
    setCalendarRange({ from: dateRange.from, to: dateRange.to });
  }, [dateRange.from, dateRange.to]);

  function formatDayAbbr(date: Date): string {
    return new Intl.DateTimeFormat(locale, { weekday: "short" }).format(date);
  }

  const displayedDateRange = useMemo(() => {
    if (isSameDay(dateRange.from, dateRange.to) && dateRange.from.getTime() === dateRange.to.getTime()) {
      return `${getDisplayDate(dateRange.to, false, locale, userTZ?.timeZone)}, ${new Intl.DateTimeFormat(locale, { hour: "numeric", minute: "2-digit" }).format(dateRange.to)}`;
    }
    if (isSameDay(dateRange.from, dateRange.to)) {
      let displayedTime = `${new Intl.DateTimeFormat(locale, { hour: "numeric" }).format(dateRange.from)}-${new Intl.DateTimeFormat(locale, { hour: "numeric" }).format(dateRange.to)}`;
      if (displayedTime === "12 AM-11 PM") displayedTime = "All day";
      return `${getDisplayDate(dateRange.from, false, locale, userTZ?.timeZone)},  ${displayedTime}`;
    }
    return `${getDisplayDate(dateRange.from, false, locale, userTZ?.timeZone)} - ${getDisplayDate(dateRange.to, false, locale, userTZ?.timeZone)}`;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dateRange.from, dateRange.to, locale]);

  return (
    <DropdownMenu open={isOpen} onOpenChange={setIsOpen} modal={true}>
      <DropdownMenuTrigger asChild>
        <button className="cursor-pointer flex justify-start items-center gap-2 p-2 w-full h-full hover:bg-popover rounded-md outline-hidden">
          <span className="text-sm font-medium">{displayedDateRange}</span>
        </button>
      </DropdownMenuTrigger>

      <DropdownMenuContent
        className="z-50 flex flex-col gap-2 p-1 w-62.5 font-extralight"
        align="start"
      >
        <DropdownMenuItem
          onSelect={(e) => { e.preventDefault(); }}
          className="flex w-full cursor-pointer items-center justify-between"
          onClick={() => {
            const d = endOfDay(new Date());
            setDateRange({ from: d, to: d });
            setIsOpen(false);
          }}
        >
          <div className="flex gap-2 items-center">
            <Sun strokeWidth={1.7} className="w-4! h-4!" />
            {appDict("today")}
          </div>
          <p className="text-xs text-muted-foreground">
            {formatDayAbbr(new Date())}
          </p>
        </DropdownMenuItem>

        <DropdownMenuItem
          onSelect={(e) => { e.preventDefault(); }}
          className="flex w-full cursor-pointer items-center justify-between"
          onClick={() => {
            const d = endOfDay(tomorrow);
            setDateRange({ from: d, to: d });
            setIsOpen(false);
          }}
        >
          <div className="flex gap-2 items-center">
            <IterationCcw strokeWidth={1.7} className="w-4! h-4!" />
            {appDict("tomorrow")}
          </div>
          <p className="text-xs text-muted-foreground">
            {formatDayAbbr(tomorrow)}
          </p>
        </DropdownMenuItem>

        <DropdownMenuItem
          onSelect={(e) => { e.preventDefault(); }}
          className="flex w-full cursor-pointer items-center justify-between"
          onClick={() => {
            const d = endOfDay(nextWeek);
            setDateRange({ from: d, to: d });
            setIsOpen(false);
          }}
        >
          <div className="flex gap-2 items-center">
            <CalendarIcon strokeWidth={1.7} className="w-4! h-4!" />
            {appDict("nextWeek")}
          </div>
          <p className="text-xs text-muted-foreground">
            {formatDayAbbr(nextWeek)}
          </p>
        </DropdownMenuItem>

        <DropdownMenuSub>
          <DropdownMenuSubTrigger className="flex w-full cursor-pointer items-center justify-between rounded-sm px-2 py-1.5 text-sm outline-hidden hover:bg-accent hover:text-accent-foreground transition-colors group">
            <div className="flex gap-2 items-center">
              <Clock strokeWidth={1.7} className="w-4! h-4!" />
              {appDict("duration")}
            </div>
          </DropdownMenuSubTrigger>
          <DropdownMenuPortal>
            <DropdownMenuSubContent className="w-[320px] p-4 rounded-lg z-60"
              onPointerDown={(e) => e.stopPropagation()}
              onKeyDown={(e) => e.stopPropagation()}>
              <DurationPickerSub dateRange={dateRange} setDateRange={setDateRange} />
            </DropdownMenuSubContent>
          </DropdownMenuPortal>
        </DropdownMenuSub>

        <LineSeparator className="border-popover-border w-full my-1 mb-4" />

        <div className="flex justify-center p-0">
          <Calendar
            className="p-0 pb-2"
            mode="range"
            defaultMonth={dateRange.from}
            selected={calendarRange}
            onSelect={(newRange) => {
              setCalendarRange(newRange);
              if (newRange?.from && newRange?.to) {
                const d = endOfDay(newRange.to);
                setDateRange({ from: d, to: d });
              }
            }}
            numberOfMonths={1}
          />
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};

export default DateDropdownMenu;

type DurationPickerSubProps = {
  dateRange: DropdownDateRange;
  setDateRange: React.Dispatch<React.SetStateAction<DropdownDateRange>>;
};

function DurationPickerSub({ dateRange, setDateRange }: DurationPickerSubProps) {
  const locale = useLocale();
  const { t: appDict } = useTranslation("app");

  const [timeStr, setTimeStr] = React.useState(
    dateRange?.to ? format(dateRange.to, "HH:mm") : "00:00",
  );

  React.useEffect(() => {
    if (dateRange?.to) setTimeStr(format(dateRange.to, "HH:mm"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dateRange?.to?.getTime()]);

  const handleTimeChange = (val: string) => {
    setTimeStr(val);
    const parsed = parse(val || "00:00", "HH:mm", dateRange.to);
    if (!isValid(parsed)) return;
    setDateRange({ from: parsed, to: parsed });
  };

  const formatDate = (date: Date) => {
    return new Intl.DateTimeFormat(locale, {
      day: "2-digit",
      month: "short",
    }).format(date);
  };

  return (
    <div className=" flex flex-col gap-4">
      <div className="space-y-1">
        <h4 className="text-sm font-semibold leading-none tracking-tight">
          {appDict("durationMenu.title")}
        </h4>
        <p className="text-[11px] leading-snug text-muted-foreground">
          {appDict("durationMenu.subTitle")}
        </p>
      </div>

      <div className="rounded-md border p-1">
        <div className="flex justify-center gap-2 items-center">
          <span className="text-xs font-medium text-muted-foreground">
            {formatDate(dateRange.to)}
          </span>
          <div className="p-0 flex-1">
            <Input
              value={timeStr}
              onChange={(e) => handleTimeChange(e.currentTarget.value)}
              type="time"
              className="p-0 select-none border-none bg-transparent focus-visible:outline-hidden focus-visible:ring-0 focus-visible:ring-transparent focus-visible:ring-offset-0 hover:cursor-pointer"
            />
          </div>
        </div>
      </div>
    </div>
  );
}

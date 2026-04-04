import { DrawerClose } from "@/components/ui/drawer";
import { format, parse, isValid } from "date-fns";
import { useTranslation } from "react-i18next";
import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { useLocale } from "@/lib/navigation";

type DrawerDateRange = { from: Date; to: Date };

export default function DurationPicker({
    dateRange,
    setDateRange,
}: {
    dateRange: DrawerDateRange;
    setDateRange: React.Dispatch<React.SetStateAction<DrawerDateRange>>;
}) {
    const { t: appDict } = useTranslation("app");
    const locale = useLocale();

    const [timeStr, setTimeStr] = useState(format(dateRange.to, "HH:mm"));

    useEffect(() => {
        setTimeStr(format(dateRange.to, "HH:mm"));
    }, [dateRange.to]);

    const handleTimeChange = (value: string) => {
        setTimeStr(value);
        const parsed = parse(value, "HH:mm", dateRange.to);
        if (!isValid(parsed)) return;
        setDateRange({ from: parsed, to: parsed });
    };

    const formatDateFull = (date: Date) => {
        return new Intl.DateTimeFormat(locale, {
            weekday: "short",
            month: "long",
            day: "numeric",
        }).format(date);
    };

    return (
        <div className="flex flex-col gap-8 p-6">
            <div className="flex flex-col gap-4">
                <div className="group relative flex flex-col gap-3 bg-secondary/20 p-5 rounded-3xl border-2 border-transparent focus-within:border-lime transition-all">
                    <div className="flex justify-between items-center">
                        <span className="text-xs font-bold uppercase text-muted-foreground">Due</span>
                        <span className="text-xs font-medium text-muted-foreground/60">{formatDateFull(dateRange.to)}</span>
                    </div>
                    <div className="flex items-baseline gap-2">
                        <input
                            type="time"
                            value={timeStr}
                            onChange={(e) => handleTimeChange(e.target.value)}
                            className="bg-transparent text-4xl sm:text-5xl font-black tracking-tighter focus:outline-hidden w-full cursor-pointer"
                        />
                    </div>
                </div>
            </div>

            <div className="mt-4 pt-6 flex items-center justify-between">
                <DrawerClose asChild>
                    <Button className=" px-8 h-fit py-1.5! text-base rounded-md bg-transparent hover:text-foreground border  w-full">
                        {appDict("save")}
                    </Button>
                </DrawerClose>
            </div>
        </div>
    );
}

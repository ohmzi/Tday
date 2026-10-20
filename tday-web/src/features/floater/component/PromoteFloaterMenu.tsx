import React from "react";
import clsx from "clsx";
import { CalendarPlus } from "lucide-react";
import { addDays, nextMonday, set, startOfDay } from "date-fns";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { usePromoteFloater } from "@/features/floater/query/promote-floater";
import type { FloaterItemType } from "@/types";

const suppressDragActivation = (event: React.SyntheticEvent) => {
  event.stopPropagation();
};

/**
 * "Schedule…": promotes a floater into a real Todo at a quick-picked due
 * instant. Options mirror the Quick Defer instants (tonight 19:00, tomorrow
 * 09:00, next Monday 09:00); anything finer is one edit away after promoting.
 */
export function PromoteFloaterMenu({
  floater,
  className,
}: {
  floater: FloaterItemType;
  className?: string;
}) {
  const { promoteMutateFn } = usePromoteFloater();

  const promote = (due: Date) => promoteMutateFn({ floater, due });
  const now = new Date();
  const tonight = set(startOfDay(now), { hours: 19 });
  const tomorrowMorning = set(startOfDay(addDays(now, 1)), { hours: 9 });
  const nextWeek = set(startOfDay(nextMonday(now)), { hours: 9 });

  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          aria-label="Schedule floater"
          title="Schedule floater"
          onPointerDown={suppressDragActivation}
          onMouseDown={suppressDragActivation}
          onTouchStart={suppressDragActivation}
          onClick={(event) => event.stopPropagation()}
          className={clsx(
            "rounded-full bg-card/80 p-1.5 text-muted-foreground backdrop-blur transition-colors hover:bg-muted hover:text-foreground",
            className,
          )}
        >
          <CalendarPlus className="h-4 w-4" strokeWidth={1.8} />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="text-foreground">
        <DropdownMenuItem onClick={() => promote(tonight)}>Tonight</DropdownMenuItem>
        <DropdownMenuItem onClick={() => promote(tomorrowMorning)}>Tomorrow morning</DropdownMenuItem>
        <DropdownMenuItem onClick={() => promote(nextWeek)}>Next week</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

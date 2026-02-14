import React from "react";
import { useMenu } from "@/providers/MenuProvider";
import clsx from "clsx";
import { Calendar1Icon } from "lucide-react";
import { Link } from "@/i18n/navigation";
import useWindowSize from "@/hooks/useWindowSize";
import { Button } from "@/components/ui/button";
import { useTranslations } from "next-intl";
const CalendarItem = () => {
  const sidebarDict = useTranslations("sidebar")
  const { width } = useWindowSize();

  const { activeMenu, setActiveMenu, setShowMenu } = useMenu();
  return (
    <Button
      asChild
      variant={"ghost"}
      className={clsx(
        "h-10 w-full justify-start rounded-xl border border-transparent px-3 text-sm font-medium text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent/50 hover:text-sidebar-foreground",
        activeMenu.name === "Calendar" &&
        "bg-sidebar-accent text-sidebar-accent-foreground",
      )}
    >
      <Link
        href="/app/calendar"
        className="flex h-full w-full items-center gap-3"
        onClick={() => {
          setActiveMenu({ name: "Calendar" });
          if (width <= 1266) setShowMenu(false);
        }}
      >
        <Calendar1Icon className="h-4 w-4 shrink-0" />
        <p className="truncate whitespace-nowrap text-foreground">{sidebarDict("calendar")}</p>
      </Link>
    </Button>
  );
};

export default CalendarItem;
